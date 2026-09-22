package ru.gnaizel.model.games;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Игра не из Steam, замеченная через презенс Discord.
 * <p>
 * Ни у Riot, ни у кого-то ещё нет открытой ручки с наигранными часами,
 * поэтому время считаем сами: пока Discord видит игру, оно копится здесь.
 * Карточка живёт и после выхода из игры — как у любой игры из Steam.
 */
@Data
@Entity
@Table(name = "tracked_game")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrackedGame {
    /** Название так, как его пишет Discord. Оно же ключ: другого у игры нет. */
    @Id
    @Column(length = 128)
    private String name;

    @Column(name = "application_id", length = 32)
    private String applicationId;

    @Column(name = "icon_url", length = 256)
    private String iconUrl;

    @Column(name = "banner_url", length = 256)
    private String bannerUrl;

    /** Всего насчитано нами, без стартовых часов из настроек. */
    @Column(name = "seconds_played", nullable = false)
    private long secondsPlayed;

    @Column(name = "last_played", nullable = false)
    private Instant lastPlayed;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;
}
