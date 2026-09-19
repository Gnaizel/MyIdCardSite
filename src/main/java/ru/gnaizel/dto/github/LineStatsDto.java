package ru.gnaizel.dto.github;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Внутренний носитель: сумма строк по всем репозиториям за год. */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class LineStatsDto {
    private long added;
    private long removed;
    private boolean available;
}
