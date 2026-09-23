package ru.gnaizel.controller.log;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.gnaizel.dto.log.LogEventDto;
import ru.gnaizel.service.log.LogService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class LogController {
    private final LogService logService;

    @GetMapping("/log")
    public List<LogEventDto> getLog() {
        return logService.getLog();
    }
}
