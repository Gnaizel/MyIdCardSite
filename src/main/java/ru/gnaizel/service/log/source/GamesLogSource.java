package ru.gnaizel.service.log.source;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.gnaizel.dto.log.LogEventDto;
import ru.gnaizel.mapper.game.GameMapper;
import ru.gnaizel.service.games.GameService;
import ru.gnaizel.service.log.LogSource;

import java.time.ZoneId;
import java.util.List;

/**
 * Во что играл: последний запуск каждой игры из библиотек Steam и Fortnite.
 * <p>
 * Истории сессий ни одно из этих API не отдаёт, только время последнего
 * запуска — поэтому у каждой игры в ленте одна строка, самая свежая.
 * Библиотеку берём из сервиса игр: она у него уже в кеше, лишних
 * запросов в Steam лента не делает.
 */
@Component
@RequiredArgsConstructor
public class GamesLogSource implements LogSource {
    private static final int LIMIT = 10;

    /* В этом поясе GameMapper раскладывает время запуска — в нём же и собираем обратно. */
    private static final ZoneId ZONE = ZoneId.of("UTC+4");

    private final GameService gameService;

    @Override
    public String name() {
        return "games";
    }

    @Override
    public List<LogEventDto> fetch() {
        return gameService.getPlayedGames().stream()
                .limit(LIMIT)
                .map(game -> LogEventDto.builder()
                        .source(game.getAppid() != 0 ? "steam" : "epic")
                        .kind("play")
                        .at(game.getRtime_last_played().atZone(ZONE).toInstant())
                        .subject(game.getName())
                        /* баннер тот же, что в блоке игр: у Steam по appid, у Fortnite из настроек */
                        .image(game.getBanner_url())
                        .detail(game.getPlaytime_2weeks() > 0
                                ? GameMapper.formatPlaytime(game.getPlaytime_2weeks()) + " in the last 2 weeks"
                                : null)
                        .url(game.getAppid() != 0 ? "https://store.steampowered.com/app/" + game.getAppid() : null)
                        .build())
                .toList();
    }
}
