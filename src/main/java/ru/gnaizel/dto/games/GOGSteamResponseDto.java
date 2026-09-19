package ru.gnaizel.dto.games;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode(callSuper = true)
// GetOwnedGames
public class GOGSteamResponseDto extends GameDtoSteam {
    @NotNull
    long rtime_last_played;
    @NotNull
    int playtime_disconnected;

    /* Название и иконку Steam отдаёт только при include_appinfo=1. Раньше их
       брали из GetRecentlyPlayedGames, и из-за этого список игр жил ровно две
       недели: не играл — пересечение пустое, список исчезал. */
    String name;
    String img_icon_url;
}
