package ru.gnaizel.service.github;

import ru.gnaizel.dto.github.GithubActivityDto;

public interface GithubService {
    GithubActivityDto getActivity();
}
