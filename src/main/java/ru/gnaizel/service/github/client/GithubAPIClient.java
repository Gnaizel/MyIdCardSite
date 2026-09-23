package ru.gnaizel.service.github.client;

import com.fasterxml.jackson.databind.JsonNode;
import ru.gnaizel.dto.github.LineStatsDto;
import ru.gnaizel.model.github.ContributionsCollection;

import java.util.Collection;
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

    /**
     * Описание, язык и звёзды репозиториев — для карточек звёзд, форков
     * и новых репозиториев: сами события GitHub их не несут. Ключ — «владелец/имя».
     */
    Map<String, RepoInfo> describeRepos(Collection<String> repos);

    /** Пуш из ленты событий: куда, какой коммит стал головой, какой был до него. */
    record Push(String repo, String head, String before) {
    }

    record Commit(String sha, String message) {
    }

    /**
     * @param commits       коммиты пуша, от свежего к старому
     * @param complete      нашлось ли в истории начало пуша. Нет (force push,
     *                      огромный пуш) — в commits только голова, а сколько
     *                      коммитов было на самом деле, неизвестно
     * @param defaultBranch главная ветка репозитория — чтобы подписывать
     *                      ветку только у пушей мимо неё
     */
    record PushInfo(List<Commit> commits, boolean complete, String defaultBranch) {
    }

    record RepoInfo(String description, String language, String languageColor, int stars) {
    }
}
