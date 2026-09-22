package ru.gnaizel.service.games;

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
import ru.gnaizel.model.games.TrackedGame;
import ru.gnaizel.repository.games.TrackedGameDayRepository;
import ru.gnaizel.repository.games.TrackedGameRepository;
import ru.gnaizel.service.games.client.FortniteAPIClient;
import ru.gnaizel.service.games.client.SteamAPIClient;
import ru.gnaizel.service.presence.PresenceService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    private final TrackedGameRepository trackedGames;
    private final TrackedGameDayRepository trackedDays;

    /* Стартовые часы для игр, которые считаем по Discord: «VALORANT=512;
       Other Game=10». Своих часов за всё время у этих игр не узнать ни из
       какого API, поэтому начальное число задаётся руками — например,
       подсмотренное на tracker.gg, — а дальше растёт само. */
    @Value("${discord.hours:}")
    private String startHours;

    @Value("${steam.recent-limit:12}")
    private int limit;

    @Value("${fortnite.title:Fortnite}")
    private String fortniteTitle;

    @Value("${fortnite.icon:/image/game-icon.jpg}")
    private String fortniteIcon;

    @Value("${fortnite.banner:/image/game-icon.jpg}")
    private String fortniteBanner;

    private volatile Library cached;
    private volatile Instant cachedAt;

    /**
     * Последние запущенные игры, без ограничения по сроку давности.
     * <p>
     * Раньше список строился пересечением библиотеки с ответом
     * GetRecentlyPlayedGames, а тот отдаёт только последние две недели. Стоило
     * неделю не играть — пересечение пустело, и блок на странице исчезал
     * целиком. Время последнего запуска есть у каждой игры в библиотеке,
     * так что достаточно отсортировать по нему и взять сколько нужно.
     */
    @Override
    public List<GameDto> getRecentlyGames() {
        Library library = library();

        List<Game> games = new ArrayList<>(library.steam().stream()
                /* Ноль — это «не запускали ни разу». Такие игры в список
                   последних не попадают: у них нет своего места на шкале. */
                .filter(gog -> gog.getRtime_last_played() > 0)
                .map(GameMapper::gogDtoToGame)
                .toList());

        library.fortnite().ifPresent(stats -> games.add(
                GameMapper.fortniteToGame(stats, fortniteTitle, fortniteIcon, fortniteBanner)));

        /* Игры, часы которых считаем сами по Discord, встают в тот же
           список наравне со всеми. Если игру с тех пор купил в Steam,
           её ведёт Steam, и второй карточки не будет. */
        Set<String> known = namesCountedElsewhere(library);
        LocalDate twoWeeksAgo = LocalDate.now(ZoneId.of("UTC+4")).minusDays(13);
        Map<String, Integer> extra = startMinutes();
        for (TrackedGame tracked : trackedGames.findAll()) {
            String key = GameMapper.sameName(tracked.getName());
            if (known.contains(key)) {
                continue;
            }
            games.add(GameMapper.trackedToGame(tracked, extra.getOrDefault(key, 0),
                    trackedDays.secondsSince(tracked.getName(), twoWeeksAgo)));
        }

        games.sort(Comparator.comparing(Game::getRtime_last_played).reversed());

        /* Запущенную игру поднимаем наверх независимо от того, что записано
           в библиотеке: Steam обновляет время последнего запуска не сразу,
           и пока он думает, игра стояла бы в списке на вчерашнем месте.
           А «играю прямо сейчас» — это и есть самое свежее, что может быть. */
        Optional<NowPlayingDto> now = steamAPIClient.getNowPlaying();
        now.ifPresent(playing -> {
            int at = indexOf(games, playing.getAppid());
            if (at >= 0) {
                games.add(0, games.remove(at));
            } else {
                /* Игры нет в библиотеке — значит запустили впервые, и до
                   следующего обновления библиотеки её неоткуда взять.
                   Показываем то, что знаем из профиля: название и appid. */
                games.add(0, GameMapper.nowPlayingToGame(playing));
            }
        });

        /* У игр без appid (Fortnite и всё, что считаем по Discord) узнать,
           запущены ли они, можно только у Discord. Спрашиваем, только если
           Steam молчит: одновременно в две игры не играют. */
        String discordGame = now.isPresent() ? "" : presenceService.nowPlaying()
                .map(PresenceDto::getGame)
                .map(GameMapper::sameName)
                .orElse("");
        if (!discordGame.isEmpty()) {
            for (int i = 0; i < games.size(); i++) {
                Game game = games.get(i);
                if (game.getAppid() == 0 && GameMapper.sameName(game.getName()).equals(discordGame)) {
                    games.add(0, games.remove(i));
                    break;
                }
            }
        }

        int playingAppid = now.map(NowPlayingDto::getAppid).orElse(-1);
        String joinUrl = now.map(NowPlayingDto::getJoinUrl).orElse(null);
        List<GameDto> recent = games.stream()
                .limit(limit)
                .map(game -> {
                    boolean playing = game.getAppid() != 0
                            ? game.getAppid() == playingAppid
                            : !discordGame.isEmpty() && GameMapper.sameName(game.getName()).equals(discordGame);
                    return GameMapper.gameToGameDto(game, playing, playing ? joinUrl : null);
                })
                .toList();

        if (recent.isEmpty()) {
            throw new GameFiltrationError("ERROR IN getRecentlyGames: games list is empty");
        }

        return recent;
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

        Set<String> known = namesCountedElsewhere(library);
        Map<String, Integer> extra = startMinutes();
        double trackedMinutes = trackedGames.findAll().stream()
                .filter(tracked -> !known.contains(GameMapper.sameName(tracked.getName())))
                .mapToDouble(tracked -> tracked.getSecondsPlayed() / 60.0
                        + extra.getOrDefault(GameMapper.sameName(tracked.getName()), 0))
                .sum();

        return (steamMinutes + fortniteMinutes + trackedMinutes) / 60;
    }

    @Override
    public boolean countedElsewhere(String name) {
        return namesCountedElsewhere(library()).contains(GameMapper.sameName(name));
    }

    /* Всё, что ведут Steam и Fortnite API. Сюда же игра, запущенная в Steam
       прямо сейчас: при первом запуске её ещё нет в библиотеке, и без этого
       Discord-счётчик завёл бы ей вторую карточку. */
    private Set<String> namesCountedElsewhere(Library library) {
        Set<String> names = new HashSet<>();
        library.steam().forEach(game -> names.add(GameMapper.sameName(game.getName())));
        if (library.fortnite().isPresent()) {
            names.add(GameMapper.sameName(fortniteTitle));
        }
        steamAPIClient.getNowPlaying().ifPresent(playing -> names.add(GameMapper.sameName(playing.getName())));
        names.remove("");
        return names;
    }

    private Map<String, Integer> startMinutes() {
        Map<String, Integer> minutes = new HashMap<>();
        for (String pair : startHours.split(";")) {
            int at = pair.lastIndexOf('=');
            if (at <= 0) {
                continue;
            }
            try {
                double hours = Double.parseDouble(pair.substring(at + 1).trim().replace(',', '.'));
                minutes.put(GameMapper.sameName(pair.substring(0, at)), (int) Math.round(hours * 60));
            } catch (NumberFormatException e) {
                log.warn("GAMES: не понял стартовые часы «{}», пропускаю", pair.trim());
            }
        }
        return minutes;
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
            steam = answer.games();
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
}
