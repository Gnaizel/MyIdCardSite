package ru.gnaizel.model.music;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TopArtist {
    private String name;
    private String playcount;  // last.fm отдаёт число строкой
}
