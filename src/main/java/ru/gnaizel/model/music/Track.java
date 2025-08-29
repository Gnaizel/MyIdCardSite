package ru.gnaizel.model.music;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Track {
    private String name;

    private Artist artist;

    private List<Image> image;

}
