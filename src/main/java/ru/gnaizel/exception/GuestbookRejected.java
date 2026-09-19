package ru.gnaizel.exception;

/** Сообщение не прошло проверку: пустое, длинное или со ссылкой. */
public class GuestbookRejected extends RuntimeException {
    public GuestbookRejected(String message) {
        super(message);
    }
}
