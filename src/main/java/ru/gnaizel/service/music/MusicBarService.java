package ru.gnaizel.service.music;

import ru.gnaizel.dto.music.RotationDto;
import ru.gnaizel.dto.music.TrackDto;

import java.util.List;

public interface MusicBarService {
    List<TrackDto> getRecentTracks();

    RotationDto getRotation();
}
