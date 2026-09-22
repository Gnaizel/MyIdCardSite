package ru.gnaizel.dto.presence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Во что играю прямо сейчас по данным Discord.
 * <p>
 * Нужно для игр, которых нет в Steam: Valorant, например, живёт у Riot,
 * и ни одна открытая ручка не отвечает, идёт ли матч. Discord при этом
 * сам определяет запущенную игру и показывает её в профиле — этим и
 * пользуемся.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class PresenceDto {
    /** Название игры так, как его назвал Discord. Пусто — значит не играю. */
    private String game;

    /** Подпись под названием: что именно делаю, если Discord уточнил. */
    private String details;

    /** Приложение Discord, за которым он узнал игру: по нему берутся
     *  её иконка и обложка. Бывает пустым у игр, найденных только по процессу. */
    private String applicationId;
}
