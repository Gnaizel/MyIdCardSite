package ru.gnaizel.controller.game;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.gnaizel.dto.games.GameDto;
import ru.gnaizel.dto.games.SteamAccountDto;
import ru.gnaizel.service.games.GameService;
import ru.gnaizel.service.games.client.SteamAPIClient;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class GameController {
    public final GameService gameService;
    private final SteamAPIClient steamAPIClient;

    /* Список страницами: sort=recent — по последнему запуску, sort=hours —
       по наигранным часам. Без параметров — первая страница последних,
       как было всегда. */
    @GetMapping("/games")
    public List<GameDto> getGames(@RequestParam(defaultValue = "recent") String sort,
                                  @RequestParam(defaultValue = "0") int offset,
                                  @RequestParam(defaultValue = "0") int limit) {
        return gameService.getGames(GameService.Sort.parse(sort), offset, limit);
    }

    @GetMapping("/games-total-hours")
    public double getTotalHours() {
        return gameService.getTotalHours();
    }

    @GetMapping("/steam/accounts")
    public List<SteamAccountDto> getSteamAccounts() {
        return steamAPIClient.getAccounts();
    }
}
