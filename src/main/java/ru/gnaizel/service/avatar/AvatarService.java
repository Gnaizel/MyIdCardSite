package ru.gnaizel.service.avatar;

/**
 * Аватарка на карточке. Лежит не в jar, а в каталоге на диске: иначе её
 * нельзя было бы сменить, не пересобрав образ.
 */
public interface AvatarService {
    /** Приводит присланное фото к квадрату нужного размера и делает его текущим. */
    void replace(byte[] image);

    /** Возвращает ту аватарку, что лежала в сборке. */
    void reset();

    /** Что отдавать по запросу: байты и тег версии для кэша браузера. */
    Avatar current();

    record Avatar(byte[] bytes, String etag) {
    }
}
