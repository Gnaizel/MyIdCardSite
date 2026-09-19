package ru.gnaizel.model.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContributionDay {
    private String date;
    private int contributionCount;
    private String contributionLevel;  // NONE | FIRST_QUARTILE | ... | FOURTH_QUARTILE
}
