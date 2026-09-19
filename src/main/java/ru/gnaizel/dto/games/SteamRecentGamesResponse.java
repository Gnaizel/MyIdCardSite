package ru.gnaizel.dto.games;

import lombok.Data;

import java.util.List;

@Data
public class SteamRecentGamesResponse {
    private Response response;

    @Data
    public static class Response {
        /* int, а не byte: в byte влезает 127, и аккаунт со 128 играми
           ронял бы разбор всего ответа. Поле не используется, но приходит. */
        int total_count;
        List<GRPGSteamResponseDto> games;
    }
}
