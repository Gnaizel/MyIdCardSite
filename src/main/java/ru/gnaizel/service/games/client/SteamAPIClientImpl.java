package ru.gnaizel.service.games.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import ru.gnaizel.dto.games.GRPGSteamResponseDto;
import ru.gnaizel.dto.games.GOGSteamResponseDto;
import ru.gnaizel.dto.games.SteamOwnedGamesResponse;
import ru.gnaizel.dto.games.SteamRecentGamesResponse;
import ru.gnaizel.exception.SteamApiResponseException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class SteamAPIClientImpl implements SteamAPIClient {
    private final RestTemplate template = new RestTemplate();

    @Value("${steam.api-token}")
    private String token;

    @Value("${steam.id}")
    private long id;

    @Override
    public List<GRPGSteamResponseDto> getLastActivity() {
        String url = "https://api.steampowered.com/IPlayerService/GetRecentlyPlayedGames/v0001/?" +
                "key=" + token + "&" +
                "steamid=" + id + "&" +
                "format=json";
        List<GRPGSteamResponseDto> games = new ArrayList<>();
        try {
            games = template.getForObject(url, SteamRecentGamesResponse.class)
                    .getResponse()
                    .getGames();
        } catch (HttpClientErrorException e) {
            log.error("STEAM API ERROR: " + e.getMessage());
        }

        if (games == null || games.isEmpty()) {
            throw new SteamApiResponseException("STEAM API ERROR: " + "array is null");
        }

        return games;
    }

    @Override
    public List<GOGSteamResponseDto> getAllGameLib() {
        String url = "https://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/?" +
                "key=" + token + "&" +
                "steamid=" + id + "&" +
                "format=json";
        List<GOGSteamResponseDto> games = new ArrayList<>();
        try {
             games = template.getForObject(url, SteamOwnedGamesResponse.class)
                     .getResponse()
                     .getGames();
        } catch (HttpClientErrorException e) {
            log.error("STEAM API ERROR: " + e.getMessage());
        }

        if (games == null || games.isEmpty()) {
            throw new SteamApiResponseException("STEAM API ERROR");
        }

        return games;
    }
}
