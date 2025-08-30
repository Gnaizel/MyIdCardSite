package ru.gnaizel.controller.user;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.gnaizel.service.user.UserService;

@RequiredArgsConstructor
@RestController
public class UserController {
    public final UserService service;

    @GetMapping("/visitor")
    public Long getCountVisitor() {
        return service.getCountOfVisit();
    }
}
