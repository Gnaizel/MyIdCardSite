package ru.gnaizel.model.music;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlayNow {
    private boolean nowplaying;
}
