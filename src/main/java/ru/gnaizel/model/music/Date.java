package ru.gnaizel.model.music;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Date {
    Long uts;

    @JsonProperty("#text")
    String date;
}
