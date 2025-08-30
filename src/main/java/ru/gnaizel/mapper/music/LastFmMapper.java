package ru.gnaizel.mapper.music;

import org.springframework.stereotype.Service;
import ru.gnaizel.dto.TrackDto;
import ru.gnaizel.model.music.LastFmResponse;
import ru.gnaizel.model.music.PlayNow;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class LastFmMapper {
    public List<TrackDto> mapToDto(LastFmResponse lastFmResponse) {
        return lastFmResponse.getRecentTracks().getTrack().stream()
                .map(track -> {
                    String artist = track.getArtist().getText();
                    String name = track.getName();
                    Optional<PlayNow> playNow = Optional.ofNullable(track.getAttr());
                    String imageUrl = track.getImage().stream()
                            .filter(img -> "extralarge".equals(img.getSize()))
                            .map(img -> img.getUrl())
                            .findFirst()
                            .orElse("");
                    return new TrackDto(name,
                            artist,
                            imageUrl,
                            playNow.orElse(PlayNow.builder().nowplaying(false).build()).isNowplaying());
                })
                .collect(Collectors.toList());
    }
}
