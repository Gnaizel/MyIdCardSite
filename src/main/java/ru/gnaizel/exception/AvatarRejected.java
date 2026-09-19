package ru.gnaizel.exception;

/** Присланное боту фото не годится в аватарку: не картинка, битое или слишком большое. */
public class AvatarRejected extends RuntimeException {
    public AvatarRejected(String message) {
        super(message);
    }
}
