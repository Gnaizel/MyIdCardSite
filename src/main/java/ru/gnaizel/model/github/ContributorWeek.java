package ru.gnaizel.model.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** Недельная корзина из /stats/contributors: w — начало недели, a/d — строки. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContributorWeek {
    private long w;
    private long a;
    private long d;
    private long c;
}
