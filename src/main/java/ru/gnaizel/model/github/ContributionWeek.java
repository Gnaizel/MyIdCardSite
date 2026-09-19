package ru.gnaizel.model.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContributionWeek {
    private List<ContributionDay> contributionDays;
}
