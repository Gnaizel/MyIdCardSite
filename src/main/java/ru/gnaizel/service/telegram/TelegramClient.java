package ru.gnaizel.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Тонкая обёртка над Bot API: знает про токен, таймауты и то, что токен
 * нельзя пускать в логи. Разбирать ответы целиком в DTO смысла нет —
 * из каждого метода нужны два-три поля, поэтому наружу отдаётся JsonNode.
 */
@Slf4j
@Service
public class TelegramClient {
    private static final String API = "https://api.telegram.org/bot%s/%s";
    private static final String FILES = "https://api.telegram.org/file/bot%s/%s";

    /* Обычные вызовы отвечают сразу; getUpdates намеренно висит до тридцати
       секунд, ожидая новых сообщений, поэтому для него нужен отдельный
       клиент с длинным таймаутом чтения. */
    private final RestTemplate quick = template(Duration.ofSeconds(10));
    private final RestTemplate patient = template(Duration.ofSeconds(90));

    @Value("${telegram.bot-token:}")
    private String botToken;

    @Value("${telegram.chat-id:}")
    private String chatId;

    public boolean configured() {
        return botToken != null && !botToken.isBlank()
                && chatId != null && !chatId.isBlank();
    }

    public String chatId() {
        return chatId;
    }

    /** Ответ Bot API при успехе, иначе пусто: ошибку метод пишет в лог сам. */
    public Optional<JsonNode> call(String method, Map<String, Object> params) {
        return call(method, params, false);
    }

    /** То же самое, но для долгого getUpdates. */
    public Optional<JsonNode> callLong(String method, Map<String, Object> params) {
        return call(method, params, true);
    }

    private Optional<JsonNode> call(String method, Map<String, Object> params, boolean longPoll) {
        if (!configured()) {
            return Optional.empty();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            RestTemplate template = longPoll ? patient : quick;
            ResponseEntity<JsonNode> response = template.postForEntity(
                    API.formatted(botToken, method), new HttpEntity<>(params, headers), JsonNode.class);
            JsonNode body = response.getBody();
            if (body == null || !body.path("ok").asBoolean()) {
                log.error("TELEGRAM {}: {}", method, hideToken(String.valueOf(body)));
                return Optional.empty();
            }
            return Optional.of(body.path("result"));
        } catch (RestClientException e) {
            /* RestTemplate вписывает в текст ошибки весь url, а в url лежит
               токен — без вырезания он оседает в логах контейнера, которые
               защищены куда хуже, чем .env с правами 600. */
            log.error("TELEGRAM {}: {}", method, hideToken(e.getMessage()));
            return Optional.empty();
        }
    }

    /** Содержимое файла по file_path из getFile. */
    public Optional<byte[]> download(String filePath) {
        if (!configured()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(
                    quick.getForObject(FILES.formatted(botToken, filePath), byte[].class));
        } catch (RestClientException e) {
            log.error("TELEGRAM download: {}", hideToken(e.getMessage()));
            return Optional.empty();
        }
    }

    public String hideToken(String text) {
        if (text == null) {
            return "";
        }
        return botToken == null || botToken.isBlank() ? text : text.replace(botToken, "***");
    }

    /* У RestTemplate по умолчанию таймаутов нет вообще: подвисший телеграм
       занял бы вызывающий поток навсегда. */
    private static RestTemplate template(Duration read) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(read);
        return new RestTemplate(factory);
    }
}
