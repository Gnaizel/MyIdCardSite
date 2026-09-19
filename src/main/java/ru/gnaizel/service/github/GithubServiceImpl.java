package ru.gnaizel.service.github;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.gnaizel.dto.github.GithubActivityDto;
import ru.gnaizel.dto.github.LineStatsDto;
import ru.gnaizel.mapper.github.GithubMapper;
import ru.gnaizel.service.github.client.GithubAPIClient;

import java.time.Duration;
import java.time.Instant;

/**
 * Кеш здесь обязателен, а не для красоты: фронт дёргает /github вместе
 * с остальными эндпоинтами, и без него каждый заход на страницу стоил бы
 * пары запросов в GitHub.
 * <p>
 * Обе величины живут 15 минут. Строкам раньше стояли сутки — тогда они
 * собирались обходом всех репозиториев по одному; теперь это один запрос,
 * и держать число устаревшим целый день незачем.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GithubServiceImpl implements GithubService {
    private static final Duration ACTIVITY_TTL = Duration.ofMinutes(15);
    private static final Duration LINES_TTL = Duration.ofMinutes(15);

    private final GithubAPIClient client;
    private final GithubMapper mapper;

    private volatile GithubActivityDto cachedActivity;
    private volatile Instant activityAt;

    private volatile LineStatsDto cachedLines = new LineStatsDto(0, 0, false);
    private volatile Instant linesAt;

    @Override
    public GithubActivityDto getActivity() {
        if (cachedActivity != null && fresh(activityAt, ACTIVITY_TTL)) {
            return cachedActivity;
        }

        GithubActivityDto activity = mapper.mapToDto(client.getContributions(), lines());
        cachedActivity = activity;
        activityAt = Instant.now();
        return activity;
    }

    private LineStatsDto lines() {
        if (fresh(linesAt, LINES_TTL)) {
            return cachedLines;
        }
        try {
            cachedLines = client.getLineStats();
        } catch (RuntimeException e) {
            // строки — не главное: отдадим календарь без них, чем ничего
            log.error("GITHUB LINES ERROR: " + e.getMessage());
        }
        linesAt = Instant.now();
        return cachedLines;
    }

    private boolean fresh(Instant at, Duration ttl) {
        return at != null && Duration.between(at, Instant.now()).compareTo(ttl) < 0;
    }
}
