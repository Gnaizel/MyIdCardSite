package ru.gnaizel.service.games;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.gnaizel.dto.presence.PresenceDto;
import ru.gnaizel.mapper.game.GameMapper;
import ru.gnaizel.model.games.TrackedGame;
import ru.gnaizel.model.games.TrackedGameDay;
import ru.gnaizel.repository.games.TrackedGameDayRepository;
import ru.gnaizel.repository.games.TrackedGameRepository;
import ru.gnaizel.service.presence.DiscordAppArt;
import ru.gnaizel.service.presence.PresenceService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Считает часы в играх не из Steam по презенсу Discord.
 * <p>
 * Раз в минуту смотрит, во что я играю. Если это игра, которую уже ведёт
 * Steam или Fortnite API, — пропускает: у них часы свои и точные. Иначе
 * добавляет прошедшее с прошлой проверки время к этой игре.
 * <p>
 * Прошедшее время ограничено сверху: если сервер спал или Discord не
 * отвечал полчаса, это не значит, что всё это время я играл.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaytimeTracker {
    private static final Duration MAX_GAP = Duration.ofMinutes(3);
    private static final ZoneId ZONE = ZoneId.of("UTC+4");

    private final PresenceService presenceService;
    private final GameService gameService;
    private final DiscordAppArt appArt;
    private final TrackedGameRepository games;
    private final TrackedGameDayRepository days;

    /* Discord считает «игрой» любую программу, которую узнал, в том числе
       IDE и плееры. Такие в список игр попадать не должны. */
    @Value("${discord.ignore:}")
    private String ignore;

    private volatile String lastGame;
    private volatile Instant lastTick;

    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    @Transactional
    public void tick() {
        Instant now = Instant.now();
        PresenceDto presence = presenceService.nowPlaying()
                .filter(p -> !ignored(p.getGame()) && !gameService.countedElsewhere(p.getGame()))
                .orElse(null);
        String game = presence == null ? null : presence.getGame();

        String previous = lastGame;
        Instant previousTick = lastTick;
        lastGame = game;
        lastTick = now;
        if (game == null) {
            return;
        }

        TrackedGame tracked = games.findById(game).orElseGet(() -> {
            log.info("GAMES: начинаю считать часы для «{}»", game);
            return TrackedGame.builder().name(game).firstSeen(now).lastPlayed(now).build();
        });

        if (tracked.getIconUrl() == null && presence.getApplicationId() != null) {
            tracked.setApplicationId(presence.getApplicationId());
            appArt.find(presence.getApplicationId()).ifPresent(art -> {
                tracked.setIconUrl(art.icon());
                tracked.setBannerUrl(art.banner());
            });
        }

        /* Засчитываем отрезок, только если и на прошлой проверке была эта же
           игра: первая минута сессии теряется, зато не приписываем игре
           время, когда её ещё не было запущено. */
        if (game.equals(previous) && previousTick != null) {
            Duration gap = Duration.between(previousTick, now);
            long seconds = Math.min(gap.getSeconds(), MAX_GAP.getSeconds());
            if (seconds > 0) {
                tracked.setSecondsPlayed(tracked.getSecondsPlayed() + seconds);
                LocalDate today = LocalDate.now(ZONE);
                TrackedGameDay day = days.findById(new TrackedGameDay.Key(game, today))
                        .orElseGet(() -> new TrackedGameDay(game, today, 0));
                day.setSecondsPlayed(day.getSecondsPlayed() + seconds);
                days.save(day);
            }
        }

        tracked.setLastPlayed(now);
        games.save(tracked);
    }

    private boolean ignored(String name) {
        Set<String> names = Arrays.stream(ignore.split(","))
                .map(GameMapper::sameName)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
        return names.contains(GameMapper.sameName(name));
    }
}
