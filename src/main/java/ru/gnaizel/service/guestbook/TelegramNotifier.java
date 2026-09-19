package ru.gnaizel.service.guestbook;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.gnaizel.model.guestbook.Message;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Отправляет новое сообщение гостевой книги в телеграм.
 * <p>
 * Пока {@code telegram.bot-token} или {@code telegram.chat-id} пустые, класс
 * ничего не делает: сообщения всё так же копятся в базе и видны через
 * {@code GET /guestbook/all}. Так сайт поднимается и без телеграма.
 */
@Slf4j
@Service
public class TelegramNotifier implements MessageNotifier {
    private static final String API = "https://api.telegram.org/bot%s/sendMessage";

    /* Телеграм режет бота примерно на 20 сообщениях в минуту в один чат и
       дальше отвечает 429. Свой потолок ставим ниже — заодно это защита от
       флуда: если в книгу польётся поток, телефон не станет будильником. */
    private static final int PER_MINUTE = 15;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    /* Одна очередь и один поток: отправка не должна ни задерживать ответ
       гостю, ни держать открытой транзакцию, внутри которой её вызывают.
       Очередь ограничена — при флуде лучше потерять уведомление, чем
       память: сообщение уже сохранено в базе. */
    private final ThreadPoolExecutor sender = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(100),
            runnable -> {
                Thread thread = new Thread(runnable, "telegram-notifier");
                thread.setDaemon(true);
                return thread;
            },
            (runnable, executor) -> log.warn("TELEGRAM: очередь переполнена, уведомление отброшено"));

    private final RestTemplate template = timeoutedTemplate();

    /* Трогает их только поток отправки, поэтому синхронизация не нужна. */
    private final Deque<Instant> recentlySent = new ArrayDeque<>();
    private boolean floodNoticeSent;

    @Value("${telegram.bot-token:}")
    private String botToken;

    @Value("${telegram.chat-id:}")
    private String chatId;

    @Value("${telegram.zone:UTC+4}")
    private String zone;

    @PostConstruct
    void announce() {
        if (enabled()) {
            log.info("TELEGRAM: уведомления включены, чат {}", chatId);
        } else {
            log.warn("TELEGRAM: уведомления выключены — не заданы telegram.bot-token и/или telegram.chat-id");
        }
    }

    @Override
    public void onNewMessage(Message message) {
        if (!enabled()) {
            return;
        }
        String text = render(message);
        sender.execute(() -> send(text, message.getId()));
    }

    @PreDestroy
    void shutdown() {
        sender.shutdown();
        try {
            if (!sender.awaitTermination(5, TimeUnit.SECONDS)) {
                sender.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sender.shutdownNow();
        }
    }

    private boolean enabled() {
        return botToken != null && !botToken.isBlank()
                && chatId != null && !chatId.isBlank();
    }

    /* Всё, что ниже, выполняется на потоке отправки. */

    private void send(String text, Long id) {
        if (!withinLimit()) {
            log.warn("TELEGRAM: превышен лимит {} сообщений в минуту, #{} не отправлено", PER_MINUTE, id);
            if (!floodNoticeSent) {
                floodNoticeSent = true;
                post("⚠️ поток сообщений в гостевой книге — дальше молчу."
                        + " Что пришло, видно в <code>GET /guestbook/all</code>.");
            }
            return;
        }
        floodNoticeSent = false;
        post(text);
    }

    private void post(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> payload = Map.of(
                "chat_id", chatId,
                "text", text,
                "parse_mode", "HTML",
                "disable_web_page_preview", true);
        try {
            template.postForEntity(API.formatted(botToken), new HttpEntity<>(payload, headers), String.class);
        } catch (RestClientException e) {
            /* RestTemplate вписывает в текст ошибки весь url, а в url лежит
               токен — без вырезания он оседает в логах контейнера, которые
               защищены куда хуже, чем .env с правами 600. */
            log.error("TELEGRAM SEND ERROR: {}", withoutToken(e.getMessage()));
        }
    }

    private String withoutToken(String text) {
        if (text == null) {
            return "";
        }
        return botToken.isBlank() ? text : text.replace(botToken, "***");
    }

    private boolean withinLimit() {
        Instant now = Instant.now();
        while (!recentlySent.isEmpty()
                && Duration.between(recentlySent.peekFirst(), now).compareTo(WINDOW) > 0) {
            recentlySent.pollFirst();
        }
        if (recentlySent.size() >= PER_MINUTE) {
            return false;
        }
        recentlySent.addLast(now);
        return true;
    }

    /* Текст гостя экранируется целиком: иначе любая угловая скобка в сообщении
       ломает разбор HTML на стороне телеграма, и уведомление просто не дойдёт. */
    private String render(Message message) {
        String nick = message.getNick() == null || message.getNick().isBlank()
                ? "anon"
                : message.getNick();
        String when = LocalDateTime.ofInstant(message.getCreatedAt(), ZoneId.of(zone)).format(WHEN);
        return """
                💌 <b>%s</b>

                %s

                <code>#%d · %s · %s</code>"""
                .formatted(escape(nick), escape(message.getBody()),
                        message.getId(), when, escape(message.getIp()));
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /* У RestTemplate по умолчанию таймаутов нет вообще: подвисший телеграм
       намертво занял бы единственный поток отправки. */
    private static RestTemplate timeoutedTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }
}
