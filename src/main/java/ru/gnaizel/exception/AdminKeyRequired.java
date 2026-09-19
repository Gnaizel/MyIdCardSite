package ru.gnaizel.exception;

/** Админский эндпоинт дёрнули без ключа или с неверным. */
public class AdminKeyRequired extends RuntimeException {
    public AdminKeyRequired(String message) {
        super(message);
    }
}
