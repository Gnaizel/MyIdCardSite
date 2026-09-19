package ru.gnaizel.service.github.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.gnaizel.dto.github.LineStatsDto;
import ru.gnaizel.exception.GithubApiResponseException;
import ru.gnaizel.model.github.ContributionsCollection;
import ru.gnaizel.model.github.ContributorStats;
import ru.gnaizel.model.github.ContributorWeek;
import ru.gnaizel.model.github.GithubGraphQlResponse;
import ru.gnaizel.model.github.GithubRepoRef;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
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

    /* Репозиториев у человека десятки, и /stats/contributors считается на
       стороне GitHub. Больше сотни за один проход не берём: остальное всё
       равно не попадёт в годовое окно. */
    private static final int MAX_REPOS = 100;

    private final RestTemplate template = new RestTemplate();
    private final ObjectMapper json = new ObjectMapper();

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
        List<GithubRepoRef> repos = listRepos();
        if (repos.isEmpty()) {
            return new LineStatsDto(0, 0, false, false);
        }

        long since = Instant.now().minus(365, ChronoUnit.DAYS).getEpochSecond();
        long added = 0;
        long removed = 0;
        boolean any = false;
        boolean pending = false;

        for (GithubRepoRef repo : repos) {
            if (repo.isFork() || repo.getFullName() == null) {
                continue;
            }
            Stats stats = contributorStats(repo.getFullName());
            pending |= stats.pending();
            for (ContributorStats stat : stats.contributors()) {
                if (stat.getAuthor() == null || !login.equalsIgnoreCase(stat.getAuthor().getLogin())) {
                    continue;
                }
                if (stat.getWeeks() == null) {
                    continue;
                }
                for (ContributorWeek week : stat.getWeeks()) {
                    if (week.getW() >= since) {
                        added += week.getA();
                        removed += week.getD();
                        any = true;
                    }
                }
            }
        }

        return new LineStatsDto(added, removed, any, pending);
    }

    /* Три исхода вместо двух: есть данные, GitHub ещё считает, запрос не удался.
       Раньше последние два были неразличимы, и «ещё считает» оседало в кэше
       на сутки как готовый ответ. */
    private record Stats(List<ContributorStats> contributors, boolean pending) {
        static Stats none() {
            return new Stats(List.of(), false);
        }

        static Stats notReady() {
            return new Stats(List.of(), true);
        }
    }

    private List<GithubRepoRef> listRepos() {
        String url = REST_URL + "/user/repos?per_page=" + MAX_REPOS
                + "&affiliation=owner&sort=pushed";
        try {
            GithubRepoRef[] repos = template.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers()), GithubRepoRef[].class).getBody();
            return repos == null ? List.of() : Arrays.asList(repos);
        } catch (RestClientException e) {
            log.error("GITHUB API ERROR (repos): " + e.getMessage());
            return List.of();
        }
    }

    /* Ответ берём строкой и разбираем сами: на 202 GitHub отдаёт не массив,
       а пустой объект, и разбор сразу в ContributorStats[] падал на нём
       исключением. Со стороны это выглядело как сбой, хотя на деле GitHub
       просто ещё не досчитал — и репозиторий молча выпадал из суммы. */
    private Stats contributorStats(String fullName) {
        String url = REST_URL + "/repos/" + fullName + "/stats/contributors";
        try {
            ResponseEntity<String> response = template.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers()), String.class);
            if (response.getStatusCode() == HttpStatus.ACCEPTED) {
                log.info("GITHUB: статистика по {} ещё считается на их стороне", fullName);
                return Stats.notReady();
            }
            String body = response.getBody();
            if (body == null || body.isBlank() || !body.trim().startsWith("[")) {
                return Stats.notReady();
            }
            return new Stats(Arrays.asList(json.readValue(body, ContributorStats[].class)), false);
        } catch (RestClientException | JsonProcessingException e) {
            log.warn("GITHUB API WARN (stats {}): {}", fullName, e.getMessage());
            return Stats.none();
        }
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        return headers;
    }
}
