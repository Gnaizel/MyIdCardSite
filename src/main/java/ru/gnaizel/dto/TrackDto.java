package ru.gnaizel.dto;

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
}
