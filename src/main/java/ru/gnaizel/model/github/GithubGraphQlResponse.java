package ru.gnaizel.model.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubGraphQlResponse {
    private GithubViewerData data;
}
