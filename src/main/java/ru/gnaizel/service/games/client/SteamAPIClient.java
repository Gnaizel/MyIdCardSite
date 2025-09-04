package ru.gnaizel.service.games.client;

import ru.gnaizel.dto.games.GOGSteamResponseDto;
import ru.gnaizel.dto.games.GRPGSteamResponseDto;

import java.util.List;

public interface SteamAPIClient {
    List<GRPGSteamResponseDto> getLastActivity();

    List<GOGSteamResponseDto> getAllGameLib();
}
