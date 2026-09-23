package ru.gnaizel.dto.log;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Одна строка ленты: что сделал, где и когда — и всё, из чего страница
 * соберёт карточку события.
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
    /** Откуда событие: github, youtube, lastfm, steam, epic. */
    private String source;

    /** Что сделал: push, star, like, love, play… От него зависит вид карточки. */
    private String kind;

    private Instant at;

    /** Над чем: репозиторий, видео, трек, игра. */
    private String subject;

    /** Чьё: исполнитель трека, канал видео. */
    private String author;

    /** Сколько коммитов в пуше. Пусто, если GitHub не дал это выяснить. */
    private Integer count;

    /** Подробность: сообщение коммита, часы в игре, описание репозитория. */
    private String detail;

    /** Картинка карточки: кадр видео, обложка, баннер игры, аватарка. */
    private String image;

    /** Длительность видео, «18:42». */
    private String duration;

    /** Коммиты пуша, от свежего к старому. */
    private List<Commit> commits;

    /** Репозиторий: основной язык, его цвет на GitHub и звёзды. */
    private String language;

    private String languageColor;

    private Integer stars;

    private String url;

    public record Commit(String sha, String message) {
    }
}
