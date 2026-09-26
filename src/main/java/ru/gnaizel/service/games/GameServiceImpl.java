package ru.gnaizel.service.games;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.gnaizel.dto.games.FortniteStatsDto;
import ru.gnaizel.dto.games.GOGSteamResponseDto;
import ru.gnaizel.dto.games.GameDto;
import ru.gnaizel.dto.games.NowPlayingDto;
import ru.gnaizel.exception.GameFiltrationError;
import ru.gnaizel.mapper.game.GameMapper;
import ru.gnaizel.dto.presence.PresenceDto;
import ru.gnaizel.model.games.Game;
import ru.gnaizel.service.games.client.FortniteAPIClient;
import ru.gnaizel.service.games.client.SteamAPIClient;
import ru.gnaizel.service.presence.DiscordAppArt;
import ru.gnaizel.service.presence.PresenceService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {
    /* Кеш обязателен, а не для красоты: страница дёргает и /games, и
       /games-total-hours, каждый из них обходит все аккаунты Steam, и один
       заход выливался в десяток запросов наружу. Игры меняются медленнее,
       чем открывается страница. */
    private static final Duration TTL = Duration.ofMinutes(15);

    /* Если часть аккаунтов не ответила, сумма занижена. Держать такую
       четверть часа нельзя: счётчик часов молча просядет и застынет.
       Через минуту спросим снова — лимит частоты к тому времени отпустит. */
    private static final Duration PARTIAL_TTL = Duration.ofMinutes(1);

    private final SteamAPIClient steamAPIClient;
    private final FortniteAPIClient fortniteAPIClient;
    private final PresenceService presenceService;
    private final DiscordAppArt appArt;

    /* Discord считает «игрой» любую программу, которую узнал, в том числе
       IDE и плееры. Такие в список игр попадать не должны. */
    @Value("${discord.ignore:}")
    private String ignore;

    /* Оформление приложения Discord не меняется, а страница опрашивает
       список часто — спрашиваем его один раз на приложение. */
    private final Map<String, DiscordAppArt.Art> art = new ConcurrentHashMap<>();

    /* Сколько игр в одной странице списка. */
    @Value("${steam.recent-limit:12}")
    private int limit;

    /* Программы из библиотеки Steam (ShareX, обои и т.п.): Steam считает их
       наравне с играми, но ни в списках, ни в часах им не место. */
    @Value("${steam.not-games:}")
    private String notGames;
    private Set<Integer> notGameIds = Set.of();

    /* Больше за раз не отдаём: страница просит по дюжине, а огромный limit
       из адресной строки не должен выгружать всю библиотеку одним ответом. */
    private static final int MAX_PAGE = 60;

    @Value("${fortnite.title:Fortnite}")
    private String fortniteTitle;

    @Value("${fortnite.icon:/image/game-icon.jpg}")
    private String fortniteIcon;

    @Value("${fortnite.banner:/image/game-icon.jpg}")
    private String fortniteBanner;

    private volatile Library cached;
    private volatile Instant cachedAt;

    @PostConstruct
    void parseNotGames() {
        notGameIds = Arrays.stream(notGames.split(","))
                .map(String::trim)
                .filter(id -> id.matches("\\d+"))
                .map(Integer::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    /* Запущенная программа — это не «играет сейчас». */
    private Optional<NowPlayingDto> nowPlaying() {
        return steamAPIClient.getNowPlaying().filter(playing -> !notGameIds.contains(playing.getAppid()));
    }

    @Override
    public List<GameDto> getGames(Sort sort, int offset, int limit) {
        List<GameDto> all = sort == Sort.HOURS ? mostPlayed() : recent();
        if (all.isEmpty()) {
            throw new GameFiltrationError("ERROR IN getGames: games list is empty");
        }
        int size = limit <= 0 ? this.limit : Math.min(limit, MAX_PAGE);
        int from = Math.min(Math.max(offset, 0), all.size());
        int to = Math.min(from + size, all.size());
        return List.copyOf(all.subList(from, to));
    }

    /**
     * Запущенные игры, самые свежие первыми, без ограничения по сроку давности.
     * <p>
     * Раньше список строился пересечением библиотеки с ответом
     * GetRecentlyPlayedGames, а тот отдаёт только последние две недели. Стоило
     * неделю не играть — пересечение пустело, и блок на странице исчезал
     * целиком. Время последнего запуска есть у каждой игры в библиотеке,
     * так что достаточно отсортировать по нему.
     */
    private List<GameDto> recent() {
        Library library = library();

        List<Game> games = new ArrayList<>(library.steam().stream()
                /* Ноль — это «не запускали ни разу». Такие игры в список
                   последних не попадают: у них нет своего места на шкале. */
                .filter(gog -> gog.getRtime_last_played() > 0)
                .map(GameMapper::gogDtoToGame)
                .toList());

        library.fortnite().ifPresent(stats -> games.add(
                GameMapper.fortniteToGame(stats, fortniteTitle, fortniteIcon, fortniteBanner)));

        games.sort(Comparator.comparing(Game::getRtime_last_played).reversed());

        /* Запущенную игру поднимаем наверх независимо от того, что записано
           в библиотеке: Steam обновляет время последнего запуска не сразу,
           и пока он думает, игра стояла бы в списке на вчерашнем месте.
           А «играю прямо сейчас» — это и есть самое свежее, что может быть. */
        Optional<NowPlayingDto> now = nowPlaying();
        now.ifPresent(playing -> {
            int at = indexOf(games, playing.getAppid());
            if (at >= 0) {
                games.add(0, games.remove(at));
            } else {
                /* Среди запущенных игры нет: её запустили впервые, или она
                   чужая по Family Sharing — тогда в библиотеке её нет вовсе,
                   а у владельца она числится с нулём часов и без запусков.
                   Иконку берём из любой библиотеки, где игра есть, остальное —
                   из профиля. */
                GOGSteamResponseDto owned = library.steam().stream()
                        .filter(game -> game.getAppid() == playing.getAppid())
                        .findFirst()
                        .orElse(null);
                games.add(0, GameMapper.nowPlayingToGame(playing, owned));
            }
        });

        /* У игр без appid (Fortnite и всё, чего нет в Steam) узнать,
           запущены ли они, можно только у Discord. Спрашиваем, только если
           Steam молчит: одновременно в две игры не играют. */
        Optional<PresenceDto> presence = now.isPresent() ? Optional.empty() : presenceService.nowPlaying()
                .filter(p -> !ignored(p.getGame()));
        String discordGame = presence.map(PresenceDto::getGame).map(GameMapper::sameName).orElse("");
        boolean discordInList = false;
        if (!discordGame.isEmpty()) {
            for (int i = 0; i < games.size(); i++) {
                Game game = games.get(i);
                if (game.getAppid() == 0 && GameMapper.sameName(game.getName()).equals(discordGame)) {
                    games.add(0, games.remove(i));
                    discordInList = true;
                    break;
                }
            }
        }

        Playing current = new Playing(now.map(NowPlayingDto::getAppid).orElse(-1), discordGame,
                now.map(NowPlayingDto::getJoinUrl).orElse(null));
        List<GameDto> recent = new ArrayList<>(games.stream()
                .map(game -> toDto(game, current))
                .toList());

        /* Игру, которой нет ни в Steam, ни в Fortnite API, знает только
           Discord и только пока она запущена. Показываем её, пока идёт,
           ровно тем, что он отдал, — после выхода карточка пропадает. */
        if (presence.isPresent() && !discordInList
                && !namesCountedElsewhere(library).contains(discordGame)) {
            PresenceDto playing = presence.get();
            Optional<DiscordAppArt.Art> found = artFor(playing.getApplicationId());
            recent.add(0, GameMapper.presenceToGameDto(playing.getGame(),
                    found.map(DiscordAppArt.Art::icon).orElse(null),
                    found.map(DiscordAppArt.Art::banner).orElse(null)));
        }
        return recent;
    }

    /* Самые наигранные первыми. Здесь все игры с часами, в том числе без
       даты запуска: у давно заброшенных Steam её не хранит, а часы помнит.
       Игру, которую знает только Discord, сюда не ставим — часов у неё нет. */
    private List<GameDto> mostPlayed() {
        Library library = library();
        List<Game> games = new ArrayList<>(library.steam().stream()
                .filter(gog -> gog.getPlaytime_forever() > 0)
                .map(GameMapper::gogDtoToGame)
                .toList());
        library.fortnite().ifPresent(stats -> games.add(
                GameMapper.fortniteToGame(stats, fortniteTitle, fortniteIcon, fortniteBanner)));
        games.sort(Comparator.comparingInt(Game::getPlaytime_forever).reversed());

        /* «Играет сейчас» и здесь: запущенная игра в любом месте списка
           должна гореть так же, как в основном виде. */
        Optional<NowPlayingDto> now = nowPlaying();
        String discordGame = now.isPresent() ? "" : presenceService.nowPlaying()
                .filter(p -> !ignored(p.getGame()))
                .map(PresenceDto::getGame)
                .map(GameMapper::sameName)
                .orElse("");
        Playing playing = new Playing(now.map(NowPlayingDto::getAppid).orElse(-1), discordGame,
                now.map(NowPlayingDto::getJoinUrl).orElse(null));
        return games.stream().map(game -> toDto(game, playing)).toList();
    }

    /* Запущена ли игра, у игр из Steam узнаём по appid, у остальных —
       по имени из Discord. */
    private static GameDto toDto(Game game, Playing now) {
        boolean playing = game.getAppid() != 0
                ? game.getAppid() == now.appid()
                : !now.discordGame().isEmpty() && GameMapper.sameName(game.getName()).equals(now.discordGame());
        GameDto dto = GameMapper.gameToGameDto(game, playing, playing ? now.joinUrl() : null);
        /* Идёт игра, а Steam пишет ноль часов — значит, он их не
           знает: по Family Sharing часы у одолжившего не видны
           в API ни в одной библиотеке. «0m» тут была бы неправдой. */
        if (playing && game.getAppid() != 0 && game.getPlaytime_forever() == 0) {
            dto.setPlaytime_forever("—");
            dto.setPlaytime_2weeks("—");
        }
        return dto;
    }

    private static int indexOf(List<Game> games, int appid) {
        for (int i = 0; i < games.size(); i++) {
            if (games.get(i).getAppid() == appid) {
                return i;
            }
        }
        return -1;
    }

    /** Часы считаются по всей библиотеке, а не по показанной её верхушке. */
    @Override
    public double getTotalHours() {
        Library library = library();

        double steamMinutes = library.steam().stream()
                .mapToDouble(GOGSteamResponseDto::getPlaytime_forever)
                .sum();
        double fortniteMinutes = library.fortnite()
                .map(FortniteStatsDto::getMinutesPlayed)
                .orElse(0);

        return (steamMinutes + fortniteMinutes) / 60;
    }

    /* Всё, что ведут Steam и Fortnite API: такой игре карточка от Discord
       не нужна, у неё уже есть своя. */
    private Set<String> namesCountedElsewhere(Library library) {
        Set<String> names = new HashSet<>();
        library.steam().forEach(game -> names.add(GameMapper.sameName(game.getName())));
        if (library.fortnite().isPresent()) {
            names.add(GameMapper.sameName(fortniteTitle));
        }
        nowPlaying().ifPresent(playing -> names.add(GameMapper.sameName(playing.getName())));
        names.remove("");
        return names;
    }

    /* Неудачный ответ не запоминаем: Discord мог просто не ответить,
       и тогда на следующем опросе спросим снова. */
    private Optional<DiscordAppArt.Art> artFor(String applicationId) {
        if (applicationId == null) {
            return Optional.empty();
        }
        DiscordAppArt.Art known = art.get(applicationId);
        if (known != null) {
            return Optional.of(known);
        }
        Optional<DiscordAppArt.Art> found = appArt.find(applicationId);
        found.ifPresent(value -> art.put(applicationId, value));
        return found;
    }

    private boolean ignored(String name) {
        Set<String> names = Arrays.stream(ignore.split(","))
                .map(GameMapper::sameName)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
        return names.contains(GameMapper.sameName(name));
    }

    private Library library() {
        Library current = cached;
        Duration ttl = current != null && current.complete() ? TTL : PARTIAL_TTL;
        if (current != null && cachedAt != null
                && Duration.between(cachedAt, Instant.now()).compareTo(ttl) < 0) {
            return current;
        }

        List<GOGSteamResponseDto> steam = List.of();
        boolean complete = false;
        try {
            SteamAPIClient.Library answer = steamAPIClient.getAllGameLib();
            /* Программы отсеиваем здесь, один раз: всё остальное — списки,
               общие часы, лента — строится уже из отфильтрованного. */
            steam = answer.games().stream()
                    .filter(game -> !notGameIds.contains(game.getAppid()))
                    .toList();
            complete = answer.complete();
        } catch (RuntimeException e) {
            /* Steam мог не ответить, но Fortnite при этом жив — отдадим хоть
               что-то, вместо того чтобы обнулить весь блок. */
            log.error("GAMES: steam не ответил: {}", e.getMessage());
        }

        Library fresh = new Library(steam, fortniteAPIClient.getStats(), complete);
        if (steam.isEmpty() && fresh.fortnite().isEmpty()) {
            /* Пустой результат не кэшируем: иначе одна неудачная минута
               оставила бы блок пустым на следующие пятнадцать. */
            return current == null ? fresh : current;
        }

        /* Неполной библиотекой полную не затираем: часть аккаунтов могла
           упереться в лимит частоты, и сумма часов просела бы на глазах.
           Показываем прежнюю, правильную, и скоро попробуем снова. */
        if (!complete && current != null && current.complete()) {
            log.warn("GAMES: ответили не все аккаунты, оставляю прежнюю библиотеку");
            cachedAt = Instant.now();
            return current;
        }

        cached = fresh;
        cachedAt = Instant.now();
        return fresh;
    }

    private record Library(List<GOGSteamResponseDto> steam, Optional<FortniteStatsDto> fortnite,
                           boolean complete) {
    }

    /* Что запущено прямо сейчас: appid из Steam или имя из Discord. */
    private record Playing(int appid, String discordGame, String joinUrl) {
    }
}
