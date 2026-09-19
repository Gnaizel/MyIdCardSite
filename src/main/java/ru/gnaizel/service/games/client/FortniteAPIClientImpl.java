package ru.gnaizel.service.games.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.gnaizel.dto.games.FortniteStatsDto;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Статистика Fortnite через fortnite-api.com.
 * <p>
 * Своего API для библиотеки у Epic нет — на их форуме это подтвердил
 * сотрудник Epic: «we do not offer or expose an API for these specific
 * items». Зато по отдельной игре статистика доступна, и Fortnite можно
 * поставить в один ряд со Steam-играми.
 * <p>
 * Статистика должна быть открыта в самой игре: Career → приватность →
 * показывать в таблице лидеров. Иначе сервис отвечает отказом даже
 * с верным ключом, и это правильно — чужую закрытую статистику он не отдаёт.
 */
@Slf4j
@Service
public class FortniteAPIClientImpl implements FortniteAPIClient {
    private static final String URL = "https://fortnite-api.com/v2/stats/br/v2?name=%s&accountType=%s";

    private final RestTemplate template = new RestTemplate();

    @Value("${fortnite.api-key:}")
    private String apiKey;

    @Value("${fortnite.name:}")
    private String name;

    @Value("${fortnite.account-type:epic}")
    private String accountType;

    @Override
    public Optional<FortniteStatsDto> getStats() {
        if (apiKey == null || apiKey.isBlank() || name == null || name.isBlank()) {
            return Optional.empty();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, apiKey);

        JsonNode body;
        try {
            ResponseEntity<JsonNode> response = template.exchange(
                    URL.formatted(name, accountType), HttpMethod.GET,
                    new HttpEntity<>(headers), JsonNode.class);
            body = response.getBody();
        } catch (HttpStatusCodeException e) {
            log.warn("FORTNITE: {} — {}", explain(e.getStatusCode()), hideKey(e.getMessage()));
            return Optional.empty();
        } catch (RestClientException e) {
            log.error("FORTNITE API ERROR: {}", hideKey(e.getMessage()));
            return Optional.empty();
        }

        if (body == null) {
            log.error("FORTNITE API ERROR: пустой ответ");
            return Optional.empty();
        }

        JsonNode overall = body.path("data").path("stats").path("all").path("overall");
        if (overall.isMissingNode() || !overall.has("minutesPlayed")) {
            log.warn("FORTNITE: в ответе нет общей статистики");
            return Optional.empty();
        }

        return Optional.of(new FortniteStatsDto(
                overall.path("minutesPlayed").asInt(),
                overall.path("matches").asInt(),
                overall.path("wins").asInt(),
                overall.path("kills").asInt(),
                overall.path("kd").asDouble(),
                parseTime(overall.path("lastModified").asText(null))));
    }

    /* Отказы тут ожидаемые и осмысленные, поэтому переводим их на человеческий:
       иначе «закрытая статистика» выглядела бы в логе как поломка сервиса. */
    private static String explain(HttpStatusCode status) {
        if (status.isSameCodeAs(HttpStatus.FORBIDDEN)) {
            return "статистика закрыта настройками профиля";
        }
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return "аккаунт с таким ником не найден";
        }
        if (status.isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
            return "ключ не принят";
        }
        if (status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
            return "слишком часто, сервис ограничил запросы";
        }
        return "отказ " + status.value();
    }

    /* Ключ уходит заголовком, но RestTemplate печатает в ошибке весь url —
       на случай, если он однажды туда попадёт, вырезаем заранее. */
    private String hideKey(String text) {
        if (text == null) {
            return "";
        }
        return apiKey.isBlank() ? text : text.replace(apiKey, "***");
    }

    private static Instant parseTime(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            log.warn("FORTNITE: не разобрал дату {}", value);
            return Instant.EPOCH;
        }
    }
}
