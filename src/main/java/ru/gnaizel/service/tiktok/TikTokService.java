package ru.gnaizel.service.tiktok;

import ru.gnaizel.dto.tiktok.TikTokVideoDto;

import java.util.List;
import java.util.Optional;

public interface TikTokService {
    /**
     * Репосты с профиля, свежие сверху. Пустой список — значит не настроено
     * или TikTok не ответил; блок на странице в таком случае просто не
     * появляется, а не показывает ошибку.
     */
    List<TikTokVideoDto> getReposts();

    /**
     * Настоящий адрес видео по его id, если оно есть в текущем списке.
     * Нужен только проксирующей ручке: наружу этот адрес не отдаётся.
     */
    Optional<String> playAddr(String id);
}
