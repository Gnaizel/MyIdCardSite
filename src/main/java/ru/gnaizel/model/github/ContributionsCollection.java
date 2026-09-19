package ru.gnaizel.model.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContributionsCollection {
    private int totalCommitContributions;
    private int totalPullRequestContributions;
    private int totalIssueContributions;
    private int totalRepositoriesWithContributedCommits;
    private int restrictedContributionsCount;
    private ContributionCalendar contributionCalendar;
}
