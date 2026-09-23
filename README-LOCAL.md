# Запуск сайта

## Коротко

```powershell
copy .env.example .env    # один раз, потом впиши ключи
.\run-local.ps1
```

Сайт откроется на **http://localhost:8080**. Остановить — `Ctrl+C`.

Нужны Docker Desktop (для базы), JDK 21+ и Maven. Всё это уже стоит.

Скрипт сам поднимает базу, собирает jar и стартует приложение.

## Ключи

Все ключи лежат в `.env` в корне проекта. Этот файл в `.gitignore`,
так что в репозиторий он не уедет. Без ключа соответствующий блок на
странице просто напишет, что данные недоступны — сайт при этом работает.

| Переменная | Что открывает | Где взять |
|---|---|---|
| `LASTFM_API_TOKEN` | музыка: `/tracks`, `/rotation` | [last.fm/api/account/create](https://www.last.fm/api/account/create) |
| `STEAM_API_TOKEN` | игры: `/games`, `/games-total-hours` | [steamcommunity.com/dev/apikey](https://steamcommunity.com/dev/apikey) |
| `STEAM_IDS` | тот же блок игр, можно несколько аккаунтов | [steamid.io](https://steamid.io) — нужен SteamID64 |
| `GITHUB_TOKEN` | календарь коммитов: `/github` | [github.com/settings/tokens](https://github.com/settings/tokens) |
| `YOUTUBE_CLIENT_ID`, `YOUTUBE_CLIENT_SECRET`, `YOUTUBE_REFRESH_TOKEN` | лайки YouTube в ленте `[ log ]` | см. ниже |
| `FORTNITE_API_KEY` | часы и статистика Fortnite в блоке игр | [dash.fortnite-api.com](https://dash.fortnite-api.com/) |
| `FORTNITE_NAME` | тот же блок: ник в Epic | свой ник |
| `GUESTBOOK_ADMIN_KEY` | модерация гостевой книги | придумай сам |
| `TELEGRAM_BOT_TOKEN` | бот шлёт в телеграм всё, что пишут в книгу | [@BotFather](https://t.me/BotFather) |
| `TELEGRAM_CHAT_ID` | тот же бот: кому слать | см. ниже |

### Last.fm

Форму заполняешь чем угодно осмысленным, callback URL можно оставить пустым.
После отправки нужен **API key** — длинная строка из букв и цифр. Secret не нужен.

### Steam

Страница ключа попросит указать домен — впиши `localhost`, для личного
использования этого хватает. Ключ выдаётся сразу.

`STEAM_IDS` — это не ники, а числа из 17 цифр. Вставь на steamid.io ссылку
на свой профиль и возьми поле **steamID64**. Профиль и раздел «Игры» должны
быть публичными, иначе Steam отдаст пустой список даже с верным ключом.

Аккаунтов можно перечислить сколько угодно через запятую:

```
STEAM_IDS=76561198000000001,76561198000000002
```

Часы складываются, библиотеки сшиваются по игре: одна и та же игра на двух
аккаунтах даст сумму часов, а не два пункта в списке. Ключ нужен один — он
выдаётся разработчику, а не профилю, и работает с любым публичным профилем.
Аккаунт с закрытым профилем просто не попадёт в сумму, в логе будет строка
`STEAM: аккаунт ... не отдал ничего`.

### Fortnite

У Epic нет открытого API для библиотеки: на их форуме сотрудник Epic ответил
прямо — «we do not offer or expose an API for these specific items». Списка
купленных игр и часов по ним получить неоткуда.

Но статистика по отдельной игре доступна через
[fortnite-api.com](https://fortnite-api.com/). Ключ бесплатный, берётся на
[dash.fortnite-api.com](https://dash.fortnite-api.com/). Fortnite встаёт
в общий список игр наравне со Steam: часы, дата последнего запуска, общая
сумма часов.

**Статистика должна быть открыта в самой игре**: Career → настройки
приватности → показывать в таблице лидеров. Иначе сервис отвечает отказом
даже с верным ключом — чужую закрытую статистику он не отдаёт. В логе это
видно строкой `FORTNITE: статистика закрыта настройками профиля`.

Картинку игры задают `FORTNITE_ICON` и `FORTNITE_BANNER`: у Steam они
берутся по appid, а у Fortnite его нет.

### GitHub

**Settings → Developer settings → Personal access tokens → Tokens (classic) →
Generate new token (classic)**. Из галочек нужна только **`read:user`** —
она даёт доступ к статистике календаря контрибуций, включая приватные репозитории.
Наружу по `/github` уходят только числа: ни названий репозиториев, ни коммитов.

Токен показывается один раз — скопируй сразу.

### YouTube

Лента `[ log ]` показывает лайкнутые видео. Историю просмотров YouTube API
не отдаёт никому, а лайки — только с разрешения владельца аккаунта, поэтому
здесь не ключ, а OAuth. Делается один раз:

1. [console.cloud.google.com](https://console.cloud.google.com/) → создай проект →
   **APIs & Services → Library** → включи **YouTube Data API v3**.
2. **OAuth consent screen**: тип **External**, название любое, почта своя.
   В **Test users** добавь свой Google-аккаунт. В конце нажми **Publish app**,
   чтобы статус стал **In production**: в режиме Testing разрешение умирает
   через 7 дней. Проверку Google проходить не нужно — при входе он покажет
   «приложение не проверено», жми «Дополнительно → перейти».
3. **Credentials → Create credentials → OAuth client ID**, тип **Web application**.
   В **Authorized redirect URIs** впиши `https://developers.google.com/oauthplayground`.
   Получишь **Client ID** и **Client secret**.
4. Открой [OAuth 2.0 Playground](https://developers.google.com/oauthplayground):
   шестерёнка справа сверху → **Use your own OAuth credentials** → вставь
   Client ID и secret. Слева в поле под списком впиши scope
   `https://www.googleapis.com/auth/youtube.readonly` → **Authorize APIs** →
   войди аккаунтом, на котором ставишь лайки → **Exchange authorization code
   for tokens**. Скопируй **Refresh token**.
5. Впиши в `.env` `YOUTUBE_CLIENT_ID`, `YOUTUBE_CLIENT_SECRET` и `YOUTUBE_REFRESH_TOKEN`.

Пока хоть одно из трёх пустое, YouTube в ленте просто нет. Если разрешение
отзовут (сменил пароль, убрал доступ в настройках аккаунта Google), в логе
появится `LOG: youtube не ответил: 400 ... invalid_grant` — повтори шаг 4.

### Телеграм-бот

Бот пересылает в личку всё, что пишут в «blackmail box».

`TELEGRAM_BOT_TOKEN` — у [@BotFather](https://t.me/BotFather): `/newbot`,
дальше он выдаст строку вида `123456:AA...`.

`TELEGRAM_CHAT_ID` — это не username, а число. Напиши своему боту любое
сообщение (иначе он не имеет права тебе писать первым) и открой

```
https://api.telegram.org/bot<ТОКЕН>/getUpdates
```

Нужное число лежит в `result[0].message.chat.id`.

Пока любая из двух переменных пустая, уведомления не уходят — сообщения
всё так же копятся в базе и видны через `GET /guestbook/all`. В логах на
старте видно, включено оно или нет: строка `TELEGRAM: уведомления ...`.

### Ключ гостевой книги

Любая длинная случайная строка. С ней работают `GET /guestbook/all`,
`DELETE /guestbook/{id}` и `POST /guestbook/{id}/restore` — ключ передаётся
заголовком `X-Admin-Key`. Пока строка пустая, эти ручки отвечают 401.

## Если что-то не так

**`docker не ответил`** — не запущен Docker Desktop.

**Порт 8080 занят** — посмотреть, кем: `netstat -ano | findstr :8080`.

**База сбойнула** — пересоздать с нуля:
`docker compose down -v; docker compose up -d visitor-db`.

## Прод

Всё про выкладку на сервер, https и сертификаты — в
[README-DEPLOY.md](README-DEPLOY.md). Коротко: на сервере

```bash
cp .env.example .env   # вписать SITE_DOMAIN, ACME_EMAIL, POSTGRES_PASSWORD
docker compose up -d --build
```

Впереди приложения встаёт Caddy: он сам берёт сертификат Let's Encrypt,
сам его продлевает и сам уводит весь http на https. Собирать jar заранее
не нужно — сборка идёт внутри образа.

Локально compose нужен только ради базы (`run-local.ps1` поднимает её сам),
поэтому `docker compose up -d` целиком на своей машине запускать смысла нет:
Caddy тогда выпишет сертификат на `localhost`.
