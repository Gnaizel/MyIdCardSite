package ru.gnaizel.dto.music;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class TrackDto {
    private String name;
    private String artist;
    private String imageUrl;
    private boolean attr;
    private String timeFromLastListen;
}
