package ru.gnaizel.dto.guestbook;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Вид для владельца: здесь ip и статус видны, отдаётся только по ключу. */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class MessageAdminDto {
    private Long id;
    private String nick;
    private String body;
    private String ip;
    private Instant createdAt;
    private boolean hidden;
}
