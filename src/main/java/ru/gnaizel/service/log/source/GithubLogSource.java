package ru.gnaizel.service.log.source;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.gnaizel.dto.log.LogEventDto;
import ru.gnaizel.service.github.client.GithubAPIClient;
import ru.gnaizel.service.github.client.GithubAPIClient.Push;
import ru.gnaizel.service.github.client.GithubAPIClient.PushInfo;
import ru.gnaizel.service.log.LogSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Пуши, звёзды, новые репозитории, пулл-реквесты — из публичной ленты
 * событий GitHub. Приватные репозитории сюда не попадают: их имена
 * на странице светить незачем.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GithubLogSource implements LogSource {
    private static final String GITHUB = "https://github.com/";

    /* Больше ста событий GitHub за раз не отдаёт, так что это «все пуши»:
       предел только страхует размер запроса, если он когда-нибудь это изменит. */
    private static final int DESCRIBED_PUSHES = 100;

    private final GithubAPIClient client;

    @Override
    public String name() {
        return "github";
    }

    @Override
    public List<LogEventDto> fetch() {
        /* GitHub отдаёт события не строго по времени, а пуши нужно описать
           самые свежие — поэтому сначала сортируем. */
        List<JsonNode> events = new ArrayList<>();
        client.getPublicEvents().forEach(events::add);
        events.sort(Comparator.comparing((JsonNode e) -> e.path("created_at").asText()).reversed());

        List<Push> pushes = events.stream()
                .filter(e -> "PushEvent".equals(e.path("type").asText()))
                .limit(DESCRIBED_PUSHES)
                .map(e -> new Push(repo(e), e.path("payload").path("head").asText(),
                        e.path("payload").path("before").asText()))
                .toList();
        Map<String, PushInfo> described;
        try {
            described = client.describePushes(pushes);
        } catch (RuntimeException e) {
            /* Без подробностей пуш всё равно пуш: строка останется,
               только без сообщения коммита. */
            log.warn("LOG: не описал пуши: {}", e.getMessage());
            described = Map.of();
        }

        List<LogEventDto> rows = new ArrayList<>();
        for (JsonNode event : events) {
            LogEventDto row = switch (event.path("type").asText()) {
                case "PushEvent" -> push(event, described);
                case "WatchEvent" -> simple(event, "star", GITHUB + repo(event));
                case "ForkEvent" -> simple(event, "fork",
                        GITHUB + event.path("payload").path("forkee").path("full_name").asText(repo(event)));
                case "CreateEvent" -> created(event);
                case "PullRequestEvent" -> pullRequest(event);
                case "IssuesEvent" -> issue(event);
                case "ReleaseEvent" -> release(event);
                /* Удаление веток, комментарии и прочая служебщина в ленту
                   не идут: это шум, а не то, что я сделал. */
                default -> null;
            };
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    private LogEventDto push(JsonNode event, Map<String, PushInfo> described) {
        JsonNode payload = event.path("payload");
        String head = payload.path("head").asText();
        String before = payload.path("before").asText();
        String branch = payload.path("ref").asText().replaceFirst("^refs/heads/", "");
        PushInfo info = described.get(head);

        String subject = repo(event);
        /* Ветку пишем, только если пуш мимо главной: иначе она была бы
           в каждой строке и ничего бы не говорила. */
        if (info != null && info.defaultBranch() != null && !branch.equals(info.defaultBranch())) {
            subject += " · " + branch;
        }
        Integer count = info == null ? null : info.commits();
        String url = count != null && count > 1
                ? GITHUB + repo(event) + "/compare/" + shortSha(before) + "..." + shortSha(head)
                : GITHUB + repo(event) + "/commit/" + head;

        return LogEventDto.builder()
                .source("github")
                .kind("push")
                .at(at(event))
                .subject(subject)
                .count(count)
                .detail(info == null ? null : info.headline())
                .url(url)
                .build();
    }

    /* Только новый репозиторий: ветки и теги появляются по десятку в день
       и ленту бы засорили. */
    private LogEventDto created(JsonNode event) {
        if (!"repository".equals(event.path("payload").path("ref_type").asText())) {
            return null;
        }
        LogEventDto row = simple(event, "repo", GITHUB + repo(event));
        row.setDetail(text(event.path("payload").path("description")));
        return row;
    }

    private LogEventDto pullRequest(JsonNode event) {
        JsonNode payload = event.path("payload");
        JsonNode pr = payload.path("pull_request");
        String action = payload.path("action").asText();
        String kind = "opened".equals(action) ? "pr"
                : "closed".equals(action) && pr.path("merged").asBoolean() ? "merge"
                : null;
        if (kind == null) {
            return null;
        }
        String url = text(pr.path("html_url"));
        LogEventDto row = simple(event, kind, url != null ? url
                : GITHUB + repo(event) + "/pull/" + payload.path("number").asText());
        row.setDetail(text(pr.path("title")));
        return row;
    }

    private LogEventDto issue(JsonNode event) {
        JsonNode payload = event.path("payload");
        if (!"opened".equals(payload.path("action").asText())) {
            return null;
        }
        String url = text(payload.path("issue").path("html_url"));
        LogEventDto row = simple(event, "issue", url != null ? url : GITHUB + repo(event) + "/issues");
        row.setDetail(text(payload.path("issue").path("title")));
        return row;
    }

    private LogEventDto release(JsonNode event) {
        JsonNode release = event.path("payload").path("release");
        if (!"published".equals(event.path("payload").path("action").asText())) {
            return null;
        }
        String url = text(release.path("html_url"));
        LogEventDto row = simple(event, "release", url != null ? url : GITHUB + repo(event) + "/releases");
        row.setDetail(text(release.path("tag_name")));
        return row;
    }

    private LogEventDto simple(JsonNode event, String kind, String url) {
        return LogEventDto.builder()
                .source("github")
                .kind(kind)
                .at(at(event))
                .subject(repo(event))
                .url(url)
                .build();
    }

    private static String repo(JsonNode event) {
        return event.path("repo").path("name").asText();
    }

    private static Instant at(JsonNode event) {
        return Instant.parse(event.path("created_at").asText());
    }

    private static String shortSha(String sha) {
        return sha.length() > 12 ? sha.substring(0, 12) : sha;
    }

    private static String text(JsonNode node) {
        String value = node.asText(null);
        return value == null || value.isBlank() || node.isNull() ? null : value;
    }
}
