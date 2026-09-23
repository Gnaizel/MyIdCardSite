package ru.gnaizel.service.log;

import ru.gnaizel.dto.log.LogEventDto;

import java.util.List;

/**
 * Один источник ленты. Каждый ходит в своё API и сам решает, какие из
 * его событий стоят строки; ошибка одного не должна гасить остальные.
 */
public interface LogSource {
    /** Имя для логов. */
    String name();

    List<LogEventDto> fetch();
}
