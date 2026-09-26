package ru.gnaizel.service.games;

import ru.gnaizel.dto.games.GameDto;

import java.util.List;

public interface GameService {
    /**
     * Страница списка игр: offset — сколько пропустить, limit — сколько
     * отдать (0 — сколько задано в настройках). Пустая страница за концом
     * списка — не ошибка, а знак, что дальше ничего нет.
     */
    List<GameDto> getGames(Sort sort, int offset, int limit);

    double getTotalHours();

    enum Sort {
        /** По последнему запуску, самые свежие первыми. */
        RECENT,
        /** По наигранным часам, от большего к меньшему. */
        HOURS;

        /* Неизвестное значение — основной вид, а не ошибка: адрес могли
           набрать руками. */
        public static Sort parse(String value) {
            return "hours".equalsIgnoreCase(value) ? HOURS : RECENT;
        }
    }
}
