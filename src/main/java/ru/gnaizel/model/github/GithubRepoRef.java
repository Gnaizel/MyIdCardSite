package ru.gnaizel.model.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** Из списка репозиториев берём ровно столько, сколько нужно для /stats. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubRepoRef {
    @JsonProperty("full_name")
    private String fullName;

    private boolean fork;
}
