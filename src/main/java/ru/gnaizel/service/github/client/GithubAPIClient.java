package ru.gnaizel.service.github.client;

import com.fasterxml.jackson.databind.JsonNode;
import ru.gnaizel.dto.github.LineStatsDto;
import ru.gnaizel.model.github.ContributionsCollection;

import java.util.List;
import java.util.Map;

public interface GithubAPIClient {
    ContributionsCollection getContributions();

    LineStatsDto getLineStats();

    /**
     * Публичные события профиля за последние недели: пуши, звёзды, новые
     * репозитории. Приватные сюда не попадают вовсе — это другая ручка.
     */
    JsonNode getPublicEvents();

    /**
     * Что было в каждом пуше. Сами события GitHub больше не несут ни
     * коммитов, ни их числа — только голову пуша и что было до неё, так
     * что остальное достаётся из истории. Ключ ответа — sha головы.
     */
    Map<String, PushInfo> describePushes(List<Push> pushes);

    /** Пуш из ленты событий: куда, какой коммит стал головой, какой был до него. */
    record Push(String repo, String head, String before) {
    }

    /**
     * @param commits       сколько коммитов принёс пуш; null — начала пуша
     *                      в истории не нашлось (force push, огромный пуш)
     * @param defaultBranch главная ветка репозитория — чтобы подписывать
     *                      ветку только у пушей мимо неё
     */
    record PushInfo(String headline, Integer commits, String defaultBranch) {
    }
}
