package ru.gnaizel.mapper.music;

import org.springframework.stereotype.Service;
import ru.gnaizel.dto.TrackDto;
import ru.gnaizel.model.music.LastFmResponse;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LastFmMapper {
    public List<TrackDto> mapToDto(LastFmResponse lastFmResponse) {
        return lastFmResponse.getRecentTracks().getTrack().stream()
                .map(track -> {
                    String artist = track.getArtist().getText();
                    String name = track.getName();
                    String imageUrl = track.getImage().stream()
                            .filter(img -> "extralarge".equals(img.getSize()))
                            .map(img -> img.getUrl())
                            .findFirst()
                            .orElse(""); // если нет изображения
                    return new TrackDto(name, artist, imageUrl);
                })
                .collect(Collectors.toList());
    }
}
