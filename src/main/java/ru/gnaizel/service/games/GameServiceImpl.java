package ru.gnaizel.service.games;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.gnaizel.dto.games.FortniteStatsDto;
import ru.gnaizel.dto.games.GOGSteamResponseDto;
import ru.gnaizel.dto.games.GameDto;
import ru.gnaizel.exception.GameFiltrationError;
import ru.gnaizel.mapper.game.GameMapper;
import ru.gnaizel.model.games.Game;
import ru.gnaizel.service.games.client.FortniteAPIClient;
import ru.gnaizel.service.games.client.SteamAPIClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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

        List<GameDto> recent = games.stream()
                .sorted(Comparator.comparing(Game::getRtime_last_played).reversed())
                .limit(limit)
                .map(GameMapper::gameToGameDto)
                .toList();

        if (recent.isEmpty()) {
            throw new GameFiltrationError("ERROR IN getRecentlyGames: games list is empty");
        }

        return recent;
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
