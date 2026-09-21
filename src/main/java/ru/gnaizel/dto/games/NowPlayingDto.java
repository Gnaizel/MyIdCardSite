package ru.gnaizel.dto.games;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Во что играют прямо сейчас. Steam отдаёт это в профиле, а не в библиотеке:
 * название и appid появляются, только пока игра запущена, и исчезают,
 * как только её закрыли.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class NowPlayingDto {
    private int appid;
    private String name;

    /* Ссылка «зайти в игру», если Steam её вообще даёт. Появляется, только
       когда игрок в лобби, куда можно присоединиться: у одиночных игр
       и вне лобби её нет, и это нормальное состояние, а не сбой. */
    private String joinUrl;
}
