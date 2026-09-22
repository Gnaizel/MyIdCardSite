package ru.gnaizel.model.games;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Сколько наиграно в игру не из Steam за один день.
 * <p>
 * По дням, а не одной суммой: иначе нечем было бы заполнить
 * «playtime 2 weeks» — у Steam это поле есть, и карточка без него
 * отличалась бы от соседних.
 */
@Data
@Entity
@Table(name = "tracked_game_day")
@IdClass(TrackedGameDay.Key.class)
@AllArgsConstructor
@NoArgsConstructor
public class TrackedGameDay {
    @Id
    @Column(length = 128)
    private String name;

    @Id
    private LocalDate day;

    @Column(name = "seconds_played", nullable = false)
    private long secondsPlayed;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Key implements Serializable {
        private String name;
        private LocalDate day;
    }
}
