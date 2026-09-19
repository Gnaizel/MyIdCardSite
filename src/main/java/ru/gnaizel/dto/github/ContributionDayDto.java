package ru.gnaizel.dto.github;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ContributionDayDto {
    private String date;  // yyyy-MM-dd
    private int count;
    private int level;    // 0..4, как красит сам GitHub
}
