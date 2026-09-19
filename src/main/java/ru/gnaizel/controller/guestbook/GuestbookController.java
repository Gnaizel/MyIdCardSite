package ru.gnaizel.controller.guestbook;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.gnaizel.dto.guestbook.MessageAdminDto;
import ru.gnaizel.dto.guestbook.MessageCreateDto;
import ru.gnaizel.dto.guestbook.MessageDto;
import ru.gnaizel.service.guestbook.GuestbookService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class GuestbookController {
    private static final String ADMIN_HEADER = "X-Admin-Key";

    private final GuestbookService guestbookService;

    /* X-Forwarded-For можно подделать заголовком, а значит и обойти лимиты,
       поэтому по умолчанию доверяем только адресу соединения. Включать флаг
       имеет смысл, только если перед приложением реально стоит прокси. */
    @Value("${guestbook.trust-forwarded-for:false}")
    private boolean trustForwardedFor;

    /* Публичной выдачи сообщений нет и не должно быть: их видит только
       владелец через /guestbook/all по ключу. */
    @PostMapping("/guestbook")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto addMessage(@RequestBody MessageCreateDto dto, HttpServletRequest request) {
        return guestbookService.add(dto, clientIp(request));
    }

    @GetMapping("/guestbook/all")
    public List<MessageAdminDto> getAll(@RequestHeader(value = ADMIN_HEADER, required = false) String key) {
        return guestbookService.getAll(key);
    }

    @DeleteMapping("/guestbook/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hide(@PathVariable Long id,
                     @RequestHeader(value = ADMIN_HEADER, required = false) String key) {
        guestbookService.hide(id, key);
    }

    @PostMapping("/guestbook/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable Long id,
                        @RequestHeader(value = ADMIN_HEADER, required = false) String key) {
        guestbookService.restore(id, key);
    }

    private String clientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
