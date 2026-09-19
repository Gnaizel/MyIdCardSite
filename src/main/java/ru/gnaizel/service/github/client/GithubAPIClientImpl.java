package ru.gnaizel.service.github.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        return headers;
    }
}
