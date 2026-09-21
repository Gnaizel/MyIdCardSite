package ru.gnaizel.service.tiktok;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.gnaizel.dto.tiktok.TikTokVideoDto;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Репосты с профиля TikTok.
 * <p>
 * Берутся у той же ручки, которой пользуется сама страница профиля:
 * она открыта, подписи запросов не требует и отвечает в том числе
 * с серверного адреса — проверено. В документированном Display API репостов
 * нет вовсе, они есть только в Research API под академическую заявку,
 * так что это единственный доступный путь.
 * <p>
 * Ручка недокументированная: TikTok может её изменить, и тогда блок просто
 * опустеет. Поэтому ни одна ошибка отсюда наружу не выходит — страница
 * должна жить и без этого блока.
 */
@Slf4j
@Service
public class TikTokServiceImpl implements TikTokService {
    private static final String REPOSTS_URL =
            "https://www.tiktok.com/api/repost/item_list/?aid=1988&secUid=%s&count=%d&cursor=0";

    /* Обложки подписаны и живут около двух суток. Час — с большим запасом:
       к моменту, когда ссылка протухнет, мы сходим за списком десятки раз. */
    private static final Duration TTL = Duration.ofHours(1);

    /* TikTok отдаёт браузерный ответ и на простой запрос, но без узнаваемого
       User-Agent начинает подсовывать проверку. */
    public static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36";

    private final RestTemplate template = timeoutedTemplate();

    private volatile List<TikTokVideoDto> cached = List.of();
    private volatile Instant cachedAt;

    @Value("${tiktok.sec-uid:}")
    private String secUid;

    @Value("${tiktok.username:}")
    private String username;

    @Value("${tiktok.limit:9}")
    private int limit;

    @Override
    public List<TikTokVideoDto> getReposts() {
        if (secUid == null || secUid.isBlank()) {
            return List.of();
        }
        if (cachedAt != null && Duration.between(cachedAt, Instant.now()).compareTo(TTL) < 0) {
            return cached;
        }

        List<TikTokVideoDto> fresh = fetch();
        /* Пустым ответом непустой список не затираем: TikTok мог просто
           моргнуть, а блок на странице из-за этого исчез бы целиком. */
        if (!fresh.isEmpty() || cached.isEmpty()) {
            cached = fresh;
        }
        cachedAt = Instant.now();
        return cached;
    }

    @Override
    public Optional<String> playAddr(String id) {
        /* Ищем по уже полученному списку, а не принимаем адрес снаружи:
           иначе ручка превратилась бы в открытый прокси, которым можно
           ходить куда угодно от имени сервера. */
        return getReposts().stream()
                .filter(video -> video.getId().equals(id))
                .map(TikTokVideoDto::getPlayAddr)
                .filter(addr -> addr != null && !addr.isBlank())
                .findFirst();
    }

    private List<TikTokVideoDto> fetch() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, UA);
        headers.set(HttpHeaders.REFERER, "https://www.tiktok.com/@" + username);

        JsonNode body;
        try {
            ResponseEntity<JsonNode> response = template.exchange(
                    REPOSTS_URL.formatted(secUid, limit), HttpMethod.GET,
                    new HttpEntity<>(headers), JsonNode.class);
            body = response.getBody();
        } catch (RestClientException e) {
            log.warn("TIKTOK: не забрал репосты: {}", e.getMessage());
            return List.of();
        }

        if (body == null || !body.has("itemList")) {
            log.warn("TIKTOK: в ответе нет списка — похоже, ручка изменилась");
            return List.of();
        }

        List<TikTokVideoDto> videos = new ArrayList<>();
        for (JsonNode item : body.path("itemList")) {
            String id = item.path("id").asText(null);
            String author = item.path("author").path("uniqueId").asText(null);
            String cover = item.path("video").path("cover").asText(null);
            if (id == null || author == null || cover == null) {
                continue;
            }
            videos.add(new TikTokVideoDto(
                    id,
                    "https://www.tiktok.com/@%s/video/%s".formatted(author, id),
                    cover,
                    item.path("desc").asText(""),
                    author,
                    "/tiktok/video/" + id,
                    item.path("video").path("playAddr").asText(null)));
            if (videos.size() >= limit) {
                break;
            }
        }
        log.info("TIKTOK: получено репостов: {}", videos.size());
        return List.copyOf(videos);
    }

    /* Ответ у этой ручки на полмегабайта, и приходит он не мгновенно —
       но и ждать его бесконечно нельзя: он рисуется на общей странице. */
    private static RestTemplate timeoutedTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return new RestTemplate(factory);
    }
}
