package ru.gnaizel.service.games;

import ru.gnaizel.dto.games.GameDto;

import java.util.List;

public interface GameService {
    List<GameDto> getRecentlyGames();

    double getTotalHours();

    /**
     * Игру уже считает Steam или Fortnite API, и Discord-счётчику её вести
     * не нужно: у них часы точные и за всё время, а у нас — с первого раза,
     * как Discord её увидел.
     */
    boolean countedElsewhere(String name);
}
