package ru.gnaizel.dto.music;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class TopArtistDto {
    private String name;
    private int plays;
    private int share;  // процент от прослушиваний внутри топа
}
