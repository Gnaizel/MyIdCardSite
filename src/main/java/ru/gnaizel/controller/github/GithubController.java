package ru.gnaizel.controller.github;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.gnaizel.dto.github.GithubActivityDto;
import ru.gnaizel.service.github.GithubService;

@RestController
@RequiredArgsConstructor
public class GithubController {
    private final GithubService githubService;

    @GetMapping("/github")
    public GithubActivityDto getActivity() {
        return githubService.getActivity();
    }
}
