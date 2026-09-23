package ru.gnaizel.service.log.source;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.gnaizel.dto.log.LogEventDto;
import ru.gnaizel.exception.RequestForTrecksException;
import ru.gnaizel.service.log.LogSource;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Треки, которые я лайкнул на Last.fm, с временем лайка и обложкой.
 * <p>
 * Аккаунтов может быть несколько: один и тот же трек, лайкнутый на обоих,
 * в ленте встаёт один раз — по самому свежему лайку.
 */
@Slf4j
@Component
public class LastFmLogSource implements LogSource {
    private static final String API = "https://ws.audioscrobbler.com/2.0/?format=json&api_key=%s";
    private static final String LOVED = API + "&method=user.getlovedtracks&user=%s&limit=%d";
    private static final String TRACK = API + "&method=track.getinfo&artist=%s&track=%s";

    /* Так Last.fm отдаёт «картинки нет»: серая звезда вместо обложки. */
    private static final String PLACEHOLDER = "2a96cbd8b46e442fc41c2b86b821562f";

    /* Лайки редкие, и двадцати хватает на недели назад. Больше — это уже
       не лента, а архив. */
    private static final int LIMIT = 20;

    private final RestTemplate template = new RestTemplate();

    /* Обложки треков. В списке лайков Last.fm вместо них отдаёт заглушку,
       настоящая есть только у альбома в описании трека — это запрос на
       каждый трек. Обложка у трека не меняется, поэтому спрашиваем один раз
       и помним до перезапуска; пустая строка — «обложки у трека нет». */
    private final Map<String, String> covers = new ConcurrentHashMap<>();

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
                .forEach(event -> loved.putIfAbsent(
                        (event.getAuthor() + " — " + event.getSubject()).toLowerCase(), event));

        List<LogEventDto> rows = new ArrayList<>(loved.values());
        rows.forEach(event -> event.setImage(cover(event.getAuthor(), event.getSubject())));
        return rows;
    }

    private List<LogEventDto> lovedBy(String user) {
        JsonNode body = call(LOVED.formatted(token, encode(user), LIMIT));
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
                    .subject(track.path("name").asText())
                    .author(track.path("artist").path("name").asText())
                    .url(track.path("url").asText(null))
                    .build());
        }
        return rows;
    }

    private String cover(String artist, String track) {
        String key = (artist + " — " + track).toLowerCase();
        String known = covers.get(key);
        if (known != null) {
            return known.isEmpty() ? null : known;
        }
        try {
            JsonNode info = call(TRACK.formatted(token, encode(artist), encode(track)));
            if (info == null) {
                return null;
            }
            if (info.has("error")) {
                /* 6 — «такого трека Last.fm не знает»: обложки не будет и потом.
                   Прочие ошибки (лимит, ключ) временные, их не запоминаем. */
                if (info.path("error").asInt() == 6) {
                    covers.put(key, "");
                }
                return null;
            }
            String found = "";
            for (JsonNode image : info.path("track").path("album").path("image")) {
                String url = image.path("#text").asText("");
                /* картинки идут от мелкой к крупной — берём последнюю настоящую */
                if (!url.isBlank() && !url.contains(PLACEHOLDER)) {
                    found = url;
                }
            }
            covers.put(key, found);
            return found.isEmpty() ? null : found;
        } catch (RestClientException e) {
            /* Сбой не запоминаем: в следующий раз спросим снова. */
            log.warn("LOG: не получил обложку трека: {}", e.getMessage());
            return null;
        }
    }

    /* Адрес уходит готовым URI: строку RestTemplate закодировал бы ещё раз,
       и кириллица в названии трека превратилась бы в %25D0… — такого трека
       Last.fm, понятно, не найдёт. */
    private JsonNode call(String url) {
        return template.getForObject(URI.create(url), JsonNode.class);
    }

    /* Пробел — %20, а не «+» от URLEncoder: так его прочтёт любой сервер. */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
