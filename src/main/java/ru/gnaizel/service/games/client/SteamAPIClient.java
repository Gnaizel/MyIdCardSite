package ru.gnaizel.service.games.client;

import ru.gnaizel.dto.games.GOGSteamResponseDto;
import ru.gnaizel.dto.games.NowPlayingDto;
import ru.gnaizel.dto.games.SteamAccountDto;

import java.util.List;
import java.util.Optional;

public interface SteamAPIClient {
    /**
     * Вся библиотека со всех аккаунтов, слитая по appid. У каждой игры есть
     * время последнего запуска, поэтому отдельный запрос за «недавними»
     * не нужен — и список не ограничен двумя неделями.
     */
    Library getAllGameLib();

    /**
     * Во что играют прямо сейчас, или пусто. Спрашивается у профиля, а не
     * у библиотеки: это отдельный и куда более дешёвый запрос — все аккаунты
     * умещаются в один, поэтому его можно звать часто, в отличие от разбора
     * библиотек.
     */
    Optional<NowPlayingDto> getNowPlaying();

    /**
     * Профили всех аккаунтов в том порядке, в каком они перечислены
     * в настройках. Берутся из того же запроса, что и {@link #getNowPlaying()}.
     */
    List<SteamAccountDto> getAccounts();

    /**
     * @param complete все ли аккаунты ответили. Неполную библиотеку нельзя
     *                 класть в кэш как готовую: счётчик часов молча просядет
     *                 и застынет так до конца срока жизни кэша.
     */
    record Library(List<GOGSteamResponseDto> games, boolean complete) {
    }
}
