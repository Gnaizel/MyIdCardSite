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

    int playtime_disconnected;
}