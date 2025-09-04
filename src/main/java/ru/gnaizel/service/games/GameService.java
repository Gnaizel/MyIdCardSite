package ru.gnaizel.service.games;

import ru.gnaizel.dto.games.GameDto;

import java.util.List;

public interface GameService {
    List<GameDto> getRecentlyGames();
    double getTotalHours();
}
