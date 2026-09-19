package ru.gnaizel.service.github.client;

import ru.gnaizel.dto.github.LineStatsDto;
import ru.gnaizel.model.github.ContributionsCollection;

public interface GithubAPIClient {
    ContributionsCollection getContributions();

    LineStatsDto getLineStats();
}
