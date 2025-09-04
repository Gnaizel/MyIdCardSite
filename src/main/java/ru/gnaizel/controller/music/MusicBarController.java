package ru.gnaizel.controller.music;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.gnaizel.dto.music.TrackDto;
import ru.gnaizel.service.music.MusicBarService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MusicBarController {
    private final MusicBarService musicBarService;

    @GetMapping("/tracks")
    public List<TrackDto> getRecentTracks() {
        return musicBarService.getRecentTracks();
    }
}
