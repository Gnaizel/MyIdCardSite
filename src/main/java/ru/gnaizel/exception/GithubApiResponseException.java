package ru.gnaizel.exception;

public class GithubApiResponseException extends RuntimeException {
    public GithubApiResponseException(String message) {
        super(message);
    }
}
