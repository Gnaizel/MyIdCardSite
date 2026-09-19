package ru.gnaizel.model.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContributionCalendar {
    private int totalContributions;
    private List<ContributionWeek> weeks;
}
