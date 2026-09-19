package ru.gnaizel.dto.guestbook;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Публичный вид сообщения. ip сюда не попадает намеренно. */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class MessageDto {
    private Long id;
    private String nick;
    private String body;
    private String ago;
}
