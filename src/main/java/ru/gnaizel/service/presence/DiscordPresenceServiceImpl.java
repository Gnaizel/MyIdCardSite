package ru.gnaizel.service.presence;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.gnaizel.dto.presence.PresenceDto;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Во что играю прямо сейчас — через презенс Discord.
 * <p>
 * Зачем вообще: у Riot живого статуса для Valorant нет ни в каком открытом
 * виде. В официальном API его не существует, а внутренняя ручка core-game
 * требует токенов, которые добываются только из запущенного локально клиента,
 * — с сервера их взять неоткуда. Сторонние трекеры отдают историю матчей
 * и MMR, но не то, идёт ли матч сейчас.
 * <p>
 * Discord же определяет запущенную игру сам, без всякой поддержки со стороны
 * игры, и показывает её в профиле. Lanyard отдаёт этот презенс обычным GET,
 * поэтому ничего своего на машине владельца не нужно — только запущенный
 * Discord, который и так запущен.
 * <p>
 * Сервис сторонний и недокументированный договором: если он замолчит, блок
 * просто не появится. Ни одна ошибка отсюда наружу не выходит.
 */
@Slf4j
@Service
public class DiscordPresenceServiceImpl implements PresenceService {
    private static final String PRESENCE_URL = "https://api.lanyard.rest/v1/users/%s";

    /* У Discord тип активности числом: 0 — «играет». Остальные это музыка,
       стрим, произвольный статус и прочее, и к вопросу «во что играю»
       отношения не имеют. */
    private static final int PLAYING = 0;

    /* Полминуты: статус должен загораться и гаснуть на глазах, но дёргать
       чужой сервис на каждое открытие страницы незачем. */
    private static final Duration TTL = Duration.ofSeconds(30);

    private final RestTemplate template = timeoutedTemplate();

    private volatile PresenceDto cached;
    private volatile Instant cachedAt;

    /* О чём жаловались в прошлый раз. Нужно, чтобы не повторять одно и то же
       в логе каждые полминуты: «владелец не вступил в сервер Lanyard» —
       состояние настройки, а не событие, и писать его без конца незачем.
       А вот смену причины пропускать нельзя, отсюда сравнение, а не флаг. */
    private volatile String lastComplaint;

    @Value("${discord.user-id:}")
    private String userId;

    @Override
    public Optional<PresenceDto> nowPlaying() {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        if (cachedAt != null && Duration.between(cachedAt, Instant.now()).compareTo(TTL) < 0) {
            return Optional.ofNullable(cached);
        }

        /* Здесь, в отличие от ленты репостов, пустой ответ затирает прошлый:
           пустой — это и есть «вышел из игры», и держаться за старое значение
           значило бы показывать, что я играю, когда я уже нет. */
        cached = fetch();
        cachedAt = Instant.now();
        return Optional.ofNullable(cached);
    }

    private PresenceDto fetch() {
        JsonNode body;
        try {
            body = template.getForObject(PRESENCE_URL.formatted(userId), JsonNode.class);
        } catch (RestClientException e) {
            complain("не забрал презенс: " + e.getMessage());
            return null;
        }

        if (body == null || !body.path("success").asBoolean()) {
            /* Самая частая причина — user_not_monitored: владелец не состоит
               в сервере Lanyard, и бот его презенса не видит. */
            String code = body == null ? "пустой ответ" : body.path("error").path("code").asText("неизвестно");
            complain("презенс не отдан (" + code + ")");
            return null;
        }
        lastComplaint = null;

        for (JsonNode activity : body.path("data").path("activities")) {
            if (activity.path("type").asInt(-1) != PLAYING) {
                continue;
            }
            String name = activity.path("name").asText(null);
            if (name == null || name.isBlank()) {
                continue;
            }
            return PresenceDto.builder()
                    .game(name)
                    /* details Discord заполняет не всегда: у игр без Rich
                       Presence там пусто, и это нормально — название игры
                       и есть весь ответ. */
                    .details(emptyToNull(activity.path("details").asText(null)))
                    .applicationId(emptyToNull(activity.path("application_id").asText(null)))
                    .startedAt(activity.path("timestamps").path("start").isNumber()
                            ? activity.path("timestamps").path("start").asLong() : null)
                    .build();
        }
        return null;
    }

    /* Одна и та же жалоба пишется один раз: сервис опрашивается постоянно,
       и неизменная причина превратила бы лог в ленту повторов. */
    private void complain(String reason) {
        if (!reason.equals(lastComplaint)) {
            log.warn("DISCORD: {}", reason);
            lastComplaint = reason;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /* Ответ маленький, но сервис чужой: ждать его дольше пары секунд нельзя,
       он рисуется на общей странице. */
    private static RestTemplate timeoutedTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }
}
