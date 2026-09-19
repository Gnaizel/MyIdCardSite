package ru.gnaizel.model.guestbook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Сообщение из гостевой книги.
 * ip хранится только ради лимитов и разбора полётов — наружу он не отдаётся
 * ни одним публичным эндпоинтом.
 */
@Data
@Table(name = "guestbook")
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 24)
    private String nick;

    @Column(nullable = false, length = 280)
    private String body;

    @Column(nullable = false, length = 120)
    private String ip;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /* Постмодерация: сообщение видно сразу, а удаление прячет его, а не стирает
       из базы. Так можно посмотреть, что именно приходило, и вернуть обратно. */
    @Column(nullable = false)
    private boolean hidden;
}
