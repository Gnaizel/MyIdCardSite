package ru.gnaizel.dto.games;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
abstract class GameDtoSteam {
    @NotNull
    int appid;

    @NotNull
    int playtime_2weeks;

    @NotNull
    int playtime_forever;

    @NotNull
    int playtime_windows_forever;
}
