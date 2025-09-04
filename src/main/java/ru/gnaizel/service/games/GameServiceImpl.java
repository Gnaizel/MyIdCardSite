package ru.gnaizel.service.games;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.gnaizel.dto.games.GOGSteamResponseDto;
import ru.gnaizel.dto.games.GRPGSteamResponseDto;
import ru.gnaizel.dto.games.GameDto;
import ru.gnaizel.exception.GameFiltrationError;
import ru.gnaizel.mapper.game.GameMapper;
import ru.gnaizel.model.games.Game;
import ru.gnaizel.service.games.client.SteamAPIClient;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {
    public final SteamAPIClient steamAPIClient;

    @Override
    public List<GameDto> getRecentlyGames() {
        List<GRPGSteamResponseDto> GRPGs = steamAPIClient.getLastActivity();
        List<GOGSteamResponseDto> GOGs = steamAPIClient.getAllGameLib();

        List<Game> games = new ArrayList<>();

        Map<Integer, GOGSteamResponseDto> gogsMap = new HashMap<>();

        for (GOGSteamResponseDto gog : GOGs) {
            gogsMap.put(gog.getAppid(), gog);
        }

        for (GRPGSteamResponseDto grpg : GRPGs) {
            GOGSteamResponseDto correspondingGog = gogsMap.get(grpg.getAppid());

            if (correspondingGog != null) {
                games.add(GameMapper.GRPGDtoAndGOGDtoToGame(grpg, correspondingGog));
            }
        }

        if (games.isEmpty()) {
            throw new GameFiltrationError("ERROR IN getRecentlyGames: games list is empty");
        }

        Collections.reverse(games);

        return games.stream()
                .map(GameMapper::gameToGameDto)
                .toList();
    }

    @Override
    public double getTotalHours() {
        List<GOGSteamResponseDto> GOGs = steamAPIClient.getAllGameLib();

        return GOGs.stream()
                .mapToDouble(gog -> (double) gog.getPlaytime_forever() / 60)
                .sum();
    }
}
