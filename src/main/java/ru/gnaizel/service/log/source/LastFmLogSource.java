package ru.gnaizel.service.log.source;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ru.gnaizel.dto.log.LogEventDto;
import ru.gnaizel.exception.RequestForTrecksException;
import ru.gnaizel.service.log.LogSource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Треки, которые я лайкнул на Last.fm, с временем лайка.
 * <p>
 * Аккаунтов может быть несколько: один и тот же трек, лайкнутый на обоих,
 * в ленте встаёт один раз — по самому свежему лайку.
 */
@Component
public class LastFmLogSource implements LogSource {
    private static final String URL = "https://ws.audioscrobbler.com/2.0/?method=user.getlovedtracks"
            + "&user=%s&limit=%d&api_key=%s&format=json";

    /* Лайки редкие, и двадцати хватает на недели назад. Больше — это уже
       не лента, а архив. */
    private static final int LIMIT = 20;

    private final RestTemplate template = new RestTemplate();

    @Value("${last.fm.api-token}")
    private String token;

    @Value("${last.fm.loved-users:}")
    private String users;

    @Override
    public String name() {
        return "last.fm";
    }

    @Override
    public List<LogEventDto> fetch() {
        if (token == null || token.isBlank()) {
            return List.of();
        }

        Map<String, LogEventDto> loved = new LinkedHashMap<>();
        Arrays.stream(users.split(","))
                .map(String::trim)
                .filter(user -> !user.isEmpty())
                .flatMap(user -> lovedBy(user).stream())
                .sorted(Comparator.comparing(LogEventDto::getAt).reversed())
                .forEach(event -> loved.putIfAbsent(event.getSubject().toLowerCase(), event));
        return new ArrayList<>(loved.values());
    }

    private List<LogEventDto> lovedBy(String user) {
        JsonNode body = template.getForObject(
                URL.formatted(URLEncoder.encode(user, StandardCharsets.UTF_8), LIMIT, token), JsonNode.class);
        if (body == null || body.has("error")) {
            throw new RequestForTrecksException("loved tracks: "
                    + (body == null ? "empty response" : body.path("message").asText()));
        }

        /* Один-единственный трек Last.fm отдаёт объектом, а не списком
           из одного элемента — поэтому обходим оба случая. */
        JsonNode tracks = body.path("lovedtracks").path("track");
        List<JsonNode> list = new ArrayList<>();
        if (tracks.isArray()) {
            tracks.forEach(list::add);
        } else if (tracks.isObject()) {
            list.add(tracks);
        }

        List<LogEventDto> rows = new ArrayList<>();
        for (JsonNode track : list) {
            long uts = track.path("date").path("uts").asLong(0);
            if (uts == 0) {
                continue;
            }
            rows.add(LogEventDto.builder()
                    .source("lastfm")
                    .kind("love")
                    .at(Instant.ofEpochSecond(uts))
                    .subject(track.path("artist").path("name").asText() + " — " + track.path("name").asText())
                    .url(track.path("url").asText(null))
                    .build());
        }
        return rows;
    }
}
