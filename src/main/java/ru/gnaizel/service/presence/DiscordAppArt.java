package ru.gnaizel.service.presence;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * Иконка и обложка игры по её приложению в Discord.
 * <p>
 * У игр не из Steam картинок по appid не собрать. Зато Discord, раз уж он
 * узнал игру, знает и её оформление: ручка /applications/{id}/rpc открытая
 * и без токена отдаёт хэши иконки и обложки, а сами картинки лежат на его CDN.
 * Ответ кешируется в памяти на приложение, в базу ничего не пишется.
 */
@Slf4j
@Component
public class DiscordAppArt {
    private static final String RPC_URL = "https://discord.com/api/v10/applications/%s/rpc";
    private static final String CDN_URL = "https://cdn.discordapp.com/app-icons/%s/%s.png?size=%d";

    private final RestTemplate template = timeoutedTemplate();

    public record Art(String icon, String banner) {
    }

    public Optional<Art> find(String applicationId) {
        if (applicationId == null || !applicationId.matches("\\d{5,32}")) {
            return Optional.empty();
        }
        try {
            JsonNode app = template.getForObject(RPC_URL.formatted(applicationId), JsonNode.class);
            if (app == null) {
                return Optional.empty();
            }
            String icon = image(applicationId, app.path("icon").asText(null), 64);
            // обложки нет — пусть в баннере стоит хотя бы иконка, чем пустота
            String banner = image(applicationId, app.path("cover_image").asText(null), 1024);
            return Optional.of(new Art(icon, banner != null ? banner : image(applicationId,
                    app.path("icon").asText(null), 512)));
        } catch (RestClientException e) {
            log.warn("DISCORD: не получил оформление приложения {}: {}", applicationId, e.getMessage());
            return Optional.empty();
        }
    }

    private static String image(String applicationId, String hash, int size) {
        if (hash == null || hash.isBlank() || "null".equals(hash)) {
            return null;
        }
        return CDN_URL.formatted(applicationId, hash, size);
    }

    private static RestTemplate timeoutedTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }
}
