package ru.gnaizel.controller.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.gnaizel.dto.presence.PresenceDto;
import ru.gnaizel.service.presence.PresenceService;

@RestController
@RequiredArgsConstructor
public class PresenceController {
    private final PresenceService presenceService;

    /**
     * Во что играю прямо сейчас, если играю.
     * <p>
     * 204, а не пустой объект: «не играю» — это отсутствие ответа, а не
     * ответ с пустыми полями, и странице так не нужно разбирать, что значит
     * game без значения.
     */
    @GetMapping("/presence")
    public ResponseEntity<PresenceDto> nowPlaying() {
        return presenceService.nowPlaying()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
