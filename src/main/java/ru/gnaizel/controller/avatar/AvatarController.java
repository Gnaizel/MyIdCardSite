package ru.gnaizel.controller.avatar;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import ru.gnaizel.service.avatar.AvatarService;

import java.time.Duration;

/**
 * Аватарка отдаётся отдельной ручкой, а не как обычный файл из static:
 * её можно сменить из бота, а всё, что лежит в static, замуровано в jar.
 */
@RestController
@RequiredArgsConstructor
public class AvatarController {
    private final AvatarService avatarService;

    @GetMapping("/image/avatar")
    public ResponseEntity<byte[]> avatar(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String knownEtag) {
        AvatarService.Avatar avatar = avatarService.current();

        /* Пять минут — компромисс: страницу открывают чаще, чем меняют фото,
           но после замены ждать обновления сутки никто не станет. Дальше
           работает ETag: браузер спрашивает, а в ответ получает пустой 304. */
        CacheControl cache = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

        if (avatar.etag().equals(knownEtag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(avatar.etag())
                    .cacheControl(cache)
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .eTag(avatar.etag())
                .cacheControl(cache)
                .body(avatar.bytes());
    }
}
