package ru.gnaizel.service.github.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
            return new LineStatsDto(0, 0, false);
        }

        long since = Instant.now().minus(365, ChronoUnit.DAYS).getEpochSecond();
        long added = 0;
        long removed = 0;
        boolean any = false;

        for (GithubRepoRef repo : repos) {
            if (repo.isFork() || repo.getFullName() == null) {
                continue;
            }
            List<ContributorStats> stats = contributorStats(repo.getFullName());
            if (stats == null) {
                continue;  // 202: GitHub ещё считает — возьмём в следующий раз
            }
            for (ContributorStats stat : stats) {
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

        return new LineStatsDto(added, removed, any);
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

    private List<ContributorStats> contributorStats(String fullName) {
        String url = REST_URL + "/repos/" + fullName + "/stats/contributors";
        try {
            ResponseEntity<ContributorStats[]> response = template.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers()), ContributorStats[].class);
            ContributorStats[] body = response.getBody();
            return body == null ? null : Arrays.asList(body);
        } catch (RestClientException e) {
            log.warn("GITHUB API WARN (stats): " + e.getMessage());
            return null;
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
