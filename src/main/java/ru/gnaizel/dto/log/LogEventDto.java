package ru.gnaizel.dto.log;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Одна строка ленты: что сделал, где и когда.
 * <p>
 * Всё здесь взято из ответа API как есть. Своего ничего не досчитываем:
 * нет у источника времени события — события в ленте нет.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogEventDto {
    /** Откуда событие: github, lastfm, steam, epic. */
    private String source;

    /** Что сделал: push, star, love, play… Глагол к нему подбирает страница. */
    private String kind;

    private Instant at;

    /** Над чем: репозиторий, трек, игра. */
    private String subject;

    /** Сколько коммитов в пуше. Пусто, если GitHub не дал это выяснить. */
    private Integer count;

    /** Подробность второй строкой: сообщение коммита, часы в игре. */
    private String detail;

    private String url;
}
