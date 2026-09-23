package ru.gnaizel.service.log;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.gnaizel.dto.log.LogEventDto;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Лента собирается из ответов API при запросе и живёт только в памяти.
 * <p>
 * Кеш нужен, чтобы страница не дёргала GitHub и Last.fm на каждый заход.
 * Свежесть ленты это почти не трогает: события и так приходят с опозданием,
 * а GitHub прямо просит не опрашивать ленту событий чаще раза в минуту.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogServiceImpl implements LogService {
    private static final Duration TTL = Duration.ofMinutes(10);

    /* Больше в ленте не читают: страница показывает десяток строк и
       прокрутку, а сотня строк только утяжелила бы ответ. */
    private static final int LIMIT = 80;

    private final List<LogSource> sources;

    /* Последний удачный ответ каждого источника. Если источник на этот раз
       упал, его строки берутся отсюда: иначе один сбой GitHub выкашивал бы
       из ленты все коммиты до следующего обновления. */
    private final Map<String, List<LogEventDto>> lastGood = new ConcurrentHashMap<>();

    private volatile List<LogEventDto> cached;
    private volatile Instant cachedAt;

    @Override
    public List<LogEventDto> getLog() {
        List<LogEventDto> current = cached;
        if (current != null && Duration.between(cachedAt, Instant.now()).compareTo(TTL) < 0) {
            return current;
        }
        return refresh();
    }

    /* synchronized: если истёкший кеш застанут сразу несколько посетителей,
       наружу всё равно пойдёт один набор запросов, а не по набору на каждого. */
    private synchronized List<LogEventDto> refresh() {
        if (cached != null && Duration.between(cachedAt, Instant.now()).compareTo(TTL) < 0) {
            return cached;
        }

        List<LogEventDto> events = new ArrayList<>();
        for (LogSource source : sources) {
            try {
                List<LogEventDto> fresh = source.fetch();
                lastGood.put(source.name(), fresh);
                events.addAll(fresh);
            } catch (RuntimeException e) {
                log.warn("LOG: {} не ответил: {}", source.name(), e.getMessage());
                events.addAll(lastGood.getOrDefault(source.name(), List.of()));
            }
        }

        List<LogEventDto> result = events.stream()
                .filter(event -> event.getAt() != null)
                .sorted(Comparator.comparing(LogEventDto::getAt).reversed())
                .limit(LIMIT)
                .toList();

        cached = result;
        cachedAt = Instant.now();
        return result;
    }
}
