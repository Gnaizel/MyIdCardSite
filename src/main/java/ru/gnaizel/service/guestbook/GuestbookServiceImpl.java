package ru.gnaizel.service.guestbook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.gnaizel.dto.guestbook.MessageAdminDto;
import ru.gnaizel.dto.guestbook.MessageCreateDto;
import ru.gnaizel.dto.guestbook.MessageDto;
import ru.gnaizel.exception.AdminKeyRequired;
import ru.gnaizel.exception.GuestbookRateLimited;
import ru.gnaizel.exception.GuestbookRejected;
import ru.gnaizel.mapper.guestbook.GuestbookMapper;
import ru.gnaizel.model.guestbook.Message;
import ru.gnaizel.repository.guestbook.GuestbookRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Единственное место на сайте, куда пишут снаружи, поэтому все ограничения
 * собраны здесь, а не размазаны по контроллеру.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuestbookServiceImpl implements GuestbookService {
    private static final int BODY_MAX = 280;
    private static final int NICK_MAX = 24;
    private static final Duration COOLDOWN = Duration.ofMinutes(2);
    private static final int PER_DAY = 10;

    /* Потолок на всю книгу, а не на адрес: лимиты по ip обходятся рассылкой
       с разных адресов, а живой сайт столько сообщений в час не собирает. */
    private static final int PER_HOUR_TOTAL = 60;

    /* Повтор текста ловим только на длинных сообщениях: «hi» или «привет»
       двое разных людей напишут совершенно честно, а вот абзац — уже нет. */
    private static final int DUPLICATE_MIN_LENGTH = 20;
    private static final Duration DUPLICATE_WINDOW = Duration.ofDays(1);

    /* Ссылочный спам — основное, что прилетает в открытые формы. Список доменов
       короткий и намеренно грубый: ложное срабатывание человек переживёт,
       он увидит внятный отказ, а бот не увидит ничего полезного. */
    private static final Pattern LINK = Pattern.compile(
            "(?i)(https?://|www\\.|\\b[a-z0-9-]+\\.(com|net|org|ru|io|xyz|top|shop|info"
                    + "|biz|online|site|club|link|cc|me|su|store|live|fun)\\b)");

    /* Управляющие символы и «невидимки»: ими рисуют разметку и растягивают
       строку на пол-экрана. */
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\p{Cf}&&[^\\n]]");

    private final GuestbookRepository repository;
    private final GuestbookMapper mapper;

    /* Optional, а не обязательная зависимость: пока реализации MessageNotifier
       в проекте нет, приложение спокойно поднимается и просто копит сообщения
       в базе. Появится @Service с этим интерфейсом — подхватится сам. */
    private final Optional<MessageNotifier> notifier;

    @Value("${guestbook.admin-key:}")
    private String adminKeyConfigured;

    @Override
    @Transactional
    public MessageDto add(MessageCreateDto dto, String ip) {
        // ловушку проверяем первой: боту незачем знать, что его раскусили
        if (dto.getWebsite() != null && !dto.getWebsite().isBlank()) {
            log.info("GUESTBOOK: honeypot hit from {}", ip);
            return new MessageDto(null, "anon", "", "just now");
        }

        String body = clean(dto.getBody());
        if (body.isEmpty()) {
            throw new GuestbookRejected("message is empty");
        }
        if (body.length() > BODY_MAX) {
            throw new GuestbookRejected("message is longer than " + BODY_MAX + " characters");
        }
        if (LINK.matcher(body).find()) {
            throw new GuestbookRejected("links are not allowed");
        }
        if (isDuplicate(body)) {
            log.info("GUESTBOOK: duplicate body from {}", ip);
            throw new GuestbookRejected("this has already been said");
        }

        String nick = clean(dto.getNick()).replace("\n", " ").trim();
        if (nick.length() > NICK_MAX) {
            throw new GuestbookRejected("nick is longer than " + NICK_MAX + " characters");
        }
        if (LINK.matcher(nick).find()) {
            throw new GuestbookRejected("links are not allowed");
        }

        checkRate(ip);

        Message saved = repository.save(Message.builder()
                .nick(nick.isEmpty() ? null : nick)
                .body(body)
                .ip(ip)
                .createdAt(Instant.now())
                .hidden(false)
                .build());

        notify(saved);
        return mapper.mapToDto(saved);
    }

    /* Сообщение уже в базе, поэтому доставка — дело необязательное: упавший
       телеграм не должен превращаться в ошибку у того, кто писал. */
    private void notify(Message message) {
        notifier.ifPresent(target -> {
            try {
                target.onNewMessage(message);
            } catch (RuntimeException e) {
                log.error("GUESTBOOK NOTIFY ERROR: " + e.getMessage());
            }
        });
    }

    @Override
    public List<MessageAdminDto> getAll(String adminKey) {
        requireAdmin(adminKey);
        return mapper.mapToAdminDto(repository.findTop100ByOrderByCreatedAtDesc());
    }

    @Override
    @Transactional
    public void hide(Long id, String adminKey) {
        setHidden(id, adminKey, true);
    }

    @Override
    @Transactional
    public void restore(Long id, String adminKey) {
        setHidden(id, adminKey, false);
    }

    private void setHidden(Long id, String adminKey, boolean hidden) {
        requireAdmin(adminKey);
        repository.findById(id).ifPresent(message -> {
            message.setHidden(hidden);
            repository.save(message);
        });
    }

    private boolean isDuplicate(String body) {
        return body.length() >= DUPLICATE_MIN_LENGTH
                && repository.existsByBodyIgnoreCaseAndCreatedAtAfter(
                body, Instant.now().minus(DUPLICATE_WINDOW));
    }

    private void checkRate(String ip) {
        if (repository.countByCreatedAtAfter(Instant.now().minus(Duration.ofHours(1))) >= PER_HOUR_TOTAL) {
            log.warn("GUESTBOOK: общий лимит {} сообщений в час исчерпан, отказ для {}", PER_HOUR_TOTAL, ip);
            throw new GuestbookRateLimited("the guestbook is busy right now, try later");
        }

        Optional<Message> last = repository.findFirstByIpOrderByCreatedAtDesc(ip);
        if (last.isPresent()
                && Duration.between(last.get().getCreatedAt(), Instant.now()).compareTo(COOLDOWN) < 0) {
            throw new GuestbookRateLimited("one message per " + COOLDOWN.toMinutes() + " minutes");
        }
        if (repository.countByIpAndCreatedAtAfter(ip, Instant.now().minus(Duration.ofDays(1))) >= PER_DAY) {
            throw new GuestbookRateLimited("no more than " + PER_DAY + " messages a day");
        }
    }

    /* Ключ не задан — админские ручки закрыты наглухо: лучше не работать,
       чем пустить кого угодно. Сравнение постоянное по времени. */
    private void requireAdmin(String adminKey) {
        if (adminKeyConfigured == null || adminKeyConfigured.isBlank()) {
            throw new AdminKeyRequired("admin key is not configured");
        }
        if (adminKey == null
                || !MessageDigest.isEqual(adminKey.getBytes(StandardCharsets.UTF_8),
                adminKeyConfigured.getBytes(StandardCharsets.UTF_8))) {
            throw new AdminKeyRequired("bad admin key");
        }
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        // в строке замены слэш экранирует следующий символ, поэтому здесь
        // настоящие переводы строки, а не "\\n"
        return CONTROL.matcher(value).replaceAll("")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
