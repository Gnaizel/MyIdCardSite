package ru.gnaizel.controller.time;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.gnaizel.service.time.TimeService;

@RestController
@RequiredArgsConstructor
public class TimeController {
    private final TimeService timeService;

    @GetMapping("/time")
    public String getCurrentTime() {
        return timeService.getCurrentTime();
    }
}
