package ru.gnaizel.mapper.github;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.gnaizel.dto.github.ContributionDayDto;
import ru.gnaizel.dto.github.GithubActivityDto;
import ru.gnaizel.dto.github.LineStatsDto;
import ru.gnaizel.model.github.ContributionDay;
import ru.gnaizel.model.github.ContributionWeek;
import ru.gnaizel.model.github.ContributionsCollection;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class GithubMapper {

    public GithubActivityDto mapToDto(ContributionsCollection collection, LineStatsDto lines) {
        List<ContributionDayDto> days = flattenDays(collection);

        return GithubActivityDto.builder()
                .totalContributions(collection.getContributionCalendar() == null
                        ? 0 : collection.getContributionCalendar().getTotalContributions())
                .commits(collection.getTotalCommitContributions())
                .pullRequests(collection.getTotalPullRequestContributions())
                .issues(collection.getTotalIssueContributions())
                .repositories(collection.getTotalRepositoriesWithContributedCommits())
                .currentStreak(currentStreak(days))
                .bestStreak(bestStreak(days))
                .busiestDay(busiestDay(days))
                .linesAdded(lines.getAdded())
                .linesRemoved(lines.getRemoved())
                .linesAvailable(lines.isAvailable())
                .days(days)
                .build();
    }

    private List<ContributionDayDto> flattenDays(ContributionsCollection collection) {
        List<ContributionDayDto> days = new ArrayList<>();
        if (collection.getContributionCalendar() == null
                || collection.getContributionCalendar().getWeeks() == null) {
            return days;
        }
        for (ContributionWeek week : collection.getContributionCalendar().getWeeks()) {
            if (week.getContributionDays() == null) {
                continue;
            }
            for (ContributionDay day : week.getContributionDays()) {
                days.add(new ContributionDayDto(day.getDate(),
                        day.getContributionCount(),
                        level(day.getContributionLevel())));
            }
        }
        return days;
    }

    /** GitHub отдаёт квартиль строкой — на фронте удобнее число 0..4. */
    private int level(String contributionLevel) {
        if (contributionLevel == null) {
            return 0;
        }
        return switch (contributionLevel) {
            case "FIRST_QUARTILE" -> 1;
            case "SECOND_QUARTILE" -> 2;
            case "THIRD_QUARTILE" -> 3;
            case "FOURTH_QUARTILE" -> 4;
            default -> 0;
        };
    }

    /* Календарь заканчивается сегодняшним днём, а он ещё может быть пустым —
       поэтому пустой последний день стрик не обрывает, а пропускается. */
    private int currentStreak(List<ContributionDayDto> days) {
        int streak = 0;
        for (int i = days.size() - 1; i >= 0; i--) {
            if (days.get(i).getCount() > 0) {
                streak++;
            } else if (i == days.size() - 1) {
                continue;
            } else {
                break;
            }
        }
        return streak;
    }

    private int bestStreak(List<ContributionDayDto> days) {
        int best = 0;
        int run = 0;
        for (ContributionDayDto day : days) {
            if (day.getCount() > 0) {
                run++;
                best = Math.max(best, run);
            } else {
                run = 0;
            }
        }
        return best;
    }

    private int busiestDay(List<ContributionDayDto> days) {
        return days.stream().mapToInt(ContributionDayDto::getCount).max().orElse(0);
    }
}
