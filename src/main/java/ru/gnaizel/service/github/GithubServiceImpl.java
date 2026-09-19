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
 * Кеш здесь обязателен, а не для красоты: фронт дёргает /github вместе с
 * остальными эндпоинтами, а подсчёт строк — это запрос на каждый репозиторий.
 * Календарь живёт 15 минут, строки — сутки (GitHub всё равно пересчитывает
 * /stats/contributors примерно раз в день). Но только если ответ пришёл
 * полным: пока GitHub досчитывает статистику по свежему пушу, он отдаёт 202,
 * такие репозитории в сумму не попадают, и держать её сутки нельзя.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GithubServiceImpl implements GithubService {
    private static final Duration ACTIVITY_TTL = Duration.ofMinutes(15);
    private static final Duration LINES_TTL = Duration.ofHours(24);

    /* Если GitHub по части репозиториев ещё считал статистику, сумма вышла
       неполной, и держать её сутки нельзя: число на карточке замерзало бы
       на день после каждого пуша. Через десять минут спросим снова. */
    private static final Duration PENDING_TTL = Duration.ofMinutes(10);

    private final GithubAPIClient client;
    private final GithubMapper mapper;

    private volatile GithubActivityDto cachedActivity;
    private volatile Instant activityAt;

    private volatile LineStatsDto cachedLines = new LineStatsDto(0, 0, false, false);
    private volatile Instant linesAt;

    /* Отдельно от cachedLines: неполный ответ мы в кэш не кладём, но помнить
       о нём надо — иначе следующая попытка отложилась бы опять на сутки. */
    private volatile boolean linesPending;

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
        if (fresh(linesAt, linesPending ? PENDING_TTL : LINES_TTL)) {
            return cachedLines;
        }
        try {
            LineStatsDto fetched = client.getLineStats();
            linesPending = fetched.isPending();
            /* Неполной суммой полную не затираем: иначе число на карточке
               проседало бы каждый раз, когда GitHub берётся пересчитывать
               статистику по свежему пушу. */
            if (!fetched.isPending() || !cachedLines.isAvailable()) {
                cachedLines = fetched;
            }
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
