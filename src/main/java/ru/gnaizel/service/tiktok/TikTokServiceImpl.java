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
            "https://www.tiktok.com/api/repost/item_list/?aid=1988&secUid=%s&count=%d&cursor=%s";

    /* За раз TikTok отдаёт не больше трёх десятков: на count=50 он отвечает
       пустым списком. Поэтому за длинным списком ходим страницами. */
    private static final int PAGE = 30;
    private static final int MAX_PAGES = 5;

    /* Верхнюю границу задают обложки: они подписаны и живут около двух суток,
       так что кэш обязан быть заметно короче. Нижнюю — удаления: пока список
       лежит у нас, снятый в TikTok репост продолжает висеть на странице.
       Час под второе оказался велик, четверть часа — разумный предел
       расхождения с профилем ценой четырёх заходов в час вместо одного. */
    private static final Duration TTL = Duration.ofMinutes(15);

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

    @Value("${tiktok.limit:30}")
    private int limit;

    @Override
    public List<TikTokVideoDto> getReposts() {
        if (secUid == null || secUid.isBlank()) {
            return List.of();
        }
        if (cachedAt != null && Duration.between(cachedAt, Instant.now()).compareTo(TTL) < 0) {
            return cached;
        }

        List<TikTokVideoDto> fresh = fetchAll();
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
        return find(id).map(TikTokVideoDto::getPlayAddr).filter(TikTokServiceImpl::filled);
    }

    @Override
    public Optional<String> musicAddr(String id) {
        return find(id).map(TikTokVideoDto::getMusicAddr).filter(TikTokServiceImpl::filled);
    }

    /* Ищем по уже полученному списку, а не принимаем адрес снаружи: иначе
       проксирующая ручка превратилась бы в открытый прокси, которым можно
       ходить куда угодно от имени сервера. */
    private Optional<TikTokVideoDto> find(String id) {
        return getReposts().stream().filter(video -> video.getId().equals(id)).findFirst();
    }

    private static boolean filled(String value) {
        return value != null && !value.isBlank();
    }

    private List<TikTokVideoDto> fetchAll() {
        List<TikTokVideoDto> all = new ArrayList<>();
        String cursor = "0";

        for (int page = 0; page < MAX_PAGES && all.size() < limit; page++) {
            JsonNode body = fetchPage(cursor);
            if (body == null) {
                break;
            }
            for (JsonNode item : body.path("itemList")) {
                parse(item).ifPresent(all::add);
                if (all.size() >= limit) {
                    break;
                }
            }
            if (!body.path("hasMore").asBoolean()) {
                break;
            }
            cursor = body.path("cursor").asText("0");
        }

        log.info("TIKTOK: получено репостов: {}", all.size());
        return List.copyOf(all);
    }

    private JsonNode fetchPage(String cursor) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, UA);
        headers.set(HttpHeaders.REFERER, "https://www.tiktok.com/@" + username);

        try {
            ResponseEntity<JsonNode> response = template.exchange(
                    REPOSTS_URL.formatted(secUid, PAGE, cursor), HttpMethod.GET,
                    new HttpEntity<>(headers), JsonNode.class);
            JsonNode body = response.getBody();
            if (body == null || !body.has("itemList")) {
                log.warn("TIKTOK: в ответе нет списка — похоже, ручка изменилась");
                return null;
            }
            return body;
        } catch (RestClientException e) {
            log.warn("TIKTOK: не забрал репосты: {}", e.getMessage());
            return null;
        }
    }

    /* Репосты бывают двух видов, и различать их обязательно: у фото-поста
       нет видео вовсе, зато есть несколько кадров и отдельная дорожка —
       раньше от него показывалась одна обложка и без звука. */
    private Optional<TikTokVideoDto> parse(JsonNode item) {
        String id = item.path("id").asText(null);
        String author = item.path("author").path("uniqueId").asText(null);
        if (id == null || author == null) {
            return Optional.empty();
        }

        List<String> images = images(item);
        String cover = images.isEmpty()
                ? item.path("video").path("cover").asText(null)
                : images.get(0);
        if (cover == null) {
            return Optional.empty();
        }

        TikTokVideoDto.TikTokVideoDtoBuilder video = TikTokVideoDto.builder()
                .id(id)
                .url("https://www.tiktok.com/@%s/video/%s".formatted(author, id))
                .cover(cover)
                .description(item.path("desc").asText(""))
                .author(author)
                .createdAt(item.path("createTime").asLong())
                .images(images);

        if (images.isEmpty()) {
            video.videoUrl("/tiktok/video/" + id)
                    .playAddr(item.path("video").path("playAddr").asText(null));
        } else {
            String music = item.path("music").path("playUrl").asText(null);
            if (filled(music)) {
                video.audioUrl("/tiktok/audio/" + id).musicAddr(music);
            }
            video.musicTitle(item.path("music").path("title").asText(""));
        }
        return Optional.of(video.build());
    }

    private List<String> images(JsonNode item) {
        JsonNode post = item.path("imagePost").path("images");
        if (!post.isArray() || post.isEmpty()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (JsonNode image : post) {
            JsonNode list = image.path("imageURL").path("urlList");
            if (list.isArray() && !list.isEmpty()) {
                urls.add(list.get(0).asText());
            }
        }
        return List.copyOf(urls);
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
