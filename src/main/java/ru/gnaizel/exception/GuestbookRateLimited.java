package ru.gnaizel.exception;

/** С этого адреса пишут слишком часто. */
public class GuestbookRateLimited extends RuntimeException {
    public GuestbookRateLimited(String message) {
        super(message);
    }
}
