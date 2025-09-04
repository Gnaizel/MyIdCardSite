package ru.gnaizel.exception;

public class GameMappingDtoError extends RuntimeException {
    public GameMappingDtoError(String message) {
        super(message);
    }
}
