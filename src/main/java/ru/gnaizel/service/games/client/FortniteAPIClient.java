package ru.gnaizel.service.games.client;

import ru.gnaizel.dto.games.FortniteStatsDto;

import java.util.Optional;

public interface FortniteAPIClient {
    /**
     * Статистика Fortnite за всё время, или пусто — если ключ не задан,
     * ник не найден или статистика закрыта настройками профиля.
     * Исключения наружу не выходят: Fortnite — одна игра из списка,
     * и её отсутствие не должно ронять весь блок.
     */
    Optional<FortniteStatsDto> getStats();
}
