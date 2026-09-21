package ru.gnaizel.dto.tiktok;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Один репост. В TikTok их два вида, и выглядят они по-разному:
 * обычное видео и фото-пост — несколько картинок, которые листают вбок,
 * со своей звуковой дорожкой.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class TikTokVideoDto {
    private String id;

    /** Страница поста в TikTok — туда уходят по клику на автора. */
    private String url;

    /* Обложка лежит на подписанном адресе CDN и живёт около двух суток —
       поэтому список нельзя держать в кэше дольше, иначе картинки отвалятся
       раньше, чем мы сходим за новыми. */
    private String cover;

    private String description;
    private String author;

    /** Откуда странице брать видео: наша ручка, а не адрес TikTok. Пусто у фото-постов. */
    private String videoUrl;

    /** Кадры фото-поста по порядку. Пусто у обычных видео. */
    private List<String> images;

    /** Звуковая дорожка фото-поста, тоже через нашу ручку. */
    private String audioUrl;

    /** Что играет в фото-посте — подписать под кадрами. */
    private String musicTitle;

    /* Когда автор выложил видео, секунды эпохи. Это не время репоста: его
       TikTok не отдаёт вовсе, и считать его самим — выдумка, которую легко
       принять за факт. Здесь честно чужое время и подпись posted.

       Возраст считает страница, а не мы: список лежит в кэше час, и готовая
       строка успела бы состариться на этот час прямо на экране. */
    private long createdAt;

    /* Настоящие адреса наружу не отдаём. Они подписаны, живут двое суток
       и всё равно бесполезны браузеру: TikTok отвечает на них только при
       Referer со своего домена, а страница пришлёт наш. */
    @JsonIgnore
    private String playAddr;

    @JsonIgnore
    private String musicAddr;
}
