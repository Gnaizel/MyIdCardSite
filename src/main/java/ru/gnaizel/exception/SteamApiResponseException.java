package ru.gnaizel.exception;

public class SteamApiResponseException extends RuntimeException {
    public SteamApiResponseException(String message) {
        super(message);
    }
}
