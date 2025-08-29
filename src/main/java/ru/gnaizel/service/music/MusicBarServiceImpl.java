package ru.gnaizel.service.music;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.gnaizel.dto.TrackDto;
import ru.gnaizel.exception.RequestForTrecksException;
import ru.gnaizel.mapper.music.LastFmMapper;
import ru.gnaizel.model.music.LastFmResponse;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MusicBarServiceImpl implements MusicBarService {
    private final LastFmMapper mapper;

    @Value("${last.fm.api-token}")
    String TOKEN;

    @Override
    public List<TrackDto> getRecentTracks() {
        RestTemplate template = new RestTemplate();
        String url = "https://ws.audioscrobbler.com/2.0/?method=user.getrecenttracks&user=Gnaizel" +
                "&api_key=" + TOKEN + "&format=json";
        LastFmResponse response = template.getForObject(url, LastFmResponse.class);
        if (mapper.mapToDto(response) == null) {
            throw new RequestForTrecksException("track list is null");
        }
        return mapper.mapToDto(response);
    }
}
