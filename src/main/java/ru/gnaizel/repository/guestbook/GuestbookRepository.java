package ru.gnaizel.repository.guestbook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.gnaizel.model.guestbook.Message;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface GuestbookRepository extends JpaRepository<Message, Long> {
    List<Message> findTop100ByOrderByCreatedAtDesc();

    long countByIpAndCreatedAtAfter(String ip, Instant after);

    Optional<Message> findFirstByIpOrderByCreatedAtDesc(String ip);

    /* Лимиты по ip спасают от одного назойливого гостя, но не от рассылки
       с сотни адресов сразу — для неё считаем всё, что пришло за час. */
    long countByCreatedAtAfter(Instant after);

    /* Один и тот же текст с разных адресов — почерк рассылки, а не совпадение. */
    boolean existsByBodyIgnoreCaseAndCreatedAtAfter(String body, Instant after);
}
