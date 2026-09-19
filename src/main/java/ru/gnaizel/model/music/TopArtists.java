package ru.gnaizel.model.music;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TopArtists {
    private List<TopArtist> artist;
}
