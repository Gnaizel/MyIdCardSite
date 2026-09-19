package ru.gnaizel.controller.guestbook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.gnaizel.exception.AdminKeyRequired;
import ru.gnaizel.exception.GuestbookRateLimited;
import ru.gnaizel.exception.GuestbookRejected;

import java.util.Map;

/**
 * Ловит только исключения гостевой книги: остальные эндпоинты как падали
 * пятисоткой, так и падают — их поведение этот класс не меняет.
 */
@Slf4j
@RestControllerAdvice
public class GuestbookExceptionHandler {

    @ExceptionHandler(GuestbookRejected.class)
    public ResponseEntity<Map<String, String>> onRejected(GuestbookRejected e) {
        return ResponseEntity.unprocessableEntity().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(GuestbookRateLimited.class)
    public ResponseEntity<Map<String, String>> onRateLimited(GuestbookRateLimited e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(AdminKeyRequired.class)
    public ResponseEntity<Map<String, String>> onAdminKey(AdminKeyRequired e) {
        log.warn("GUESTBOOK ADMIN: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "unauthorized"));
    }
}
