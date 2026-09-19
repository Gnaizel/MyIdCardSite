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
| `STEAM_ID` | тот же блок игр | [steamid.io](https://steamid.io) — нужен SteamID64 |
| `GITHUB_TOKEN` | календарь коммитов: `/github` | [github.com/settings/tokens](https://github.com/settings/tokens) |
| `GUESTBOOK_ADMIN_KEY` | модерация гостевой книги | придумай сам |
| `TELEGRAM_BOT_TOKEN` | бот шлёт в телеграм всё, что пишут в книгу | [@BotFather](https://t.me/BotFather) |
| `TELEGRAM_CHAT_ID` | тот же бот: кому слать | см. ниже |

### Last.fm

Форму заполняешь чем угодно осмысленным, callback URL можно оставить пустым.
После отправки нужен **API key** — длинная строка из букв и цифр. Secret не нужен.

### Steam

Страница ключа попросит указать домен — впиши `localhost`, для личного
использования этого хватает. Ключ выдаётся сразу.

`STEAM_ID` — это не ник, а число из 17 цифр. Вставь на steamid.io ссылку
на свой профиль и возьми поле **steamID64**. Профиль и раздел «Игры» должны
быть публичными, иначе Steam отдаст пустой список даже с верным ключом.

### GitHub

**Settings → Developer settings → Personal access tokens → Tokens (classic) →
Generate new token (classic)**. Из галочек нужна только **`read:user`** —
она даёт доступ к статистике календаря контрибуций, включая приватные репозитории.
Наружу по `/github` уходят только числа: ни названий репозиториев, ни коммитов.

Токен показывается один раз — скопируй сразу.

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

**Приложение не стартует с `NumberFormatException`** — в `.env` пустой
`STEAM_ID`. Поставь `0`, если своего пока нет.

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
