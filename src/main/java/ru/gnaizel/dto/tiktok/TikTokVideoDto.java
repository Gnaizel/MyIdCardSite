package ru.gnaizel.dto.tiktok;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Одно видео из репостов: обложка, подпись и куда вести по клику. */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class TikTokVideoDto {
    private String id;
    private String url;

    /* Обложка лежит на подписанном адресе CDN и живёт около двух суток —
       поэтому список нельзя держать в кэше дольше, иначе картинки отвалятся
       раньше, чем мы сходим за новыми. */
    private String cover;

    private String description;
    private String author;
}
