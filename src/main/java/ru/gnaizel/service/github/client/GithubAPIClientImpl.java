package ru.gnaizel.service.github.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.gnaizel.dto.github.LineStatsDto;
import ru.gnaizel.exception.GithubApiResponseException;
import ru.gnaizel.model.github.ContributionsCollection;
import ru.gnaizel.model.github.GithubGraphQlResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GithubAPIClientImpl implements GithubAPIClient {
    private static final String GRAPHQL_URL = "https://api.github.com/graphql";
    private static final String REST_URL = "https://api.github.com";

    /* viewer, а не user(login:) — только так в календарь попадают приватные
       репозитории. Токен наш, значит GitHub отдаёт по ним счётчики; имён и
       содержимого коммитов этот запрос не запрашивает вообще. */
    private static final String CONTRIBUTIONS_QUERY = """
            query {
              viewer {
                login
                contributionsCollection {
                  totalCommitContributions
                  totalPullRequestContributions
                  totalIssueContributions
                  totalRepositoriesWithContributedCommits
                  restrictedContributionsCount
                  contributionCalendar {
                    totalContributions
                    weeks {
                      contributionDays {
                        date
                        contributionCount
                        contributionLevel
                      }
                    }
                  }
                }
              }
            }""";

    /* Строки берём из истории коммитов, а не из /stats/contributors.
       Тот эндпоинт GitHub считает в фоне и до готовности отвечает 202 —
       на живом репозитории он отвечал так часами, и репозиторий просто
       выпадал из суммы. Здесь же additions и deletions лежат в самом
       коммите: ответ приходит сразу и всегда актуальный.

       Форки пропускаем: чужая история раздула бы счётчик. */
    private static final String LINES_QUERY = """
            query($since: GitTimestamp!) {
              viewer {
                login
                repositories(first: 100, ownerAffiliations: OWNER, isFork: false,
                             orderBy: {field: PUSHED_AT, direction: DESC}) {
                  nodes {
                    nameWithOwner
                    defaultBranchRef {
                      target {
                        ... on Commit {
                          history(since: $since, first: 100) {
                            pageInfo { hasNextPage endCursor }
                            nodes {
                              additions
                              deletions
                              author { user { login } }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }""";

    /* Догрузка истории одного репозитория, когда сотни коммитов не хватило. */
    private static final String LINES_PAGE_QUERY = """
            query($owner: String!, $name: String!, $since: GitTimestamp!, $after: String!) {
              repository(owner: $owner, name: $name) {
                defaultBranchRef {
                  target {
                    ... on Commit {
                      history(since: $since, first: 100, after: $after) {
                        pageInfo { hasNextPage endCursor }
                        nodes {
                          additions
                          deletions
                          author { user { login } }
                        }
                      }
                    }
                  }
                }
              }
            }""";

    /* Одна страница — последние сто событий. Старше тридцати дней GitHub
       их всё равно не отдаёт, а на странице столько и не читают. */
    private static final String EVENTS_URL = REST_URL + "/users/%s/events/public?per_page=100";

    /* Как глубоко от головы пуша искать его начало. Пуш длиннее — редкость,
       и тогда число коммитов просто не пишем. */
    private static final int PUSH_DEPTH = 50;

    private final RestTemplate template = new RestTemplate();

    @Value("${github.api-token}")
    private String token;

    @Value("${github.login}")
    private String login;

    @Override
    public ContributionsCollection getContributions() {
        HttpEntity<Map<String, String>> request =
                new HttpEntity<>(Map.of("query", CONTRIBUTIONS_QUERY), headers());

        GithubGraphQlResponse response;
        try {
            response = template.postForObject(GRAPHQL_URL, request, GithubGraphQlResponse.class);
        } catch (RestClientException e) {
            throw new GithubApiResponseException("GITHUB API ERROR: " + e.getMessage());
        }

        if (response == null
                || response.getData() == null
                || response.getData().getViewer() == null
                || response.getData().getViewer().getContributionsCollection() == null) {
            throw new GithubApiResponseException("GITHUB API ERROR: contributions are null");
        }

        return response.getData().getViewer().getContributionsCollection();
    }

    @Override
    public LineStatsDto getLineStats() {
        String since = Instant.now().minus(365, ChronoUnit.DAYS).toString();
        JsonNode viewer = graphQl(LINES_QUERY, Map.of("since", since)).path("viewer");
        String me = viewer.path("login").asText();

        long[] totals = new long[3];
        for (JsonNode repo : viewer.path("repositories").path("nodes")) {
            JsonNode history = repo.path("defaultBranchRef").path("target").path("history");
            boolean more = sumHistory(history, me, totals);

            /* Сотня коммитов за год в одном репозитории — редкость, но если
               она случилась, дочитываем остальное постранично: иначе разница
               молча потерялась бы, а именно из-за таких потерь этот счётчик
               и переписывался. */
            String cursor = history.path("pageInfo").path("endCursor").asText(null);
            String[] parts = repo.path("nameWithOwner").asText().split("/", 2);
            while (more && cursor != null && parts.length == 2) {
                JsonNode page = graphQl(LINES_PAGE_QUERY, Map.of(
                                "owner", parts[0], "name", parts[1], "since", since, "after", cursor))
                        .path("repository").path("defaultBranchRef").path("target").path("history");
                more = sumHistory(page, me, totals);
                cursor = page.path("pageInfo").path("endCursor").asText(null);
            }
        }

        return new LineStatsDto(totals[0], totals[1], totals[2] > 0);
    }

    @Override
    public JsonNode getPublicEvents() {
        JsonNode events;
        try {
            events = template.exchange(EVENTS_URL.formatted(login), HttpMethod.GET,
                    new HttpEntity<>(headers()), JsonNode.class).getBody();
        } catch (RestClientException e) {
            throw new GithubApiResponseException("GITHUB API ERROR: " + e.getMessage());
        }
        if (events == null || !events.isArray()) {
            throw new GithubApiResponseException("GITHUB API ERROR: events are not a list");
        }
        return events;
    }

    /* Все пуши — одним запросом: на каждый свой псевдоним p0, p1… с головой
       пуша и полусотней коммитов под ней. Позиция прежней головы в этом
       списке и есть число коммитов в пуше. Значения идут переменными, а не
       вклеиваются в текст запроса. */
    @Override
    public Map<String, PushInfo> describePushes(List<Push> pushes) {
        if (pushes.isEmpty() || token == null || token.isBlank()) {
            return Map.of();
        }

        StringBuilder params = new StringBuilder();
        StringBuilder fields = new StringBuilder();
        Map<String, Object> variables = new HashMap<>();
        List<Push> asked = new ArrayList<>();
        for (Push push : pushes) {
            String[] parts = push.repo().split("/", 2);
            if (parts.length != 2) {
                continue;
            }
            int n = asked.size();
            asked.add(push);
            params.append("$o%1$d: String!, $n%1$d: String!, $h%1$d: GitObjectID!, ".formatted(n));
            fields.append("""
                      p%1$d: repository(owner: $o%1$d, name: $n%1$d) {
                        defaultBranchRef { name }
                        object(oid: $h%1$d) {
                          ... on Commit { messageHeadline history(first: %2$d) { nodes { oid } } }
                        }
                      }
                    """.formatted(n, PUSH_DEPTH));
            variables.put("o" + n, parts[0]);
            variables.put("n" + n, parts[1]);
            variables.put("h" + n, push.head());
        }
        if (asked.isEmpty()) {
            return Map.of();
        }

        JsonNode data = graphQlPartial("query(" + params + ") {\n" + fields + "}", variables);
        Map<String, PushInfo> described = new HashMap<>();
        for (int n = 0; n < asked.size(); n++) {
            JsonNode repo = data.path("p" + n);
            JsonNode commit = repo.path("object");
            if (!commit.hasNonNull("messageHeadline")) {
                continue;
            }
            List<String> history = new ArrayList<>();
            commit.path("history").path("nodes").forEach(node -> history.add(node.path("oid").asText()));
            int at = history.indexOf(asked.get(n).before());
            described.put(asked.get(n).head(), new PushInfo(
                    commit.path("messageHeadline").asText(),
                    at > 0 ? at : null,
                    repo.path("defaultBranchRef").path("name").asText(null)));
        }
        return described;
    }

    /* Как graphQl, но частичный ответ не считается провалом: удалённый или
       переименованный репозиторий даёт ошибку только своему псевдониму,
       и терять из-за неё остальные пуши незачем. */
    private JsonNode graphQlPartial(String query, Map<String, Object> variables) {
        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(Map.of("query", query, "variables", variables), headers());
        JsonNode response;
        try {
            response = template.postForObject(GRAPHQL_URL, request, JsonNode.class);
        } catch (RestClientException e) {
            throw new GithubApiResponseException("GITHUB API ERROR: " + e.getMessage());
        }
        if (response == null || !response.path("data").isObject()) {
            throw new GithubApiResponseException("GITHUB API ERROR: "
                    + (response == null ? "empty response" : response.path("errors").toString()));
        }
        if (response.has("errors")) {
            log.warn("GITHUB: часть пушей не описана: {}", response.path("errors"));
        }
        return response.path("data");
    }

    /* Складываем только свои коммиты: в истории лежат и чужие, если в репозиторий
       кто-то присылал изменения. Возвращаем признак того, что страница не последняя. */
    private boolean sumHistory(JsonNode history, String me, long[] totals) {
        for (JsonNode commit : history.path("nodes")) {
            if (!me.equalsIgnoreCase(commit.path("author").path("user").path("login").asText())) {
                continue;
            }
            totals[0] += commit.path("additions").asLong();
            totals[1] += commit.path("deletions").asLong();
            totals[2] = 1;
        }
        return history.path("pageInfo").path("hasNextPage").asBoolean();
    }

    /* Ответ разбираем в JsonNode, а не в модели: из этого запроса нужны четыре
       поля, а типизировать пришлось бы всю вложенность из шести уровней. */
    private JsonNode graphQl(String query, Map<String, Object> variables) {
        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(Map.of("query", query, "variables", variables), headers());
        JsonNode response;
        try {
            response = template.postForObject(GRAPHQL_URL, request, JsonNode.class);
        } catch (RestClientException e) {
            throw new GithubApiResponseException("GITHUB API ERROR: " + e.getMessage());
        }
        if (response == null || response.has("errors")) {
            throw new GithubApiResponseException("GITHUB API ERROR: "
                    + (response == null ? "empty response" : response.path("errors").toString()));
        }
        return response.path("data");
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        /* Без токена публичные события GitHub всё равно отдаёт, только
           с лимитом поменьше; пустой заголовок он же счёл бы ошибкой. */
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        return headers;
    }
}
