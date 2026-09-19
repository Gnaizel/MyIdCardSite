package ru.gnaizel.dto.github;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Всё, что отдаётся наружу по /github.
 * Здесь принципиально одни числа: ни имён репозиториев, ни сообщений коммитов,
 * ни sha. Репозитории приватные, и наружу из них уходит только статистика.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class GithubActivityDto {
    private int totalContributions;
    private int commits;
    private int pullRequests;
    private int issues;
    private int repositories;
    private int currentStreak;
    private int bestStreak;
    private int busiestDay;
    private long linesAdded;
    private long linesRemoved;
    private boolean linesAvailable;
    private List<ContributionDayDto> days;
}
