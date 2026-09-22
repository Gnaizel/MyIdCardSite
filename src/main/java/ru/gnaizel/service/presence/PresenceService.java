package ru.gnaizel.service.presence;

import ru.gnaizel.dto.presence.PresenceDto;

import java.util.Optional;

public interface PresenceService {
    /**
     * Во что играю прямо сейчас по данным Discord, если играю.
     * <p>
     * Пусто означает сразу несколько вещей — не настроено, Discord закрыт,
     * игра не запущена, сервис не ответил, — и различать их незачем:
     * во всех случаях показывать нечего, и блок просто не появляется.
     */
    Optional<PresenceDto> nowPlaying();
}
