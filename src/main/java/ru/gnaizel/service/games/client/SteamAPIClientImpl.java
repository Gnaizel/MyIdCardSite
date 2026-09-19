package ru.gnaizel.service.games.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.gnaizel.dto.games.GOGSteamResponseDto;
import ru.gnaizel.dto.games.GRPGSteamResponseDto;
import ru.gnaizel.dto.games.SteamOwnedGamesResponse;
import ru.gnaizel.dto.games.SteamRecentGamesResponse;
import ru.gnaizel.exception.SteamApiResponseException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Аккаунтов может быть несколько: ключ Steam выдаётся разработчику, а не
 * профилю, и одним ключом опрашивается любой публичный профиль. Библиотеки
 * складываются по appid — одна и та же игра на двух аккаунтах даёт сумму
 * часов, а не два отдельных пункта.
 */
@Slf4j
@Service
public class SteamAPIClientImpl implements SteamAPIClient {
    private static final String RECENT_URL =
            "https://api.steampowered.com/IPlayerService/GetRecentlyPlayedGames/v0001/"
                    + "?key=%s&steamid=%s&format=json";

    /* include_played_free_games обязателен: без него Steam не отдаёт free-to-play
       игры, и недавно сыгранная F2P не находится в библиотеке при сшивке. */
    private static final String OWNED_URL =
            "https://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/"
                    + "?key=%s&steamid=%s&include_played_free_games=1&format=json";

    private final RestTemplate template = new RestTemplate();

    @Value("${steam.api-token}")
    private String token;

    @Value("${steam.ids:}")
    private String ids;

    @Override
    public List<GRPGSteamResponseDto> getLastActivity() {
        Map<Integer, GRPGSteamResponseDto> merged = new LinkedHashMap<>();
        for (String id : steamIds()) {
            for (GRPGSteamResponseDto game : recent(id)) {
                merged.merge(game.getAppid(), game, SteamAPIClientImpl::combineRecent);
            }
        }
        if (merged.isEmpty()) {
            throw new SteamApiResponseException("STEAM API ERROR: array is null");
        }
        return List.copyOf(merged.values());
    }

    @Override
    public List<GOGSteamResponseDto> getAllGameLib() {
        Map<Integer, GOGSteamResponseDto> merged = new LinkedHashMap<>();
        for (String id : steamIds()) {
            for (GOGSteamResponseDto game : owned(id)) {
                merged.merge(game.getAppid(), game, SteamAPIClientImpl::combineOwned);
            }
        }
        if (merged.isEmpty()) {
            throw new SteamApiResponseException("STEAM API ERROR");
        }
        return List.copyOf(merged.values());
    }

    /* Аккаунты перечисляются через запятую. Ноль отсеиваем вместе с пустыми:
       он годами стоял заглушкой «своего id пока нет». */
    private List<String> steamIds() {
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(id -> !id.isBlank() && !"0".equals(id))
                .toList();
    }

    /* Ошибка одного аккаунта не должна ронять остальные: профиль могли закрыть,
       id — опечатать. Такой просто не попадает в сумму, о чём есть строка в логе. */
    private List<GRPGSteamResponseDto> recent(String id) {
        try {
            SteamRecentGamesResponse response =
                    template.getForObject(RECENT_URL.formatted(token, id), SteamRecentGamesResponse.class);
            return games(response == null || response.getResponse() == null
                    ? null : response.getResponse().getGames(), id, "недавние");
        } catch (RestClientException e) {
            log.error("STEAM API ERROR (аккаунт {}): {}", id, e.getMessage());
            return List.of();
        }
    }

    private List<GOGSteamResponseDto> owned(String id) {
        try {
            SteamOwnedGamesResponse response =
                    template.getForObject(OWNED_URL.formatted(token, id), SteamOwnedGamesResponse.class);
            return games(response == null || response.getResponse() == null
                    ? null : response.getResponse().getGames(), id, "библиотека");
        } catch (RestClientException e) {
            log.error("STEAM API ERROR (аккаунт {}): {}", id, e.getMessage());
            return List.of();
        }
    }

    /* Пустой response приходит и у закрытого профиля, и когда за две недели
       не играли. Отличить одно от другого Steam не даёт, поэтому просто
       говорим, что аккаунт ничего не дал. */
    private <T> List<T> games(List<T> games, String id, String what) {
        if (games == null || games.isEmpty()) {
            log.info("STEAM: аккаунт {} не отдал ничего ({}) — закрытый профиль или пусто", id, what);
            return List.of();
        }
        return games;
    }

    private static GRPGSteamResponseDto combineRecent(GRPGSteamResponseDto first, GRPGSteamResponseDto second) {
        first.setPlaytime_2weeks(first.getPlaytime_2weeks() + second.getPlaytime_2weeks());
        first.setPlaytime_forever(first.getPlaytime_forever() + second.getPlaytime_forever());
        first.setPlaytime_windows_forever(
                first.getPlaytime_windows_forever() + second.getPlaytime_windows_forever());
        return first;
    }

    private static GOGSteamResponseDto combineOwned(GOGSteamResponseDto first, GOGSteamResponseDto second) {
        first.setPlaytime_2weeks(first.getPlaytime_2weeks() + second.getPlaytime_2weeks());
        first.setPlaytime_forever(first.getPlaytime_forever() + second.getPlaytime_forever());
        first.setPlaytime_windows_forever(
                first.getPlaytime_windows_forever() + second.getPlaytime_windows_forever());
        first.setPlaytime_disconnected(
                first.getPlaytime_disconnected() + second.getPlaytime_disconnected());
        /* Часы складываются, а «когда последний раз играл» — нет: берём самый
           поздний из аккаунтов, иначе игра уехала бы в конец списка недавних. */
        first.setRtime_last_played(
                Math.max(first.getRtime_last_played(), second.getRtime_last_played()));
        return first;
    }
}
