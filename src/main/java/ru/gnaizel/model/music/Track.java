package ru.gnaizel.model.music;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.lang.Nullable;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Track {
    private String name;

    private Artist artist;

    private List<Image> image;

    @Nullable
    @JsonProperty("@attr")
    private PlayNow attr;

}
