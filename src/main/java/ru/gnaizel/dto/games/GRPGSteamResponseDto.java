package ru.gnaizel.dto.games;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GRPGSteamResponseDto extends GameDtoSteam { // GetRecentlyPlayedGames Steam Response
    @NotBlank
    String name;

    @NotBlank
    String img_icon_url;

}
