package ru.gnaizel.mapper.game;

import lombok.extern.slf4j.Slf4j;
import ru.gnaizel.dto.games.FortniteStatsDto;
import ru.gnaizel.dto.games.GOGSteamResponseDto;
import ru.gnaizel.dto.games.GameDto;
import ru.gnaizel.dto.games.NowPlayingDto;
import ru.gnaizel.model.games.Game;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
public class GameMapper {

    public static GameDto gameToGameDto(Game game) {
        return gameToGameDto(game, false, null);
    }

    public static GameDto gameToGameDto(Game game, boolean playingNow, String joinUrl) {
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
                .playingNow(playingNow)
                .joinUrl(joinUrl)
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

    /* Fortnite приходит не из Steam, поэтому ни appid, ни картинок у него нет:
       название и изображения задаются настройками. В остальном это такая же
       строка списка — часы и время последнего запуска на своих местах. */
    /* Игра, о которой известно только из профиля: её ещё нет в библиотеке,
       потому что Steam обновляет её с задержкой. Часов не знаем, но картинки
       по appid соберутся те же самые. */
    public static Game nowPlayingToGame(NowPlayingDto playing) {
        Game game = new Game();
        game.setAppid(playing.getAppid());
        game.setName(playing.getName());
        game.setBanner_url("https://cdn.akamai.steamstatic.com/steam/apps/"
                + playing.getAppid() + "/header.jpg");
        game.setImg_icon_url("/image/game-icon.jpg");
        game.setRtime_last_played(LocalDateTime.now(ZoneId.of("UTC+4")));
        return game;
    }

    /* Игра не из Steam, которую сейчас видит Discord. Кроме названия
       и оформления он о ней ничего не знает: часов не отдаёт ни одно API,
       поэтому их и не показываем, а сами не считаем. */
    public static GameDto presenceToGameDto(String name, String icon, String banner) {
        return GameDto.builder()
                .name(name)
                .img_icon_url(icon != null ? icon : "/image/game-icon.jpg")
                .banner_url(banner)
                .playtime_forever("—")
                .playtime_2weeks("—")
                .rtime_last_played(LocalDateTime.now(ZoneId.of("UTC+4"))
                        .format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")))
                .playingNow(true)
                .build();
    }

    /* Discord и Steam пишут одну и ту же игру по-разному: «VALORANT»
       и «Valorant», с ™ и без, с двоеточием и без. Сравниваем по сути. */
    public static String sameName(String name) {
        return name == null ? "" : name.toLowerCase().replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    public static Game fortniteToGame(FortniteStatsDto stats, String name, String icon, String banner) {
        LocalDateTime lastPlayed = LocalDateTime.ofInstant(stats.getLastModified(), ZoneId.of("UTC+4"));

        Game game = new Game();
        game.setAppid(0);
        game.setPlaytime_2weeks(0);
        game.setPlaytime_forever(stats.getMinutesPlayed());
        game.setPlaytime_windows_forever(stats.getMinutesPlayed());
        game.setName(name);
        game.setImg_icon_url(icon);
        game.setBanner_url(banner);
        game.setRtime_last_played(lastPlayed);
        game.setPlaytime_disconnected(0);
        return game;
    }

    public static Game gogDtoToGame(GOGSteamResponseDto gog) {
        int appid = gog.getAppid();

        Instant lastPlayedInstant = Instant.ofEpochSecond(gog.getRtime_last_played());
        LocalDateTime lastPlayed = LocalDateTime.ofInstant(lastPlayedInstant, ZoneId.of("UTC+4"));

        Game game = new Game();
        game.setAppid(appid);
        game.setPlaytime_2weeks(gog.getPlaytime_2weeks());
        game.setPlaytime_forever(gog.getPlaytime_forever());
        game.setPlaytime_windows_forever(gog.getPlaytime_windows_forever());
        game.setName(gog.getName());
        game.setBanner_url("https://cdn.akamai.steamstatic.com/steam/apps/" +
                appid + "/" +
                "header.jpg");
        game.setImg_icon_url("https://media.steampowered.com/steamcommunity/public/images/apps/" +
                appid + "/" +
                gog.getImg_icon_url() + ".jpg");
        game.setRtime_last_played(lastPlayed);
        game.setPlaytime_disconnected(gog.getPlaytime_disconnected());
        return game;
    }
}
