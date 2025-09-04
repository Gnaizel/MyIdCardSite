package ru.gnaizel.controller.game;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.gnaizel.dto.games.GRPGSteamResponseDto;
import ru.gnaizel.dto.games.GameDto;
import ru.gnaizel.model.games.Game;
import ru.gnaizel.service.games.GameService;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class GameController {
    public final GameService gameService;

    @GetMapping("/games")
    public List<GameDto> getGames() {
        return gameService.getRecentlyGames();
    }

    @GetMapping("/games-total-hours")
    public double getTotalHours() {
        return gameService.getTotalHours();
    }
}
