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
}
