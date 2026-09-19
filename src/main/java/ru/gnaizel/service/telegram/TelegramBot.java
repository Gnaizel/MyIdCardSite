package ru.gnaizel.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.gnaizel.exception.AvatarRejected;
import ru.gnaizel.service.avatar.AvatarService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Приём команд из телеграма: кнопка смены аватарки и возврат стандартной.
 * <p>
 * Работает длинными опросами, а не вебхуком. Вебхук был бы экономнее, но
 * требовал бы публичного адреса, и локальный запуск без домена перестал бы
 * работать вовсе. Висящий getUpdates на фоновом потоке не стоит почти ничего.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBot {
    private static final int POLL_SECONDS = 30;
    private static final int RETRY_SECONDS = 5;

    private static final String CHANGE = "avatar:change";
    private static final String RESET = "avatar:reset";

    private static final String MENU = """
            <b>blackmail box</b>

            Сюда приходят сообщения из гостевой книги.
            Ещё можно сменить аватарку на карточке.""";

    private final TelegramClient client;
    private final AvatarService avatarService;

    private volatile boolean running;
    private Thread worker;

    /* Трогает его только поток опроса. */
    private long offset;

    @PostConstruct
    void start() {
        if (!client.configured()) {
            log.warn("TELEGRAM BOT: команды выключены — не задан токен и/или чат");
            return;
        }
        running = true;
        worker = new Thread(this::poll, "telegram-bot");
        worker.setDaemon(true);
        worker.start();
        log.info("TELEGRAM BOT: слушаю команды");
    }

    @PreDestroy
    void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    private void poll() {
        while (running) {
            Optional<JsonNode> updates = client.callLong("getUpdates", Map.of(
                    "offset", offset,
                    "timeout", POLL_SECONDS,
                    "allowed_updates", List.of("message", "callback_query")));
            if (updates.isEmpty()) {
                /* Ошибка связи или отказ Bot API: без паузы цикл начал бы
                   долбить телеграм без остановки и получил бы бан по частоте. */
                if (!sleep(RETRY_SECONDS)) {
                    return;
                }
                continue;
            }
            updates.get().forEach(this::dispatch);
        }
    }

    /* offset двигаем до разбора, а не после: иначе одно битое обновление
       телеграм присылал бы снова и снова, и бот завис бы на нём навсегда. */
    private void dispatch(JsonNode update) {
        offset = update.path("update_id").asLong() + 1;
        try {
            handle(update);
        } catch (AvatarRejected e) {
            say("не вышло: " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("TELEGRAM BOT: {}", client.hideToken(e.getMessage()));
        }
    }

    private void handle(JsonNode update) {
        JsonNode callback = update.path("callback_query");
        if (!callback.isMissingNode()) {
            onButton(callback);
            return;
        }
        JsonNode message = update.path("message");
        if (!message.isMissingNode()) {
            onMessage(message);
        }
    }

    private void onButton(JsonNode callback) {
        if (!fromOwner(callback.path("message").path("chat"))) {
            return;
        }
        String data = callback.path("data").asText();
        /* Ответить обязательно, иначе кнопка в клиенте крутится до таймаута. */
        client.call("answerCallbackQuery", Map.of("callback_query_id", callback.path("id").asText()));

        if (RESET.equals(data)) {
            avatarService.reset();
            say("Вернул аватарку из сборки.");
        } else if (CHANGE.equals(data)) {
            say("Пришли фото следующим сообщением — оно станет аватаркой."
                    + " Можно и файлом, если не хочешь сжатия телеграмом.");
        }
    }

    private void onMessage(JsonNode message) {
        if (!fromOwner(message.path("chat"))) {
            log.warn("TELEGRAM BOT: сообщение из чужого чата {}, игнорирую",
                    message.path("chat").path("id").asText());
            return;
        }
        photoFileId(message).ifPresentOrElse(this::replaceAvatar, this::menu);
    }

    /* Сжатое фото приходит массивом размеров — последний самый крупный.
       Отправленное файлом приходит документом, у него размер один. */
    private Optional<String> photoFileId(JsonNode message) {
        JsonNode photo = message.path("photo");
        if (photo.isArray() && !photo.isEmpty()) {
            return Optional.of(photo.get(photo.size() - 1).path("file_id").asText());
        }
        JsonNode document = message.path("document");
        if (document.path("mime_type").asText("").startsWith("image/")) {
            return Optional.of(document.path("file_id").asText());
        }
        return Optional.empty();
    }

    private void replaceAvatar(String fileId) {
        Optional<byte[]> bytes = client.call("getFile", Map.of("file_id", fileId))
                .map(file -> file.path("file_path").asText())
                .flatMap(client::download);
        if (bytes.isEmpty()) {
            say("Не смог забрать файл у телеграма, попробуй ещё раз.");
            return;
        }
        avatarService.replace(bytes.get());
        say("Готово, аватарка заменена: %d КБ. На сайте обновится в течение пяти минут."
                .formatted(avatarService.current().bytes().length / 1024));
    }

    private void menu() {
        client.call("sendMessage", Map.of(
                "chat_id", client.chatId(),
                "text", MENU,
                "parse_mode", "HTML",
                "reply_markup", Map.of("inline_keyboard", List.of(
                        List.of(Map.of("text", "🖼 Сменить аватарку", "callback_data", CHANGE)),
                        List.of(Map.of("text", "↩️ Вернуть стандартную", "callback_data", RESET))))));
    }

    private void say(String text) {
        client.call("sendMessage", Map.of(
                "chat_id", client.chatId(),
                "text", text,
                "disable_web_page_preview", true));
    }

    /* Бот отвечает только владельцу: иначе аватарку сайта сменил бы любой,
       кто нашёл бота в поиске. */
    private boolean fromOwner(JsonNode chat) {
        return client.chatId().equals(chat.path("id").asText());
    }

    private boolean sleep(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
