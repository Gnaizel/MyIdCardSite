package ru.gnaizel.mapper.music;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.gnaizel.dto.TrackDto;
import ru.gnaizel.model.music.Image;
import ru.gnaizel.model.music.LastFmResponse;
import ru.gnaizel.model.music.PlayNow;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LastFmMapper {
    public List<TrackDto> mapToDto(LastFmResponse lastFmResponse) {
        return lastFmResponse.getRecentTracks().getTrack().stream()
                .map(track -> {
                    String artist = track.getArtist().getText();
                    String name = track.getName();
                    Optional<PlayNow> playNow = Optional.ofNullable(track.getAttr());

                    // Прошедшее время с последнего прослушивания трека
                    String timeFromLastListen;
                    if (track.getDate() != null) {
                        Instant dateInstant = Instant.ofEpochSecond(track.getDate().getUts());
                        Instant now = Instant.now();

                        long minutesLatter = Duration.between(dateInstant, now).toMinutes();
                        if (minutesLatter > 1440) { // Дни
                            timeFromLastListen = (minutesLatter / 1440) + "d";
                        } else if (minutesLatter > 60) { // Часы
                            timeFromLastListen = (minutesLatter / 60) + "h";
                        } else { // Минуты
                            timeFromLastListen = minutesLatter + "m";
                        }
                    } else {
                        timeFromLastListen = "";
                    }

                    String imageUrl = track.getImage().stream()
                            .filter(img -> "extralarge".equals(img.getSize()))
                            .map(Image::getUrl)
                            .findFirst()
                            .orElse("");
                    return new TrackDto(name,
                            artist,
                            imageUrl,
                            playNow.orElse(PlayNow.builder().nowplaying(false).build()).isNowplaying(),
                            timeFromLastListen);
                })
                .collect(Collectors.toList());
    }
}
