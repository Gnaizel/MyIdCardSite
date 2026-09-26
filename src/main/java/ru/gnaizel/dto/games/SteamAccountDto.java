package ru.gnaizel.dto.games;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Один из аккаунтов Steam для блока ссылок: как он называется, как выглядит
 * и куда вести. Всё берётся из профиля, который Steam и так отдаёт раз
 * в минуту ради «во что играет сейчас».
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class SteamAccountDto {
    private String steamId;
    private String name;
    private String avatarUrl;
    private String profileUrl;
}
