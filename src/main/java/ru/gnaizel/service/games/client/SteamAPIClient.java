package ru.gnaizel.service.games.client;

import ru.gnaizel.dto.games.GOGSteamResponseDto;

import java.util.List;

public interface SteamAPIClient {
    /**
     * Вся библиотека со всех аккаунтов, слитая по appid. У каждой игры есть
     * время последнего запуска, поэтому отдельный запрос за «недавними»
     * не нужен — и список больше не ограничен двумя неделями.
     */
    List<GOGSteamResponseDto> getAllGameLib();
}
