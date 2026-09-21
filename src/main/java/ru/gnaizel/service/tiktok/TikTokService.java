package ru.gnaizel.service.tiktok;

import ru.gnaizel.dto.tiktok.TikTokVideoDto;

import java.util.List;

public interface TikTokService {
    /**
     * Репосты с профиля, свежие сверху. Пустой список — значит не настроено
     * или TikTok не ответил; блок на странице в таком случае просто не
     * появляется, а не показывает ошибку.
     */
    List<TikTokVideoDto> getReposts();
}
