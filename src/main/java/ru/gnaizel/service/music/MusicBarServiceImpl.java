package ru.gnaizel.service.music;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.gnaizel.dto.music.RotationDto;
import ru.gnaizel.dto.music.TrackDto;
import ru.gnaizel.exception.RequestForTrecksException;
import ru.gnaizel.mapper.music.LastFmMapper;
import ru.gnaizel.model.music.LastFmResponse;
import ru.gnaizel.model.music.TopArtistsResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Лента треков опрашивается страницей, поэтому здесь кеш: без него каждый
 * посетитель раз в десять секунд дёргал бы last.fm персонально, и на десяти
 * открытых вкладках мы бы упёрлись в лимиты API. С кешем наружу уходит
 * не чаще одного запроса в TRACKS_TTL, сколько бы людей ни сидело на сайте.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MusicBarServiceImpl implements MusicBarService {
    /* Сколько артистов берём в ротацию. Доля лидера считается от суммы именно
       этой восьмёрки — last.fm не отдаёт общее число прослушиваний за период. */
    private static final int ROTATION_SIZE = 8;

    /* Восемь секунд — чуть меньше шага опроса страницы, чтобы «сейчас играет»
       не отставало на лишний цикл. Ротация за год меняется медленно. */
    private static final Duration TRACKS_TTL = Duration.ofSeconds(8);
    private static final Duration ROTATION_TTL = Duration.ofMinutes(5);

    private final RestTemplate template = new RestTemplate();
    private final LastFmMapper mapper;

    @Value("${last.fm.api-token}")
    String TOKEN;

    private volatile List<TrackDto> cachedTracks;
    private volatile Instant tracksAt;

    private volatile RotationDto cachedRotation;
    private volatile Instant rotationAt;

    @Override
    public List<TrackDto> getRecentTracks() {
        if (cachedTracks != null && fresh(tracksAt, TRACKS_TTL)) {
            return cachedTracks;
        }

        String url = "https://ws.audioscrobbler.com/2.0/?method=user.getrecenttracks&user=Gna1zel" +
                "&api_key=" + TOKEN + "&format=json";
        LastFmResponse response = template.getForObject(url, LastFmResponse.class);
        List<TrackDto> tracks = mapper.mapToDto(response);
        if (tracks == null) {
            throw new RequestForTrecksException("track list is null");
        }

        cachedTracks = tracks;
        tracksAt = Instant.now();
        return tracks;
    }

    @Override
    public RotationDto getRotation() {
        if (cachedRotation != null && fresh(rotationAt, ROTATION_TTL)) {
            return cachedRotation;
        }

        String url = "https://ws.audioscrobbler.com/2.0/?method=user.gettopartists&user=Gna1zel" +
                "&period=12month&limit=" + ROTATION_SIZE +
                "&api_key=" + TOKEN + "&format=json";
        TopArtistsResponse response = template.getForObject(url, TopArtistsResponse.class);
        RotationDto rotation = mapper.mapToRotation(response);
        if (rotation == null) {
            throw new RequestForTrecksException("artist list is null");
        }

        cachedRotation = rotation;
        rotationAt = Instant.now();
        return rotation;
    }

    private boolean fresh(Instant at, Duration ttl) {
        return at != null && Duration.between(at, Instant.now()).compareTo(ttl) < 0;
    }
}
