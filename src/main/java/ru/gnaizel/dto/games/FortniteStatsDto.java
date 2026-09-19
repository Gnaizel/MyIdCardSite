package ru.gnaizel.dto.games;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Статистика Fortnite за всё время, режим overall.
 * <p>
 * Epic не отдаёт библиотеку целиком — такого API просто нет, это подтверждали
 * на их форуме. Зато по отдельной игре статистика есть, и Fortnite встаёт
 * в общий список играми наравне со Steam.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class FortniteStatsDto {
    private int minutesPlayed;
    private int matches;
    private int wins;
    private int kills;
    private double kd;

    /* Время последнего обновления статистики. Точного «когда последний раз
       запускал» Epic не даёт, но статистика меняется только от игры, так что
       на шкале последних запусков это честная отметка. */
    private Instant lastModified;
}
