package ru.gnaizel.exception;

public class IpNotRecognized extends RuntimeException {
    public IpNotRecognized(String message) {
        super(message);
    }
}
