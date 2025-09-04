package ru.gnaizel.dto.games;

import lombok.Data;

import java.util.List;

@Data
public class SteamOwnedGamesResponse {
    private Response response;

    @Data
    public static class Response {
        byte total_count;
        List<GOGSteamResponseDto> games;
    }
}
