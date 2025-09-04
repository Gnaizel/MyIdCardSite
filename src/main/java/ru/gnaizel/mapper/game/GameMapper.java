package ru.gnaizel.mapper.game;

import lombok.extern.slf4j.Slf4j;
import ru.gnaizel.dto.games.GOGSteamResponseDto;
import ru.gnaizel.dto.games.GRPGSteamResponseDto;
import ru.gnaizel.dto.games.GameDto;
import ru.gnaizel.exception.GameMappingDtoError;
import ru.gnaizel.model.games.Game;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
public class GameMapper {

    public static GameDto gameToGameDto(Game game) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
        Integer playtime2weeks = game.getPlaytime_2weeks();
        Integer playtimeForever = game.getPlaytime_forever();

        String fmtTwoWeeks = formatPlaytime(playtime2weeks);
        String fmtForever = formatPlaytime(playtimeForever);

        String lastPlayed = game.getRtime_last_played() != null
                ? game.getRtime_last_played().format(formatter)
                : null;

        return GameDto.builder()
                .appid(game.getAppid())
                .playtime_2weeks(fmtTwoWeeks)
                .playtime_forever(fmtForever)
                .name(game.getName())
                .img_icon_url(game.getImg_icon_url())
                .banner_url(game.getBanner_url())
                .rtime_last_played(lastPlayed)
                .playtime_disconnected(game.getPlaytime_disconnected())
                .build();
    }

    private static String formatPlaytime(Integer minutes) {
        if (minutes == null || minutes == 0) {
            return "0m";
        }
        if (minutes < 60) {
            return minutes + "m";
        }
        int hours = minutes / 60;
        int remMinutes = minutes % 60;
        if (remMinutes == 0) {
            return hours + "h";
        }
        return hours + "h " + remMinutes + "m";
    }

    public static Game GRPGDtoAndGOGDtoToGame(GRPGSteamResponseDto GRPG, GOGSteamResponseDto GOG) {
        int appid = GRPG.getAppid();

        if (appid != GOG.getAppid()) {
            throw new GameMappingDtoError("Id's GRPG and GOG don't match");
        }

        Instant lastPlayedInstant = Instant.ofEpochSecond(GOG.getRtime_last_played());
        LocalDateTime lastPlayed = LocalDateTime.ofInstant(lastPlayedInstant, ZoneId.systemDefault());


        Game game = new Game();
        game.setAppid(appid);
        game.setPlaytime_2weeks(GRPG.getPlaytime_2weeks());
        game.setPlaytime_forever(GRPG.getPlaytime_forever());
        game.setPlaytime_windows_forever(GRPG.getPlaytime_windows_forever());
        game.setName(GRPG.getName());
        game.setBanner_url("https://cdn.akamai.steamstatic.com/steam/apps/" +
                appid + "/" +
                "header.jpg");
        game.setImg_icon_url("https://media.steampowered.com/steamcommunity/public/images/apps/" +
                appid + "/" +
                GRPG.getImg_icon_url() + ".jpg");
        game.setRtime_last_played(lastPlayed);
        game.setPlaytime_disconnected(GOG.getPlaytime_disconnected());
        return game;
    }
}
