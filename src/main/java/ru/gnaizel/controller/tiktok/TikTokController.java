package ru.gnaizel.controller.tiktok;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.gnaizel.dto.tiktok.TikTokVideoDto;
import ru.gnaizel.service.tiktok.TikTokService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TikTokController {
    private final TikTokService tikTokService;

    @GetMapping("/tiktok")
    public List<TikTokVideoDto> getReposts() {
        return tikTokService.getReposts();
    }
}
