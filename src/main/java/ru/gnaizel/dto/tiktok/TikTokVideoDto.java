package ru.gnaizel.dto.tiktok;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Одно видео из репостов: обложка, подпись и куда вести по клику. */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class TikTokVideoDto {
    private String id;

    /** Страница видео в TikTok — туда уходят по клику на автора. */
    private String url;

    /* Обложка лежит на подписанном адресе CDN и живёт около двух суток —
       поэтому список нельзя держать в кэше дольше, иначе картинки отвалятся
       раньше, чем мы сходим за новыми. */
    private String cover;

    private String description;
    private String author;

    /** Откуда странице брать само видео: наша ручка, а не адрес TikTok. */
    private String videoUrl;

    /* Настоящий адрес видео наружу не отдаём. Он подписан, живёт двое суток
       и всё равно бесполезен браузеру: TikTok отвечает на него только при
       Referer со своего домена, а страница пришлёт наш. */
    @JsonIgnore
    private String playAddr;
}
