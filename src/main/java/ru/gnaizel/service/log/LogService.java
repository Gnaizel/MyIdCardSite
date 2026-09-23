package ru.gnaizel.service.log;

import ru.gnaizel.dto.log.LogEventDto;

import java.util.List;

public interface LogService {
    /** Все события из всех источников, самые свежие первыми. */
    List<LogEventDto> getLog();
}
