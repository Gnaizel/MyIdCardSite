package ru.gnaizel.dto.music;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class RotationDto {
    private List<TopArtistDto> artists;
    private int totalPlays;
    private int artistCount;
}
