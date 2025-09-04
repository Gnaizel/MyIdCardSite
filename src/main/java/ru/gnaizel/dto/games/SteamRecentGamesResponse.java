package ru.gnaizel.dto.games;

import lombok.Data;

import java.util.List;

@Data
public class SteamRecentGamesResponse {
    private Response response;

    @Data
    public static class Response {
        byte total_count;
        List<GRPGSteamResponseDto> games;
    }
}
