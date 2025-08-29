package ru.gnaizel.controller.time;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.gnaizel.service.time.DynamicValueService;

@Slf4j
@RestController
@RequiredArgsConstructor
public class DynamicValueController {
    private final DynamicValueService timeService;

    @GetMapping("/time")
    public String getCurrentTime() {
        return timeService.getCurrentTime();
    }

    @GetMapping("/old")
    public String getAlive() {
        return timeService.getAlive();
    }
}
