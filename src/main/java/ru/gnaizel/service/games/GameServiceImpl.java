package ru.gnaizel.service.games;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.gnaizel.dto.games.GOGSteamResponseDto;
import ru.gnaizel.dto.games.GameDto;
import ru.gnaizel.exception.GameFiltrationError;
import ru.gnaizel.mapper.game.GameMapper;
import ru.gnaizel.model.games.Game;
import ru.gnaizel.service.games.client.SteamAPIClient;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {
    public final SteamAPIClient steamAPIClient;

    @Value("${steam.recent-limit:12}")
    private int limit;

    /**
     * Последние запущенные игры, без ограничения по сроку давности.
     * <p>
     * Раньше список строился пересечением библиотеки с ответом
     * GetRecentlyPlayedGames, а тот отдаёт только последние две недели. Стоило
     * неделю не играть — пересечение пустело, и блок на странице исчезал
     * целиком. Время последнего запуска есть у каждой игры в библиотеке,
     * так что достаточно отсортировать по нему и взять сколько нужно: список
     * остаётся непустым, даже если в Steam не заходили полгода.
     */
    @Override
    public List<GameDto> getRecentlyGames() {
        List<GameDto> games = steamAPIClient.getAllGameLib().stream()
                /* Ноль — это «не запускали ни разу». Такие игры в список
                   последних не попадают: у них нет своего места на шкале. */
                .filter(gog -> gog.getRtime_last_played() > 0)
                .map(GameMapper::gogDtoToGame)
                .sorted(Comparator.comparing(Game::getRtime_last_played).reversed())
                .limit(limit)
                .map(GameMapper::gameToGameDto)
                .toList();

        if (games.isEmpty()) {
            throw new GameFiltrationError("ERROR IN getRecentlyGames: games list is empty");
        }

        return games;
    }

    @Override
    public double getTotalHours() {
        return steamAPIClient.getAllGameLib().stream()
                .mapToDouble(gog -> (double) gog.getPlaytime_forever() / 60)
                .sum();
    }
}
