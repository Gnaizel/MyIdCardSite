package ru.gnaizel.model.games;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Game {
    int appid;

    int playtime_2weeks;

    int playtime_forever;

    int playtime_windows_forever;

    String name;

    String img_icon_url;

    String banner_url;

    LocalDateTime rtime_last_played;

    int playtime_disconnected;
}
