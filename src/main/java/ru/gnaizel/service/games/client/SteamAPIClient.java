package ru.gnaizel.service.games.client;

import ru.gnaizel.dto.games.GOGSteamResponseDto;

import java.util.List;

public interface SteamAPIClient {
    /**
     * Вся библиотека со всех аккаунтов, слитая по appid. У каждой игры есть
     * время последнего запуска, поэтому отдельный запрос за «недавними»
     * не нужен — и список не ограничен двумя неделями.
     */
    Library getAllGameLib();

    /**
     * @param complete все ли аккаунты ответили. Неполную библиотеку нельзя
     *                 класть в кэш как готовую: счётчик часов молча просядет
     *                 и застынет так до конца срока жизни кэша.
     */
    record Library(List<GOGSteamResponseDto> games, boolean complete) {
    }
}
