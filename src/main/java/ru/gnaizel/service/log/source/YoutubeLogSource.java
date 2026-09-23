package ru.gnaizel.service.log.source;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import ru.gnaizel.dto.log.LogEventDto;
import ru.gnaizel.service.log.LogSource;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Видео, которые я лайкнул на YouTube.
 * <p>
 * Историю просмотров YouTube API не отдаёт никому — её закрыли ещё в 2016-м.
 * Лайки отдаёт, но только с разрешения владельца аккаунта: поэтому здесь
 * OAuth, а не простой ключ. Разрешение выдаётся один раз и хранится как
 * refresh-токен в .env; как его получить — в README-LOCAL.md.
 * <p>
 * Лайки лежат в служебном плейлисте «Понравившиеся», и время добавления
 * в него — это и есть время лайка.
 */
@Component
public class YoutubeLogSource implements LogSource {
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String API = "https://www.googleapis.com/youtube/v3";
    private static final String WATCH = "https://www.youtube.com/watch?v=";

    /* Столько последних лайков идёт в ленту. */
    private static final int LIMIT = 20;

    private final RestTemplate template = new RestTemplate();

    @Value("${youtube.client-id:}")
    private String clientId;

    @Value("${youtube.client-secret:}")
    private String clientSecret;

    @Value("${youtube.refresh-token:}")
    private String refreshToken;

    /* Токен доступа живёт час — берём новый, только когда старый истекает. */
    private volatile String accessToken;
    private volatile Instant accessUntil = Instant.EPOCH;

    /* У плейлиста «Понравившиеся» свой id у каждого аккаунта. Он не меняется,
       поэтому спрашиваем один раз. */
    private volatile String likesPlaylist;

    @Override
    public String name() {
        return "youtube";
    }

    @Override
    public List<LogEventDto> fetch() {
        if (blank(clientId) || blank(clientSecret) || blank(refreshToken)) {
            return List.of();
        }

        HttpEntity<Void> auth = auth();
        JsonNode items = get(API + "/playlistItems?part=snippet,contentDetails&maxResults=" + LIMIT
                + "&playlistId=" + likesPlaylist(auth), auth).path("items");

        List<JsonNode> videos = new ArrayList<>();
        for (JsonNode item : items) {
            /* Удалённые и закрытые видео остаются в плейлисте заглушками
               без канала и картинок — в ленте им делать нечего. */
            if (item.path("snippet").hasNonNull("videoOwnerChannelTitle")) {
                videos.add(item);
            }
        }
        Map<String, String> durations = durations(videos.stream()
                .map(item -> item.path("contentDetails").path("videoId").asText())
                .toList(), auth);

        List<LogEventDto> rows = new ArrayList<>();
        for (JsonNode item : videos) {
            JsonNode snippet = item.path("snippet");
            String id = item.path("contentDetails").path("videoId").asText();
            rows.add(LogEventDto.builder()
                    .source("youtube")
                    .kind("like")
                    .at(Instant.parse(snippet.path("publishedAt").asText()))
                    .subject(snippet.path("title").asText())
                    .author(snippet.path("videoOwnerChannelTitle").asText())
                    .image(thumbnail(snippet.path("thumbnails")))
                    .duration(durations.get(id))
                    .url(WATCH + id)
                    .build());
        }
        return rows;
    }

    private String likesPlaylist(HttpEntity<Void> auth) {
        if (likesPlaylist == null) {
            String id = get(API + "/channels?part=contentDetails&mine=true", auth)
                    .path("items").path(0).path("contentDetails").path("relatedPlaylists").path("likes").asText("");
            likesPlaylist = id.isBlank() ? "LL" : id;
        }
        return likesPlaylist;
    }

    /* Длительности в плейлисте нет — она только у самих видео, зато все
       двадцать умещаются в один запрос. */
    private Map<String, String> durations(List<String> ids, HttpEntity<Void> auth) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> durations = new HashMap<>();
        for (JsonNode video : get(API + "/videos?part=contentDetails&id=" + String.join(",", ids), auth).path("items")) {
            String formatted = formatDuration(video.path("contentDetails").path("duration").asText(""));
            if (formatted != null) {
                durations.put(video.path("id").asText(), formatted);
            }
        }
        return durations;
    }

    /** «PT1H2M3S» → «1:02:03», «PT18M42S» → «18:42». У трансляций длительность нулевая — такую не пишем. */
    static String formatDuration(String iso) {
        try {
            long seconds = Duration.parse(iso).getSeconds();
            if (seconds <= 0) {
                return null;
            }
            return seconds >= 3600
                    ? "%d:%02d:%02d".formatted(seconds / 3600, seconds % 3600 / 60, seconds % 60)
                    : "%d:%02d".formatted(seconds / 60, seconds % 60);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /* medium — 320×180 без чёрных полос; у high по бокам полосы от 4:3. */
    private static String thumbnail(JsonNode thumbnails) {
        for (String size : List.of("medium", "high", "default")) {
            String url = thumbnails.path(size).path("url").asText("");
            if (!url.isBlank()) {
                return url;
            }
        }
        return null;
    }

    private HttpEntity<Void> auth() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken());
        return new HttpEntity<>(headers);
    }

    private synchronized String accessToken() {
        if (accessToken != null && Instant.now().isBefore(accessUntil)) {
            return accessToken;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        JsonNode token = template.postForObject(TOKEN_URL, new HttpEntity<>(form, headers), JsonNode.class);
        if (token == null || !token.hasNonNull("access_token")) {
            throw new IllegalStateException("YOUTUBE: Google не выдал токен доступа");
        }
        accessToken = token.path("access_token").asText();
        /* минута запаса, чтобы токен не истёк посреди запроса */
        accessUntil = Instant.now().plusSeconds(token.path("expires_in").asLong(3600) - 60);
        return accessToken;
    }

    private JsonNode get(String url, HttpEntity<Void> auth) {
        JsonNode body = template.exchange(URI.create(url), HttpMethod.GET, auth, JsonNode.class).getBody();
        if (body == null) {
            throw new IllegalStateException("YOUTUBE: пустой ответ");
        }
        return body;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
