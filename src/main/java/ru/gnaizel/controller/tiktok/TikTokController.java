package ru.gnaizel.controller.tiktok;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ru.gnaizel.dto.tiktok.TikTokVideoDto;
import ru.gnaizel.service.tiktok.TikTokService;
import ru.gnaizel.service.tiktok.TikTokServiceImpl;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TikTokController {
    private final TikTokService tikTokService;

    @GetMapping("/tiktok")
    public List<TikTokVideoDto> getReposts() {
        return tikTokService.getReposts();
    }

    @GetMapping("/tiktok/video/{id}")
    public ResponseEntity<StreamingResponseBody> video(
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        return proxy(tikTokService.playAddr(id), range, "видео " + id);
    }

    /** Звук фото-поста: у него нет видео, но есть своя дорожка. */
    @GetMapping("/tiktok/audio/{id}")
    public ResponseEntity<StreamingResponseBody> audio(
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        return proxy(tikTokService.musicAddr(id), range, "звук " + id);
    }

    /**
     * Медиа проксируется через нас, а не играется с адреса TikTok напрямую.
     * <p>
     * Причина не в красоте: TikTok отдаёт файл только при Referer со своего
     * домена. Теги video и audio на нашей странице пришлют наш домен
     * и получат 403 — проверено и на видео, и на звуке. Поэтому запрос
     * повторяется отсюда, с нужными заголовками.
     * <p>
     * Заодно наружу не уезжает подписанный адрес, который всё равно
     * протухает через двое суток.
     */
    private ResponseEntity<StreamingResponseBody> proxy(Optional<String> source, String range, String what) {
        /* Адрес берётся из нашего же списка по id, а не принимается снаружи:
           иначе ручка стала бы открытым прокси, которым можно ходить куда
           угодно от имени сервера. */
        if (source.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        HttpURLConnection upstream;
        int status;
        try {
            upstream = (HttpURLConnection) URI.create(source.get()).toURL().openConnection();
            upstream.setRequestProperty(HttpHeaders.USER_AGENT, TikTokServiceImpl.UA);
            upstream.setRequestProperty(HttpHeaders.REFERER, "https://www.tiktok.com/");
            if (range != null && !range.isBlank()) {
                // перемотку браузер делает диапазонами, их нужно пропустить насквозь
                upstream.setRequestProperty(HttpHeaders.RANGE, range);
            }
            upstream.setConnectTimeout(5000);
            upstream.setReadTimeout(20000);
            status = upstream.getResponseCode();
        } catch (IOException e) {
            log.warn("TIKTOK: не открыл {}: {}", what, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }

        if (status >= 400) {
            upstream.disconnect();
            log.warn("TIKTOK: {} отдано с отказом {}", what, status);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }

        HttpHeaders headers = new HttpHeaders();
        copy(upstream, headers, HttpHeaders.CONTENT_TYPE);
        copy(upstream, headers, HttpHeaders.CONTENT_LENGTH);
        copy(upstream, headers, HttpHeaders.CONTENT_RANGE);
        copy(upstream, headers, HttpHeaders.ACCEPT_RANGES);
        /* Приватный кэш: одно и то же при прокрутке туда-сюда, но на общих
           прокси ему делать нечего — ссылка всё равно протухнет. */
        headers.setCacheControl(CacheControl.maxAge(Duration.ofMinutes(30)).cachePrivate());

        StreamingResponseBody body = out -> {
            try (InputStream in = upstream.getInputStream()) {
                in.transferTo(out);
            } catch (IOException e) {
                /* Обычное дело: посетитель пролистнул дальше или закрыл
                   вкладку посреди загрузки. Это не ошибка сервера. */
                log.debug("TIKTOK: поток ({}) оборван: {}", what, e.getMessage());
            } finally {
                upstream.disconnect();
            }
        };

        return ResponseEntity.status(status).headers(headers).body(body);
    }

    private static void copy(HttpURLConnection from, HttpHeaders to, String header) {
        String value = from.getHeaderField(header);
        if (value != null) {
            to.set(header, value);
        }
    }
}
