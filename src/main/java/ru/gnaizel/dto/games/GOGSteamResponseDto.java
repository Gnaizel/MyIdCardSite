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
}
