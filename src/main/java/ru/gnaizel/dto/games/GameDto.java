package ru.gnaizel.dto.games;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameDto {
    int appid;

    String playtime_2weeks;

    String playtime_forever;

    int playtime_windows_forever;

    String name;

    String img_icon_url;

    String banner_url;

    String rtime_last_played;

    /* То же время последнего запуска, но секундами Unix: список показывает,
       сколько с него прошло, а разбирать подпись обратно в дату ненадёжно.
       Нет, если время неизвестно: Steam не хранит его у давних игр. */
    Long lastPlayedAt;

    int playtime_disconnected;

    /* Запущена прямо сейчас. Приходит не из библиотеки, а из профиля Steam,
       и живёт ровно пока игра открыта. */
    boolean playingNow;

    /* Заполнена, только если Steam дал ссылку на лобби. */
    String joinUrl;

    /* Когда началась текущая сессия, миллисекундами Unix, по Discord.
       Есть только у запущенной игры, чьи часы входят в общий счётчик:
       по нему страница докручивает общие часы, пока идёт игра. */
    Long sessionStartedAt;
}