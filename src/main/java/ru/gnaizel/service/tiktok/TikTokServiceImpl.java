package ru.gnaizel.service.tiktok;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.gnaizel.dto.tiktok.TikTokVideoDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Репосты с профиля TikTok.
 * <p>
 * Берутся у той же ручки, которой пользуется сама страница профиля:
 * она открыта, подписи запросов не требует и отвечает в том числе
 * с серверного адреса — проверено. В документированном Display API репостов
 * нет вовсе, они есть только в Research API под академическую заявку,
 * так что это единственный доступный путь.
 * <p>
 * Ручка недокументированная: TikTok может её изменить, и тогда блок просто
 * опустеет. Поэтому ни одна ошибка отсюда наружу не выходит — страница
 * должна жить и без этого блока.
 */
@Slf4j
@Service
public class TikTokServiceImpl implements TikTokService {
    private static final String REPOSTS_URL =
            "https://www.tiktok.com/api/repost/item_list/?aid=1988&secUid=%s&count=%d&cursor=%s";

    /* За раз TikTok отдаёт не больше трёх десятков: на count=50 он отвечает
       пустым списком. Поэтому за длинным списком ходим страницами. */
    private static final int PAGE = 30;
    private static final int MAX_PAGES = 5;

    /* Обложки подписаны и живут около двух суток. Час — с большим запасом:
       к моменту, когда ссылка протухнет, мы сходим за списком десятки раз. */
    private static final Duration TTL = Duration.ofHours(1);

    /* TikTok отдаёт браузерный ответ и на простой запрос, но без узнаваемого
       User-Agent начинает подсовывать проверку. */
    public static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36";

    private final RestTemplate template = timeoutedTemplate();

    private final ObjectMapper json = new ObjectMapper();

    /* Когда каждый пост впервые попался нам на глаза. TikTok не отдаёт время
       репоста вовсе — ни поля, ни намёка, — поэтому засекаем сами. Для всего,
       что появится дальше, это и есть время репоста с точностью до часа.
       Ноль означает «лежало ещё до того, как мы начали смотреть». */
    private final Map<String, Long> firstSeen = new ConcurrentHashMap<>();

    private volatile List<TikTokVideoDto> cached = List.of();
    private volatile Instant cachedAt;

    @Value("${tiktok.sec-uid:}")
    private String secUid;

    @Value("${tiktok.username:}")
    private String username;

    @Value("${tiktok.limit:30}")
    private int limit;

    @Value("${tiktok.cache-dir:${avatar.dir:./data}}")
    private String cacheDir;

    @Override
    public List<TikTokVideoDto> getReposts() {
        if (secUid == null || secUid.isBlank()) {
            return List.of();
        }
        if (cachedAt != null && Duration.between(cachedAt, Instant.now()).compareTo(TTL) < 0) {
            return cached;
        }

        List<TikTokVideoDto> fresh = fetchAll();
        /* Пустым ответом непустой список не затираем: TikTok мог просто
           моргнуть, а блок на странице из-за этого исчез бы целиком. */
        if (!fresh.isEmpty() || cached.isEmpty()) {
            cached = fresh;
        }
        cachedAt = Instant.now();
        return cached;
    }

    @Override
    public Optional<String> playAddr(String id) {
        return find(id).map(TikTokVideoDto::getPlayAddr).filter(TikTokServiceImpl::filled);
    }

    @Override
    public Optional<String> musicAddr(String id) {
        return find(id).map(TikTokVideoDto::getMusicAddr).filter(TikTokServiceImpl::filled);
    }

    /* Ищем по уже полученному списку, а не принимаем адрес снаружи: иначе
       проксирующая ручка превратилась бы в открытый прокси, которым можно
       ходить куда угодно от имени сервера. */
    private Optional<TikTokVideoDto> find(String id) {
        return getReposts().stream().filter(video -> video.getId().equals(id)).findFirst();
    }

    private static boolean filled(String value) {
        return value != null && !value.isBlank();
    }

    /* Отметки переживают перезапуск: без этого каждая пересборка контейнера
       начинала бы отсчёт заново, и все посты снова выглядели бы «только что
       увиденными». */
    @PostConstruct
    void loadSeen() {
        Path file = seenFile();
        if (!Files.isReadable(file)) {
            return;
        }
        try {
            firstSeen.putAll(json.readValue(Files.readAllBytes(file),
                    new TypeReference<Map<String, Long>>() {
                    }));
            log.info("TIKTOK: подняты отметки времени по {} постам", firstSeen.size());
        } catch (IOException | RuntimeException e) {
            log.error("TIKTOK: не прочитал отметки времени: {}", e.getMessage());
        }
    }

    private void saveSeen() {
        Path file = seenFile();
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), "tiktok", ".tmp");
            Files.write(temp, json.writeValueAsBytes(new HashMap<>(firstSeen)));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("TIKTOK: не сохранил отметки времени: {}", e.getMessage());
        }
    }

    private Path seenFile() {
        return Path.of(cacheDir, "tiktok-seen.json");
    }

    /* Первый запуск особенный: эти посты лежали и до нас, и выдавать их
       за «только что репостнутые» было бы враньём. Помечаем нулём — значит
       времени репоста мы не знаем и покажем возраст самого видео. */
    private void mark(List<String> ids) {
        boolean seeding = firstSeen.isEmpty();
        long now = Instant.now().getEpochSecond();
        boolean added = false;
        for (String id : ids) {
            if (!firstSeen.containsKey(id)) {
                firstSeen.put(id, seeding ? 0L : now);
                added = true;
            }
        }
        if (added) {
            saveSeen();
        }
    }

    /* Тот же формат, что у треков: 5m, 2h, 3d. */
    private static String ago(Instant moment) {
        long minutes = Duration.between(moment, Instant.now()).toMinutes();
        if (minutes < 1) {
            return "now";
        }
        if (minutes > 1440) {
            return (minutes / 1440) + "d";
        }
        if (minutes > 60) {
            return (minutes / 60) + "h";
        }
        return minutes + "m";
    }

    private List<TikTokVideoDto> fetchAll() {
        List<TikTokVideoDto> all = new ArrayList<>();
        String cursor = "0";

        for (int page = 0; page < MAX_PAGES && all.size() < limit; page++) {
            JsonNode body = fetchPage(cursor);
            if (body == null) {
                break;
            }
            for (JsonNode item : body.path("itemList")) {
                parse(item).ifPresent(all::add);
                if (all.size() >= limit) {
                    break;
                }
            }
            if (!body.path("hasMore").asBoolean()) {
                break;
            }
            cursor = body.path("cursor").asText("0");
        }

        mark(all.stream().map(TikTokVideoDto::getId).toList());
        for (TikTokVideoDto video : all) {
            Long seen = firstSeen.get(video.getId());
            if (seen != null && seen > 0) {
                video.setAge(ago(Instant.ofEpochSecond(seen)));
            }
            /* Если времени репоста не знаем — не пишем ничего. Дата, когда
               автор выложил видео, тут стояла раньше и только путала: это
               чужое действие и чужое время, а спрашивали про своё. */
        }

        log.info("TIKTOK: получено репостов: {}", all.size());
        return List.copyOf(all);
    }

    private JsonNode fetchPage(String cursor) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, UA);
        headers.set(HttpHeaders.REFERER, "https://www.tiktok.com/@" + username);

        try {
            ResponseEntity<JsonNode> response = template.exchange(
                    REPOSTS_URL.formatted(secUid, PAGE, cursor), HttpMethod.GET,
                    new HttpEntity<>(headers), JsonNode.class);
            JsonNode body = response.getBody();
            if (body == null || !body.has("itemList")) {
                log.warn("TIKTOK: в ответе нет списка — похоже, ручка изменилась");
                return null;
            }
            return body;
        } catch (RestClientException e) {
            log.warn("TIKTOK: не забрал репосты: {}", e.getMessage());
            return null;
        }
    }

    /* Репосты бывают двух видов, и различать их обязательно: у фото-поста
       нет видео вовсе, зато есть несколько кадров и отдельная дорожка —
       раньше от него показывалась одна обложка и без звука. */
    private Optional<TikTokVideoDto> parse(JsonNode item) {
        String id = item.path("id").asText(null);
        String author = item.path("author").path("uniqueId").asText(null);
        if (id == null || author == null) {
            return Optional.empty();
        }

        List<String> images = images(item);
        String cover = images.isEmpty()
                ? item.path("video").path("cover").asText(null)
                : images.get(0);
        if (cover == null) {
            return Optional.empty();
        }

        TikTokVideoDto.TikTokVideoDtoBuilder video = TikTokVideoDto.builder()
                .id(id)
                .url("https://www.tiktok.com/@%s/video/%s".formatted(author, id))
                .cover(cover)
                .description(item.path("desc").asText(""))
                .author(author)
                .images(images);

        if (images.isEmpty()) {
            video.videoUrl("/tiktok/video/" + id)
                    .playAddr(item.path("video").path("playAddr").asText(null));
        } else {
            String music = item.path("music").path("playUrl").asText(null);
            if (filled(music)) {
                video.audioUrl("/tiktok/audio/" + id).musicAddr(music);
            }
            video.musicTitle(item.path("music").path("title").asText(""));
        }
        return Optional.of(video.build());
    }

    private List<String> images(JsonNode item) {
        JsonNode post = item.path("imagePost").path("images");
        if (!post.isArray() || post.isEmpty()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (JsonNode image : post) {
            JsonNode list = image.path("imageURL").path("urlList");
            if (list.isArray() && !list.isEmpty()) {
                urls.add(list.get(0).asText());
            }
        }
        return List.copyOf(urls);
    }

    /* Ответ у этой ручки на полмегабайта, и приходит он не мгновенно —
       но и ждать его бесконечно нельзя: он рисуется на общей странице. */
    private static RestTemplate timeoutedTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return new RestTemplate(factory);
    }
}
