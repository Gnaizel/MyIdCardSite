package ru.gnaizel.service.games;

import ru.gnaizel.dto.games.GameDto;
import ru.gnaizel.model.games.Game;

import java.util.List;

public interface GameService {
    List<GameDto> getRecentlyGames();

    double getTotalHours();

    /**
     * Все хоть раз запущенные игры Steam и Fortnite, самые свежие первыми.
     * Для ленты: ей нужно время последнего запуска, а не готовые подписи.
     */
    List<Game> getPlayedGames();
}
