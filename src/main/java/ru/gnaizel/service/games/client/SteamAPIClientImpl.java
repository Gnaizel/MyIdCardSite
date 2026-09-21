package ru.gnaizel.service.games.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.gnaizel.dto.games.GOGSteamResponseDto;
import ru.gnaizel.dto.games.NowPlayingDto;
import ru.gnaizel.dto.games.SteamOwnedGamesResponse;
import ru.gnaizel.exception.SteamApiResponseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Аккаунтов может быть несколько: ключ Steam выдаётся разработчику, а не
 * профилю, и одним ключом опрашивается любой публичный профиль. Библиотеки
 * складываются по appid — одна и та же игра на двух аккаунтах даёт сумму
 * часов, а не два отдельных пункта.
 * <p>
 * Каждый аккаунт живёт своей жизнью: свой ответ, свой срок годности, свой
 * запрет. Это не усложнение ради усложнения — Steam блокирует обращения
 * по отдельному аккаунту, и без этого один заблокированный обнулял бы свою
 * часть часов, а общая сумма проседала на глазах.
 */
@Slf4j
@Service
public class SteamAPIClientImpl implements SteamAPIClient {
    /* include_played_free_games — иначе Steam молчит про free-to-play, а в них
       как раз и играют. include_appinfo — иначе не приходят название и иконка,
       и игру нечем показать. */
    private static final String OWNED_URL =
            "https://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/"
                    + "?key=%s&steamid=%s&include_played_free_games=1&include_appinfo=1&format=json";

    /* Сколько ответ аккаунта считается свежим. Библиотека меняется, только
       когда играешь, так что час — с запасом, зато запросов в разы меньше. */
    private static final Duration ACCOUNT_TTL = Duration.ofHours(1);

    /* Сколько не трогать аккаунт после 429. Час — осторожная оценка снизу:
       у Steam такие запреты держатся до шести часов И ПРОДЛЕВАЮТСЯ, если
       стучаться во время запрета. Поэтому повторов на 429 здесь намеренно
       нет: они только усугубляют. */
    private static final Duration BAN = Duration.ofHours(1);

    /* Пауза между живыми запросами: пять подряд Steam считает частотой. */
    private static final long PAUSE_MS = 500;

    /* Профили всех аккаунтов умещаются в один запрос, поэтому «во что играет
       прямо сейчас» стоит ровно одно обращение в минуту, независимо от того,
       сколько человек смотрит страницу. */
    private static final String SUMMARIES_URL =
            "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/?key=%s&steamids=%s";

    /* Минута: индикатор живой, но чаще спрашивать незачем — партия столько
       не длится, а запросы копятся. */
    private static final Duration NOW_TTL = Duration.ofMinutes(1);

    private final RestTemplate template = new RestTemplate();
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            /* Иначе время пишется числом секунд и файл нельзя прочитать глазами. */
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /* Последний удачный ответ по каждому аккаунту и время, до которого его
       не стоит беспокоить. */
    private final Map<String, Snapshot> store = new ConcurrentHashMap<>();
    private final Map<String, Instant> banned = new ConcurrentHashMap<>();

    private volatile Optional<NowPlayingDto> nowPlaying = Optional.empty();
    private volatile Instant nowPlayingAt;

    @Value("${steam.api-token}")
    private String token;

    @Value("${steam.ids:}")
    private String ids;

    @Value("${steam.cache-dir:./data}")
    private String cacheDir;

    @Override
    public Library getAllGameLib() {
        Map<Integer, GOGSteamResponseDto> merged = new LinkedHashMap<>();
        boolean complete = true;
        boolean fetched = false;

        for (String id : steamIds()) {
            if (needsRefresh(id)) {
                /* Пауза только между настоящими обращениями: если всё берётся
                   из памяти, тормозить ответ страницы незачем. */
                if (fetched) {
                    pause(PAUSE_MS);
                }
                refresh(id);
                fetched = true;
            }

            Snapshot snapshot = store.get(id);
            if (snapshot == null) {
                /* Ни свежего ответа, ни старого — этот аккаунт в сумму
                   не попал, и знать об этом должен тот, кто кэширует. */
                complete = false;
                continue;
            }
            for (GOGSteamResponseDto game : snapshot.games()) {
                /* Копия, а не сам объект: combineOwned складывает часы в первый
                   аргумент, а объекты из snapshot живут в памяти между
                   запросами и уходят на диск. Без копии каждое слияние
                   дописывало бы чужие часы в сохранённые данные аккаунта,
                   и сумма росла бы сама по себе с каждым обновлением. */
                merged.merge(game.getAppid(), copyOf(game), SteamAPIClientImpl::combineOwned);
            }
        }

        if (fetched) {
            save();
        }
        if (merged.isEmpty()) {
            throw new SteamApiResponseException("STEAM API ERROR");
        }
        return new Library(List.copyOf(merged.values()), complete);
    }

    @Override
    public Optional<NowPlayingDto> getNowPlaying() {
        if (nowPlayingAt != null
                && Duration.between(nowPlayingAt, Instant.now()).compareTo(NOW_TTL) < 0) {
            return nowPlaying;
        }

        List<String> accounts = steamIds();
        if (accounts.isEmpty()) {
            return Optional.empty();
        }

        try {
            JsonNode response = template.getForObject(
                    SUMMARIES_URL.formatted(token, String.join(",", accounts)), JsonNode.class);
            nowPlaying = firstInGame(response);
        } catch (RestClientException e) {
            /* Индикатор живой: лучше на минуту показать «не играет», чем
               оставить гореть то, чего уже нет. */
            log.warn("STEAM: не узнал текущую игру: {}", e.getMessage());
            nowPlaying = Optional.empty();
        }
        nowPlayingAt = Instant.now();
        return nowPlaying;
    }

    /* gameextrainfo есть в профиле, только пока игра запущена. Аккаунтов
       несколько, но играют на одном — берём первый найденный. */
    private Optional<NowPlayingDto> firstInGame(JsonNode response) {
        if (response == null) {
            return Optional.empty();
        }
        for (JsonNode player : response.path("response").path("players")) {
            String name = player.path("gameextrainfo").asText(null);
            if (name != null && !name.isBlank()) {
                return Optional.of(new NowPlayingDto(player.path("gameid").asInt(), name));
            }
        }
        return Optional.empty();
    }

    private boolean needsRefresh(String id) {
        Instant until = banned.get(id);
        if (until != null && until.isAfter(Instant.now())) {
            return false;
        }
        Snapshot snapshot = store.get(id);
        return snapshot == null
                || Duration.between(snapshot.fetchedAt(), Instant.now()).compareTo(ACCOUNT_TTL) > 0;
    }

    private void refresh(String id) {
        try {
            List<GOGSteamResponseDto> games = request(id);
            store.put(id, new Snapshot(games, Instant.now()));
            banned.remove(id);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                Duration wait = retryAfter(e).orElse(BAN);
                banned.put(id, Instant.now().plus(wait));
                log.warn("STEAM: аккаунт {} под запретом Steam, не трогаю {} мин."
                                + " Показываю последний удачный ответ, если он есть.",
                        id, wait.toMinutes());
                return;
            }
            log.error("STEAM API ERROR (аккаунт {}): {}", id, e.getMessage());
        } catch (RestClientException e) {
            log.error("STEAM API ERROR (аккаунт {}): {}", id, e.getMessage());
        }
    }

    /* Steam иногда сам говорит, сколько ждать — тогда слушаем его, а не себя. */
    private Optional<Duration> retryAfter(HttpStatusCodeException e) {
        String header = e.getResponseHeaders() == null
                ? null : e.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Duration.ofSeconds(Long.parseLong(header.trim())));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private List<GOGSteamResponseDto> request(String id) {
        SteamOwnedGamesResponse response =
                template.getForObject(OWNED_URL.formatted(token, id), SteamOwnedGamesResponse.class);
        List<GOGSteamResponseDto> games = response == null || response.getResponse() == null
                ? null : response.getResponse().getGames();
        if (games == null || games.isEmpty()) {
            /* Пустой ответ приходит и у закрытого профиля, и у аккаунта без игр.
               Отличить одно от другого Steam не даёт, но это не сбой запроса:
               аккаунт ответил, просто показать ему нечего. */
            log.info("STEAM: аккаунт {} не отдал ничего — закрытый профиль или пусто", id);
            return List.of();
        }
        return games;
    }

    /* Аккаунты перечисляются через запятую. Ноль отсеиваем вместе с пустыми:
       он годами стоял заглушкой «своего id пока нет». */
    private List<String> steamIds() {
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(id -> !id.isBlank() && !"0".equals(id))
                .toList();
    }

    /* Ответы переживают перезапуск: контейнер пересобирается часто, а каждый
       холодный старт — это пять новых обращений к Steam и шаг к очередному
       запрету. На диске они стоят копейки. */
    @PostConstruct
    void load() {
        Path file = file();
        if (!Files.isReadable(file)) {
            log.info("STEAM: сохранённых библиотек нет, буду спрашивать заново");
            return;
        }
        try {
            Map<String, Snapshot> saved = json.readValue(Files.readAllBytes(file),
                    new TypeReference<Map<String, Snapshot>>() {
                    });
            store.putAll(saved);
            log.info("STEAM: подняты библиотеки {} аккаунтов из {}", saved.size(), file);
        } catch (IOException | RuntimeException e) {
            log.error("STEAM: не прочитал {}: {}", file, e.getMessage());
        }
    }

    private void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), "steam", ".tmp");
            Files.write(temp, json.writeValueAsBytes(store));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("STEAM: не сохранил библиотеки: {}", e.getMessage());
        }
    }

    private Path file() {
        return Path.of(cacheDir, "steam-library.json");
    }

    private void pause(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static GOGSteamResponseDto copyOf(GOGSteamResponseDto source) {
        GOGSteamResponseDto copy = new GOGSteamResponseDto();
        copy.setAppid(source.getAppid());
        copy.setPlaytime_2weeks(source.getPlaytime_2weeks());
        copy.setPlaytime_forever(source.getPlaytime_forever());
        copy.setPlaytime_windows_forever(source.getPlaytime_windows_forever());
        copy.setPlaytime_disconnected(source.getPlaytime_disconnected());
        copy.setRtime_last_played(source.getRtime_last_played());
        copy.setName(source.getName());
        copy.setImg_icon_url(source.getImg_icon_url());
        return copy;
    }

    private static GOGSteamResponseDto combineOwned(GOGSteamResponseDto first, GOGSteamResponseDto second) {
        first.setPlaytime_2weeks(first.getPlaytime_2weeks() + second.getPlaytime_2weeks());
        first.setPlaytime_forever(first.getPlaytime_forever() + second.getPlaytime_forever());
        first.setPlaytime_windows_forever(
                first.getPlaytime_windows_forever() + second.getPlaytime_windows_forever());
        first.setPlaytime_disconnected(
                first.getPlaytime_disconnected() + second.getPlaytime_disconnected());
        /* Часы складываются, а «когда последний раз играл» — нет: берём самый
           поздний из аккаунтов, иначе игра уехала бы в конец списка недавних. */
        first.setRtime_last_played(
                Math.max(first.getRtime_last_played(), second.getRtime_last_played()));
        /* Название и иконка у игры одни и те же на любом аккаунте, но прийти
           могут не от каждого — берём первое непустое. */
        if (first.getName() == null || first.getName().isBlank()) {
            first.setName(second.getName());
            first.setImg_icon_url(second.getImg_icon_url());
        }
        return first;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Snapshot(List<GOGSteamResponseDto> games, Instant fetchedAt) {
    }
}
