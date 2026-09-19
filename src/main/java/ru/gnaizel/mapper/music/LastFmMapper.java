package ru.gnaizel.mapper.music;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.gnaizel.dto.music.RotationDto;
import ru.gnaizel.dto.music.TopArtistDto;
import ru.gnaizel.dto.music.TrackDto;
import ru.gnaizel.model.music.Image;
import ru.gnaizel.model.music.LastFmResponse;
import ru.gnaizel.model.music.PlayNow;
import ru.gnaizel.model.music.TopArtist;
import ru.gnaizel.model.music.TopArtistsResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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

    public RotationDto mapToRotation(TopArtistsResponse response) {
        if (response == null
                || response.getTopArtists() == null
                || response.getTopArtists().getArtist() == null) {
            return null;
        }

        List<TopArtist> artists = response.getTopArtists().getArtist();
        int total = artists.stream().mapToInt(artist -> plays(artist.getPlaycount())).sum();

        List<TopArtistDto> top = new ArrayList<>();
        for (TopArtist artist : artists) {
            int plays = plays(artist.getPlaycount());
            // доля от прослушиваний внутри топа: делить не на что, если он пуст
            int share = total == 0 ? 0 : Math.round(plays * 100f / total);
            top.add(new TopArtistDto(artist.getName(), plays, share));
        }

        return new RotationDto(top, total, top.size());
    }

    /** last.fm отдаёт playcount строкой и иногда пустой. */
    private int plays(String playcount) {
        try {
            return playcount == null || playcount.isBlank() ? 0 : Integer.parseInt(playcount.trim());
        } catch (NumberFormatException e) {
            log.warn("LAST.FM: playcount is not a number: " + playcount);
            return 0;
        }
    }
}
