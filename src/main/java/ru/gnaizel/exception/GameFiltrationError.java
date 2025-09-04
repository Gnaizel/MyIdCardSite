package ru.gnaizel.exception;

public class GameFiltrationError extends RuntimeException {
    public GameFiltrationError(String message) {
        super(message);
    }
}
