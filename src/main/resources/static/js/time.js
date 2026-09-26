/* Страница открывается сверху, а не там, где её закрыли в прошлый раз.
   Это одна длинная визитка: восстановленная прокрутка высаживает посреди
   чужого блока, и выглядит это как сбой, а не как забота. */
if ('scrollRestoration' in history) {
    history.scrollRestoration = 'manual';
}

/* Названия треков и игр приходят извне и попадают в innerHTML — экранируем. */
function esc(value) {
    return String(value ?? '').replace(/[&<>"']/g, ch => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[ch]);
}

function reducedMotion() {
    return window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/* Перезапуск CSS-анимации: снять класс и вернуть его в том же кадре
   нельзя — браузер склеит это в «ничего не поменялось». */
function replay(el, cls) {
    el.classList.remove(cls);
    void el.offsetWidth;
    el.classList.add(cls);
}

/* Меняет текст, только если он правда другой. С animate новое значение
   коротко вспыхивает акцентом: видно, что обновилось, а вёрстка не
   сдвигается — анимируется только цвет. */
function setText(el, text, animate) {
    if (!el) return;
    const value = String(text ?? '');
    if (el.textContent === value) return;
    el.textContent = value;
    if (animate) replay(el, 'bump');
}

function updateTime() {
    fetch('/time')
        .then(response => response.text())
        .then(data => {
            const serverTime = new Date(data);
            document.getElementById('time').textContent = serverTime.toLocaleTimeString();
        })
        .catch(error => {
            console.error('Error fetching time:', error);
            document.getElementById('time').textContent = 'Error loading time';
        });
}

updateTime();
setInterval(updateTime, 1000);

function updateAge() {

    fetch('/old')
        .then(res => res.text())
        .then(data => {
            document.getElementById('old').textContent = data;
        })
        .catch(err => console.error("Ошибка при запросе возраста:", err));
}

updateAge();
setInterval(updateAge, 1000);

/* Фразы в футере. Меняются по кругу тем же затуханием, которым раньше
   перебирались слова статуса. Список правится здесь и больше нигде. */
const QUOTES = [
    "Don't forget about me.",
    "code. sleep. repeat.",
    "Seven minutes. That's all it takes to begin anew.",
    "Seven minutes. That's all it took.",
    "stay curious"
];
const QUOTE_EVERY = 6000;

function initQuotes() {
    const el = document.getElementById('quote-text');
    if (!el) return;

    // стартуем со случайной, чтобы у каждого захода на сайт был свой порядок
    let i = Math.floor(Math.random() * QUOTES.length);
    el.textContent = QUOTES[i];
    if (QUOTES.length < 2) return;

    setInterval(() => {
        i = (i + 1) % QUOTES.length;
        el.classList.add('swap');
        // текст подменяем в середине затухания, иначе видно подмену
        setTimeout(() => {
            el.textContent = QUOTES[i];
            el.classList.remove('swap');
        }, 250);
    }, QUOTE_EVERY);
}

initQuotes();

/* Лента last.fm обновляется сама. Два правила: элементы переиспользуются, а не
   создаются заново (иначе на каждом опросе перезагружались бы обложки и
   сбрасывалась прокрутка ленты), и в фоновой вкладке опрос останавливается —
   незачем годами дёргать API ради страницы, на которую никто не смотрит. */
const TRACK_POLL_PLAYING = 10000;
const TRACK_POLL_IDLE = 30000;
const TRACK_FALLBACK_COVER = '/image/unknown-track.JPG';

let trackTimer = null;
let trackPlaying = false;
let trackHeadKey = null;

function trackKey(track) {
    return (track.name ?? '') + '\u0000' + (track.artist ?? '');
}

function makeTrack() {
    const el = document.createElement('div');
    el.className = 'track';
    el.innerHTML =
        '<img alt="" loading="lazy">' +
        '<div class="track-info">' +
        '<div class="track-name"></div>' +
        '<div class="track-artist"></div>' +
        '</div>' +
        '<div class="track-time-later"></div>' +
        '<span class="eq" role="img" aria-label="now playing"><i></i><i></i><i></i></span>';
    return el;
}

/* Всё кладём через textContent, поэтому названия треков экранировать не нужно:
   разметкой они стать уже не могут. */
function setLine(el, text) {
    const value = text ?? '';
    if (el.textContent !== value) {
        el.textContent = value;
        el.title = value;
    }
}

function paintTrack(el, track) {
    const cover = track.imageUrl && track.imageUrl.trim() ? track.imageUrl : TRACK_FALLBACK_COVER;
    const img = el.querySelector('img');
    // src трогаем только при смене, иначе обложка моргает на каждом опросе
    if (img.getAttribute('src') !== cover) {
        img.setAttribute('src', cover);
    }

    setLine(el.querySelector('.track-name'), track.name);
    setLine(el.querySelector('.track-artist'), track.artist);
    setLine(el.querySelector('.track-time-later'), track.timeFromLastListen);
    el.classList.toggle('playing', Boolean(track.attr));
}

function displayTracks(tracks) {
    const list = document.getElementById('track-list');
    if (!list) return;

    const rail = list.closest('.scroll-rail');
    const scrollLeft = rail ? rail.scrollLeft : 0;

    const existing = Array.from(list.children);
    tracks.forEach((track, i) => {
        paintTrack(existing[i] || list.appendChild(makeTrack()), track);
    });
    for (let i = tracks.length; i < existing.length; i++) {
        existing[i].remove();
    }

    // подсвечиваем только смену первого трека и только не при первой отрисовке
    const headKey = tracks.length ? trackKey(tracks[0]) : null;
    const head = list.firstElementChild;
    if (trackHeadKey !== null && headKey && headKey !== trackHeadKey && head) {
        head.classList.remove('is-new');
        void head.offsetWidth;  // перезапуск анимации: без этого класс вернётся молча
        head.classList.add('is-new');
    }
    trackHeadKey = headKey;

    if (rail) rail.scrollLeft = scrollLeft;
}

async function fetchTracks() {
    try {
        const response = await fetch('/tracks');
        if (!response.ok) throw new Error('HTTP ' + response.status);
        const tracks = (await response.json()).slice(0, 11);
        trackPlaying = tracks.some(track => track.attr);
        displayTracks(tracks);
    } catch (err) {
        console.error('Ошибка при получении треков:', err);
    }
}

function scheduleTracks() {
    clearTimeout(trackTimer);
    if (document.hidden) return;
    // пока играет — чаще, в тишине незачем
    trackTimer = setTimeout(() => {
        fetchTracks().then(scheduleTracks);
    }, trackPlaying ? TRACK_POLL_PLAYING : TRACK_POLL_IDLE);
}

document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
        clearTimeout(trackTimer);
        return;
    }
    // вернулись на вкладку — показываем свежее сразу, а не через полминуты
    fetchTracks().then(scheduleTracks);
});

fetchTracks().then(scheduleTracks);

/* Горизонтальные ленты: колесо мыши, стрелки и растворение края с той стороны,
   куда ещё можно листать. */
const FADE = 48;

function initScrollers() {
    document.querySelectorAll('.scroller').forEach(scroller => {
        const rail = scroller.querySelector('.scroll-rail');
        if (!rail) return;

        const sync = () => {
            const max = rail.scrollWidth - rail.clientWidth;
            const left = rail.scrollLeft > 1;
            const right = rail.scrollLeft < max - 1;

            scroller.classList.toggle('can-left', left);
            scroller.classList.toggle('can-right', right);
            rail.style.setProperty('--fade-l', left ? FADE + 'px' : '0px');
            rail.style.setProperty('--fade-r', right ? FADE + 'px' : '0px');
        };

        rail.addEventListener('scroll', sync, { passive: true });
        window.addEventListener('resize', sync);
        new MutationObserver(sync).observe(rail, { childList: true, subtree: true });

        rail.addEventListener('wheel', e => {
            if (e.deltaY === 0) return;
            e.preventDefault();
            rail.scrollLeft += e.deltaY;
        }, { passive: false });

        scroller.querySelectorAll('[data-scroll]').forEach(btn => {
            btn.addEventListener('click', () => {
                const dir = Number(btn.dataset.scroll);
                rail.scrollBy({ left: dir * rail.clientWidth * 0.8, behavior: 'smooth' });
            });
        });

        sync();
    });
}

initScrollers();

/* Календарь контрибьюшенов. С бэка приходят только числа: ни репозиториев,
   ни сообщений коммитов там нет, приватное остаётся приватным. */
const GH_MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
                   'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function ghCompact(value) {
    const num = Number(value) || 0;
    if (num >= 1000000) return (num / 1000000).toFixed(1).replace(/\.0$/, '') + 'M';
    if (num >= 1000) return (num / 1000).toFixed(1).replace(/\.0$/, '') + 'k';
    return String(num);
}

let ghDaysKey = null;

function displayGithub(data) {
    const set = (id, text) => setText(document.getElementById(id), text, true);

    set('gh-total', ghCompact(data.totalContributions));
    set('gh-commits', ghCompact(data.commits));
    set('gh-repos', String(data.repositories ?? 0));
    set('gh-streak', (data.bestStreak ?? 0) + 'd');

    // строки считаются раз в сутки и по всем репозиториям сразу; пока GitHub
    // пересчитывает свою статистику, их просто нет — тогда прочерк
    if (data.linesAvailable) {
        set('gh-added', '+' + ghCompact(data.linesAdded));
        set('gh-removed', '\u2212' + ghCompact(data.linesRemoved));
    } else {
        set('gh-added', '--');
        set('gh-removed', '');
    }

    const heatmap = document.getElementById('gh-heatmap');
    const months = document.getElementById('gh-months');
    if (!heatmap || !months) return;

    const days = Array.isArray(data.days) ? data.days : [];
    // календарь не изменился — не пересобираем четыре сотни клеток зря
    const daysKey = JSON.stringify(days);
    if (daysKey === ghDaysKey) return;
    ghDaysKey = daysKey;

    heatmap.innerHTML = '';
    months.innerHTML = '';
    if (!days.length) return;

    /* Первая неделя календаря почти всегда неполная, поэтому колонку и строку
       ставим явно по дате: иначе весь календарь съезжает по вертикали. */
    let column = 1;
    let lastMonth = -1;

    days.forEach((day, index) => {
        const date = new Date(day.date + 'T00:00:00Z');
        const weekday = date.getUTCDay();          // 0 — воскресенье, как у GitHub
        if (index > 0 && weekday === 0) column++;

        const cell = document.createElement('span');
        cell.className = 'gh-cell';
        cell.dataset.level = String(day.level ?? 0);
        cell.style.gridRow = String(weekday + 1);
        cell.style.gridColumn = String(column);
        cell.title = day.count + (day.count === 1 ? ' contribution' : ' contributions')
            + ' on ' + day.date;
        heatmap.appendChild(cell);

        const month = date.getUTCMonth();
        // подпись ставим на первую колонку месяца и не чаще раза в месяц
        if (month !== lastMonth && date.getUTCDate() <= 7) {
            const label = document.createElement('span');
            label.textContent = GH_MONTHS[month];
            label.style.gridColumn = String(column);
            months.appendChild(label);
            lastMonth = month;
        }
    });
}

function fetchGithub() {
    fetch('/github')
        .then(res => {
            if (!res.ok) throw new Error('HTTP ' + res.status);
            return res.json();
        })
        .then(displayGithub)
        .catch(err => {
            console.error('Ошибка загрузки github:', err);
            const heatmap = document.getElementById('gh-heatmap');
            if (heatmap && !heatmap.children.length) {
                heatmap.innerHTML = '<p class="gh-empty">github unavailable</p>';
            }
        });
}

fetchGithub();
setInterval(fetchGithub, 900000);  // бэк всё равно кеширует на 15 минут

/* Топ артистов за неделю. Доля считается внутри топа — last.fm не отдаёт
   общее число прослушиваний за период, поэтому подпись честно говорит across N. */
function displayRotation(data) {
    const list = document.getElementById('rotation-list');
    const sub = document.getElementById('rotation-sub');
    const note = document.getElementById('rotation-note');
    if (!list) return;

    const artists = Array.isArray(data.artists) ? data.artists : [];
    if (!artists.length) {
        list.innerHTML = '';
        if (note) note.textContent = '';
        return;
    }

    const max = Math.max(...artists.map(artist => artist.plays || 0), 1);

    /* Строки переиспользуются: при пересборке полоски каждый раз рисовались
       заново с нуля. Теперь новые строки вырастают из нуля один раз,
       а на опросе полоски плавно доезжают до новой длины. */
    const rows = Array.from(list.children);
    const firstPaint = rows.length === 0;
    for (let i = rows.length; i < artists.length; i++) {
        const li = document.createElement('li');
        li.innerHTML = '<span class="rot-name"></span>'
            + '<span class="rot-bar"><i style="width: 0"></i></span>'
            + '<span class="rot-plays"></span>';
        list.appendChild(li);
        rows.push(li);
    }
    rows.slice(artists.length).forEach(li => li.remove());
    // без замера до смены ширины браузер не увидит нуля и не анимирует рост
    void list.offsetWidth;

    artists.forEach((artist, i) => {
        const li = rows[i];
        const name = li.querySelector('.rot-name');
        setText(name, artist.name, false);
        name.title = artist.name ?? '';
        li.querySelector('.rot-bar i').style.width = Math.round((artist.plays / max) * 100) + '%';
        setText(li.querySelector('.rot-plays'), artist.plays, !firstPaint);
    });

    if (sub) {
        setText(sub, data.totalPlays + ' plays across '
            + data.artistCount + ' artists · last 12 months', false);
    }
    if (note) {
        const top = artists[0];
        const html = `<b>${esc(top.name)}</b> is ${esc(top.share)}% of my year`;
        if (note.innerHTML !== html) note.innerHTML = html;
    }
}

function fetchRotation() {
    fetch('/rotation')
        .then(res => {
            if (!res.ok) throw new Error('HTTP ' + res.status);
            return res.json();
        })
        .then(displayRotation)
        .catch(err => console.error('Ошибка загрузки ротации:', err));
}

fetchRotation();
setInterval(fetchRotation, 300000);

/* Блок игр опрашивается раз в полминуты, и раньше на каждом опросе он
   собирался заново: баннер удалялся и проявлялся с нуля, список и часы
   на мгновение пустели. Со стороны это выглядело как мигание без причины.
   Теперь разметка живёт всё время, а опрос меняет в ней только то, что
   правда поменялось, — и именно это плавно подсвечивается. */
let gamesByKey = new Map();
let selectedGameKey = null;   // выбрана кликом; null — показываем самую свежую
let shownGameKey = null;

/* Список справа в двух видах: recent — по последнему запуску (основной)
   и hours — по наигранным часам. Каждый грузится страницами и только
   когда нужен: кто зашёл посмотреть, не качает ни второй вид, ни хвост
   списка — только первую страницу основного, как и раньше. */
const GAME_PAGE = 12;
const GAME_POLL_MAX = 60;   // больше строк за раз сервер не отдаёт
const GAME_ICON_FALLBACK = '/image/game-icon.jpg';
const gameLists = {
    recent: { items: [], loaded: false, done: false, loading: false },
    hours:  { items: [], loaded: false, done: false, loading: false },
};
let gameMode = 'recent';

/* У Fortnite appid нет (0), так что ключ по одному appid склеил бы любые
   две игры без него. */
function gameKey(game) {
    return game.appid ? String(game.appid) : 'name:' + (game.name ?? '');
}

function fetchGames(mode, offset, limit) {
    return fetch(`/games?sort=${mode}&offset=${offset}&limit=${limit}`)
        .then(rep => {
            if (!rep.ok) throw new Error('HTTP ' + rep.status);
            return rep.json();
        });
}

function rememberGames(list) {
    list.forEach(game => gamesByKey.set(gameKey(game), game));
}

/* Опрос освежает уже загруженное, но не больше GAME_POLL_MAX строк.
   Свежая голова встаёт на место старой, а хвост, догруженный прокруткой,
   остаётся — без тех игр, что за это время переехали наверх. */
function refreshGames(mode) {
    const list = gameLists[mode];
    const limit = Math.min(Math.max(list.items.length, GAME_PAGE), GAME_POLL_MAX);
    const covered = list.items.length <= limit;
    return fetchGames(mode, 0, limit).then(data => {
        const keys = new Set(data.map(gameKey));
        list.items = data.concat(list.items.slice(data.length).filter(game => !keys.has(gameKey(game))));
        list.loaded = true;
        if (covered) list.done = data.length < limit;
        rememberGames(data);
        if (gameMode === mode) displayGameLib(list.items);
        return data;
    });
}

function displayMyGameLib() {
    refreshGames('recent')
        .then(data => {
            /* Самую свежую игру больше не выбрасываем из списка: без неё
               нумерация начиналась со второй, и «01» стояло не у той игры,
               что показана крупно слева. */
            gamePlaying = data.some(game => game.playingNow);
            sessionStartedAt = data.find(game => game.playingNow && game.sessionStartedAt)?.sessionStartedAt ?? null;
            sessionSeenAt = Date.now();
            /* Выбранная кликом игра остаётся выбранной и после опроса:
               раньше каждые полминуты крупный блок сам перескакивал
               обратно на первую. Без выбора крупно — самая свежая, в каком
               бы виде ни был список справа. */
            showGame((selectedGameKey && gamesByKey.get(selectedGameKey)) || data[0]);
            /* Общие часы одни на весь блок и от выбранной игры не зависят,
               поэтому запрашиваются вместе со списком, а не на каждый клик. */
            displayTotalHours();
        })
        .catch(err => {
            console.error("Ошибка при получении или отображении игр:", err);

            /* Одна неудачная попытка не должна стирать уже нарисованный
               список: ошибку показываем, только если показывать больше нечего. */
            const gameLib = document.getElementById('game-lib');
            if (gameLib && !gameLib.querySelector('.game')) {
                gameLib.innerHTML = '<p>Ошибка загрузки игр.</p>';
            }
        });

    // второй вид освежаем, только пока его смотрят
    if (gameMode === 'hours' && gameLists.hours.loaded) {
        refreshGames('hours').catch(err => console.error('Ошибка при обновлении списка игр:', err));
    }
}

/* Следующая страница — когда список докрутили почти до конца. */
function loadMoreGames() {
    const mode = gameMode;
    const list = gameLists[mode];
    if (list.loading || list.done) return;
    list.loading = true;
    fetchGames(mode, list.items.length, GAME_PAGE)
        .then(data => {
            const keys = new Set(list.items.map(gameKey));
            list.items = list.items.concat(data.filter(game => !keys.has(gameKey(game))));
            list.loaded = true;
            list.done = data.length < GAME_PAGE;
            rememberGames(data);
            if (gameMode === mode) {
                displayGameLib(list.items);
                markSelected(shownGameKey);
            }
        })
        .catch(err => console.error('Ошибка при подгрузке игр:', err))
        .finally(() => {
            list.loading = false;
            if (gameMode === mode) document.getElementById('game-lib')?.classList.remove('is-loading');
        });
}

/* Второй вид при первом выборе догружается, дальше переключение
   мгновенное: оба списка уже в памяти. */
function setGameMode(mode) {
    if (mode === gameMode || !gameLists[mode]) return;
    gameMode = mode;
    document.querySelectorAll('#game-sort .game-sort-btn').forEach(btn =>
        btn.setAttribute('aria-pressed', String(btn.dataset.mode === mode)));

    const gameLib = document.getElementById('game-lib');
    if (!gameLib) return;
    gameLib.scrollTop = 0;
    const list = gameLists[mode];
    if (list.loaded) {
        // цифры справа у всех строк другие — без вспышки на каждой
        displayGameLib(list.items, true);
        markSelected(shownGameKey);
        return;
    }
    gameLib.classList.add('is-loading');
    loadMoreGames();
}

/* Справа в строке: в основном виде — сколько прошло с последнего запуска,
   в виде по часам — сами часы. */
function gameStat(game) {
    return gameMode === 'hours' ? game.playtime_forever : timeAgo(game.lastPlayedAt);
}

/* Коротко, чтобы влезло туда же, где раньше стояли часы. */
function timeAgo(seconds) {
    if (!seconds) return '—';
    const minutes = Math.max(0, Date.now() / 1000 - seconds) / 60;
    const hours = minutes / 60;
    const days = hours / 24;
    if (minutes < 1) return 'just now';
    if (hours < 1) return Math.floor(minutes) + 'm ago';
    if (days < 1) return Math.floor(hours) + 'h ago';
    if (days < 7) return Math.floor(days) + 'd ago';
    if (days < 30) return Math.floor(days / 7) + 'w ago';
    if (days < 365) return Math.floor(days / 30) + 'mo ago';
    return Math.floor(days / 365) + 'y ago';
}

(function setupGameList() {
    const gameLib = document.getElementById('game-lib');
    const sort = document.getElementById('game-sort');
    if (!gameLib || !sort) return;

    sort.addEventListener('click', event => {
        const btn = event.target.closest('.game-sort-btn');
        if (btn) setGameMode(btn.dataset.mode);
    });
    gameLib.addEventListener('scroll', () => {
        if (gameLib.scrollTop + gameLib.clientHeight >= gameLib.scrollHeight - 60) loadMoreGames();
    }, { passive: true });
})();

/* Крупный блок показывает выбранную игру, а не только самую свежую:
   по клику в списке сюда приезжает статистика любой из них. */
function showGame(game) {
    if (!game) {
        console.warn("Нет данных для отображения игры.");
        return;
    }

    const card = document.getElementById('last-game');
    if (!card) {
        console.warn("Элемент с id 'last-game' не найден.");
        return;
    }

    const key = gameKey(game);
    /* Сменилась сама игра — проявляется карточка целиком. Та же игра
       с новыми цифрами — подсвечиваются только эти цифры. */
    const switched = shownGameKey !== key;
    shownGameKey = key;

    const icon = card.querySelector('.game-head img');
    const iconUrl = game.img_icon_url || '/image/game-icon.jpg';
    if (icon && icon.getAttribute('src') !== iconUrl) {
        icon.setAttribute('src', iconUrl);
    }

    const title = card.querySelector('.last-game-title');
    setText(title, game.name, false);
    if (title) title.title = game.name ?? '';

    /* Подпись под названием есть не у всех игр — только у тех, чей appid
       перечислен у неё в разметке. */
    card.querySelectorAll('.game-note').forEach(note => {
        const appids = (note.dataset.appids || '').split(/\s+/);
        note.hidden = !appids.includes(String(game.appid));
    });

    /* Классом на блоке, а не на самом индикаторе: так же, как у трека,
       и CSS остаётся одним правилом на оба списка. */
    card.classList.toggle('playing', Boolean(game.playingNow));

    setText(document.getElementById('lg-forever'), game.playtime_forever, !switched);
    setText(document.getElementById('lg-2weeks'), game.playtime_2weeks, !switched);
    setText(document.getElementById('lg-last'), game.rtime_last_played, !switched);

    showJoin(card, game);
    setBannerImage(game.banner_url);

    if (switched) replay(card, 'is-switching');

    markSelected(key);
}

/* Ссылка на лобби — своей строкой под шапкой, а не рядом с названием:
   в шапке она отъедала ширину, и длинные названия из-за неё переносились
   на вторую строку.

   Показываем её, только когда Steam её дал: она появляется лишь у игр со
   своим лобби и лишь пока в него пускают. Сама ссылка есть всегда и только
   проявляется и гаснет, а места в вёрстке не занимает (см. CSS): раньше
   она вставлялась рывком, и текст карточки съезжал вниз.
   rel и target не нужны: steam:// открывает не вкладку, а сам клиент. */
function showJoin(card, game) {
    const row = card.querySelector('.game-join');
    const link = card.querySelector('.join');
    if (!row || !link) return;

    if (game.joinUrl) link.setAttribute('href', game.joinUrl);
    row.classList.toggle('shown', Boolean(game.joinUrl));
}

/* Подсветка в списке должна совпадать с тем, что показано крупно, иначе
   непонятно, чью статистику сейчас видишь. */
function markSelected(key) {
    document.querySelectorAll('#game-lib .game').forEach(el => {
        const active = el.dataset.key === key;
        el.classList.toggle('is-active', active);
        el.setAttribute('aria-pressed', active ? 'true' : 'false');
    });
}

function makeGameItem() {
    /* Кнопка, а не div: таб, Enter и пробел начинают работать сами,
       и не нужно городить обработчики клавиатуры руками. */
    const el = document.createElement('button');
    el.type = 'button';
    el.className = 'game';
    el.innerHTML =
        '<img alt="" loading="lazy">' +
        '<div class="game-info">' +
        '<div class="game-title"></div>' +
        '<div class="playtime-forever"></div>' +
        '</div>' +
        '<span class="live">in game</span>';

    /* Steam хранит ссылки на иконки и у тех игр, чьих картинок у него уже
       нет: без заглушки строка стояла бы с дыркой. */
    const img = el.querySelector('img');
    img.addEventListener('error', () => {
        if (!img.src.endsWith(GAME_ICON_FALLBACK)) img.src = GAME_ICON_FALLBACK;
    });

    /* Игру берём по ключу в момент клика, а не замыкаем при создании:
       плитка живёт между опросами, и замкнутые данные устарели бы. */
    el.addEventListener('click', () => {
        const game = gamesByKey.get(el.dataset.key);
        if (!game) return;
        // клик по первой в основном виде — снова «следить за самой свежей»
        const first = document.querySelector('#game-lib .game');
        selectedGameKey = gameMode === 'recent' && first === el ? null : el.dataset.key;
        showGame(game);
    });
    return el;
}

function paintGameItem(el, game, animate) {
    el.dataset.key = gameKey(game);

    const img = el.querySelector('img');
    const icon = game.img_icon_url ?? '';
    /* src трогаем только при смене, иначе иконка моргает на каждом опросе.
       Сравниваем с тем, что просили, а не с src: у битой иконки там
       заглушка, и опрос иначе каждый раз ставил бы битую ссылку заново. */
    if (img.dataset.icon !== icon) {
        img.dataset.icon = icon;
        img.setAttribute('src', icon || GAME_ICON_FALLBACK);
    }

    const title = el.querySelector('.game-title');
    setText(title, game.name, false);
    title.title = game.name ?? '';

    setText(el.querySelector('.playtime-forever'), gameStat(game), animate);
    el.classList.toggle('playing', Boolean(game.playingNow));
}

/* Плитки переиспользуются по ключу игры. Если порядок поменялся — например,
   запущенная игра поднялась наверх, — плитки доезжают до новых мест,
   а не перескакивают. */
function displayGameLib(data, quiet = false) {
    const gameLib = document.getElementById('game-lib');
    if (!gameLib) return;

    // сообщение об ошибке с прошлой попытки больше не нужно
    Array.from(gameLib.children).forEach(child => {
        if (!child.classList.contains('game')) child.remove();
    });

    const existing = new Map();
    const before = new Map();
    Array.from(gameLib.children).forEach(el => {
        existing.set(el.dataset.key, el);
        before.set(el.dataset.key, el.getBoundingClientRect().top);
    });
    const firstPaint = existing.size === 0;

    data.forEach((game, i) => {
        const key = gameKey(game);
        let el = existing.get(key);
        const fresh = !el;
        if (fresh) {
            el = makeGameItem();
        } else {
            existing.delete(key);
        }
        paintGameItem(el, game, !fresh && !quiet);
        if (gameLib.children[i] !== el) {
            gameLib.insertBefore(el, gameLib.children[i] || null);
        }
        if (fresh && !firstPaint) el.classList.add('is-new');
    });
    existing.forEach(el => el.remove());

    if (firstPaint || reducedMotion()) return;
    gameLib.querySelectorAll('.game').forEach(el => {
        const was = before.get(el.dataset.key);
        if (was === undefined) return;
        const shift = was - el.getBoundingClientRect().top;
        if (Math.abs(shift) < 1) return;
        el.animate(
            [{ transform: `translateY(${shift}px)` }, { transform: 'none' }],
            { duration: 420, easing: 'cubic-bezier(.2, .7, .2, 1)' }
        );
    });
}

/* Пока идёт игра, общие часы тикают: к числу из Steam прибавляется
   та часть сессии, которой в нём ещё нет. Её начало присылает сервер
   (см. uncountedSince). Steam обновляет часы с задержкой, и без этого
   счётчик стоял бы на месте всю игру. */
const SESSION_GRACE = 90 * 1000;        // дольше без подтверждения сессию не докручиваем
const TOTAL_HOLD = 30 * 60 * 1000;      // сколько ждём, пока Steam догонит показанное
let totalHoursBase = null;      // часы из /games-total-hours
let sessionStartedAt = null;    // с какого момента сессия не засчитана, мс Unix, из /games
let sessionSeenAt = 0;          // когда опрос последний раз ответил
let totalShown = null;          // что сейчас на одометре, в минутах
let totalHeldSince = null;      // с какого момента держим его против меньшего

function renderTotalHours() {
    const odo = document.getElementById('hours-odo');
    if (!odo || totalHoursBase === null) return;
    const now = Date.now();
    /* Докручиваем только то, что подтвердил опрос: в скрытой вкладке он
       стоит, и без этой границы счётчик тикал бы и после конца игры. */
    const until = Math.min(now, sessionSeenAt + SESSION_GRACE);
    const session = sessionStartedAt ? Math.max(0, Math.floor((until - sessionStartedAt) / 60000)) : 0;
    let minutes = Math.round(totalHoursBase * 60) + session;

    /* Назад не крутим. Игру закрыли — сессия пропадает сразу, а в часы
       Steam её добавляет с опозданием (плюс четверть часа кеша на сервере),
       и счётчик откатился бы на всю сессию, чтобы потом вернуться. Поэтому
       показанное держим, пока его не догонят. Если меньшее число продержалось
       полчаса, значит, оно и правда меньше, — тогда уступаем. */
    if (totalShown !== null && minutes < totalShown) {
        if (totalHeldSince === null) totalHeldSince = now;
        if (now - totalHeldSince < TOTAL_HOLD) minutes = totalShown;
        else totalHeldSince = null;
    } else {
        totalHeldSince = null;
    }
    totalShown = minutes;

    /* Дробь часа — это минуты: «5623 h 10 m» читается сразу,
       а «5623.17 h» приходилось пересчитывать в уме. */
    const text = Math.floor(minutes / 60) + 'h' + String(minutes % 60).padStart(2, '0') + 'm';
    setOdometer(odo, text, minutes);
}

// одометр сам не крутит одинаковое число, так что раз в секунду — дёшево
setInterval(renderTotalHours, 1000);

function displayTotalHours() {
    const odo = document.getElementById('hours-odo');
    if (!odo) return;

    fetch('games-total-hours')
        .then(resp => {
            if (!resp.ok) throw new Error('Network response was not ok');
            return resp.text();
        })
        .then(data => {
            const hours = Number(data.trim());
            if (!Number.isFinite(hours)) throw new Error('not a number: ' + data);
            totalHoursBase = hours;
            renderTotalHours();
        })
        .catch(err => {
            console.error(err);
            // уже показанное число лучше, чем «unavailable» из-за одного сбоя
            if (!odo.querySelector('.odo-drum')) {
                odo.innerHTML = '<span class="odo-wait">unavailable</span>';
                odo.setAttribute('aria-label', 'unavailable');
            }
        });
}

/* Общие часы — одометром. У каждой цифры свой барабан-десятигранник
   (см. CSS), и --turn — сколько цифр он прокрутил с нуля: угол растёт
   без предела, поэтому барабан просто крутится дальше, без перемоток
   ленты. Первый раз барабаны раскручиваются с нуля, когда счётчик
   показался на экране, — правые дольше и на лишние обороты, как у
   настоящего счётчика. Потом докручиваются только сменившиеся цифры.
   Останавливается барабан с лёгким перекатом и возвратом, как колесо
   с фиксатором. */
const ODO_OVERSHOOT = 0.14;   // на какую долю цифры барабан проскакивает перед щелчком
const ODO_WHEEL = '<span class="odo-wheel">'
    + [...Array(10).keys()].map(d => `<span class="odo-face" style="--i:${d}">${d}</span>`).join('')
    + '</span>';

function turnWheel(wheel, turn, seconds) {
    const from = Number(wheel.dataset.turn || 0);
    wheel.dataset.turn = String(turn);
    if (reducedMotion()) {
        wheel.style.setProperty('--turn', turn);
        return;
    }
    wheel.style.setProperty('--dur', seconds.toFixed(2) + 's');
    wheel.style.setProperty('--ease', 'cubic-bezier(.16, .84, .3, 1)');
    wheel.style.setProperty('--turn', turn + (turn >= from ? ODO_OVERSHOOT : -ODO_OVERSHOOT));
    wheel.addEventListener('transitionend', function settle() {
        wheel.removeEventListener('transitionend', settle);
        // пока крутился, попросили другую цифру — щёлкать будет уже она
        if (wheel.dataset.turn !== String(turn)) return;
        wheel.style.setProperty('--dur', '.2s');
        wheel.style.setProperty('--ease', 'cubic-bezier(.3, 0, .2, 1)');
        wheel.style.setProperty('--turn', turn);
    });
}

/* Раскрутка — один раз и только когда счётчик на экране: при загрузке
   он обычно ниже края, и анимацию никто бы не увидел. */
function spinUpOdometer(odo) {
    const wheels = [...odo.querySelectorAll('.odo-wheel')];
    const digits = odo.dataset.text.replace(/\D/g, '');
    odo.dataset.spun = '1';
    wheels.forEach((wheel, i) => {
        const fromRight = wheels.length - 1 - i;
        const turns = fromRight === 0 ? 3 : fromRight < 3 ? 2 : 1;
        turnWheel(wheel, 10 * turns + Number(digits[i]), 1.1 + 0.16 * i);
    });
}

function whenVisible(el, run) {
    if (!('IntersectionObserver' in window)) {
        run();
        return;
    }
    const watcher = new IntersectionObserver(entries => {
        if (!entries.some(entry => entry.isIntersecting)) return;
        watcher.disconnect();
        run();
    }, { threshold: 0.6 });
    watcher.observe(el);
}

/* text — цифры и буквы единиц, например «5623h10m»: цифры встают на
   барабаны, буквы — подписями между ними. value — то же число одним
   значением, чтобы знать, в какую сторону крутить. */
function setOdometer(odo, text, value) {
    if (odo.dataset.text === text) return;
    const before = Number(odo.dataset.value);
    odo.dataset.text = text;
    odo.dataset.value = String(value);
    odo.setAttribute('aria-label', text.replace('h', ' hours ').replace('m', ' minutes'));

    /* Сменилась форма числа (появился новый разряд) или его ещё не было —
       барабаны строятся заново, стоят на нулях и ждут раскрутки. */
    const shape = text.replace(/\d/g, '0');
    if (odo.dataset.shape !== shape) {
        odo.dataset.shape = shape;
        odo.dataset.spun = '';
        odo.innerHTML = '';
        let minor = false;   // после первой единицы — младшие разряды, они тише
        for (const ch of text) {
            if (/\d/.test(ch)) {
                const drum = document.createElement('span');
                drum.className = minor ? 'odo-drum is-minor' : 'odo-drum';
                drum.setAttribute('aria-hidden', 'true');
                drum.innerHTML = ODO_WHEEL;
                odo.appendChild(drum);
            } else {
                const unit = document.createElement('span');
                unit.className = 'odo-unit';
                unit.setAttribute('aria-hidden', 'true');
                unit.textContent = ch;
                odo.appendChild(unit);
                minor = true;
            }
        }
        whenVisible(odo, () => spinUpOdometer(odo));
        return;
    }

    // ещё не раскручен — раскрутка сама встанет на последнее число
    if (!odo.dataset.spun) return;

    const digits = text.replace(/\D/g, '');
    odo.querySelectorAll('.odo-wheel').forEach((wheel, i) => {
        const turn = Number(wheel.dataset.turn || 0);
        const current = ((turn % 10) + 10) % 10;
        const digit = Number(digits[i]);
        if (current === digit) return;
        // часы растут — крутим вперёд; упали (исключили программу) — назад
        const steps = value >= before
            ? (digit - current + 10) % 10
            : -((current - digit + 10) % 10);
        turnWheel(wheel, turn + steps, 0.55 + Math.abs(steps) * 0.06);
    });
}

/* Баннер меняется, только когда сменилась картинка. Новый сначала
   докачивается целиком и лишь потом проявляется поверх старого, а старый
   гаснет под ним: без этого между ними мелькала пустая карточка. */
let bannerWanted = null;

function setBannerImage(imageUrl) {
    const card = document.getElementById('last-game');
    if (!card) return;

    const wanted = imageUrl || '';
    if (wanted === bannerWanted) return;
    bannerWanted = wanted;

    const swap = () => {
        // пока грузилась, успели попросить другую — эта уже не нужна
        if (bannerWanted !== wanted) return;

        card.querySelectorAll('.banner-image').forEach(old => {
            if (old.classList.contains('leaving')) return;
            old.classList.add('leaving');
            old.style.opacity = '0';
            setTimeout(() => old.remove(), 900);
        });
        if (!wanted) return;

        const banner = document.createElement('div');
        banner.className = 'banner-image';
        banner.style.backgroundImage = `url("${wanted.replace(/["\\]/g, '\\$&')}")`;
        // после старых, чтобы новый проявлялся поверх, а не под ними
        card.insertBefore(banner, card.querySelector('.game-head'));
        requestAnimationFrame(() => requestAnimationFrame(() => {
            banner.style.opacity = '1';
        }));
    };

    if (!wanted) {
        swap();
        return;
    }
    const probe = new Image();
    probe.onload = swap;
    probe.onerror = swap;
    probe.src = wanted;
}

displayMyGameLib();
/* Пока играет — опрашиваем чаще, в остальное время реже: список игр
   меняется редко, а индикатор должен загораться и гаснуть без большой
   задержки. Простой — минута, а не две: игры не из Steam теперь тоже
   загораются через этот опрос, а не через отдельную строку Discord.
   Скрытая вкладка не опрашивает вовсе. */
const GAME_POLL_PLAYING = 30000;
const GAME_POLL_IDLE = 60000;
let gameTimer = null;
let gamePlaying = false;

function scheduleGames() {
    clearTimeout(gameTimer);
    if (document.hidden) return;
    gameTimer = setTimeout(() => {
        displayMyGameLib();
        scheduleGames();
    }, gamePlaying ? GAME_POLL_PLAYING : GAME_POLL_IDLE);
}

document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
        clearTimeout(gameTimer);
    } else {
        displayMyGameLib();
        scheduleGames();
    }
});

scheduleGames();

/* Гостевая книга. Постмодерация: сообщение видно сразу, спрятать его может
   только владелец ключом. Всё, что пришло с сервера, уходит в DOM через esc(). */
const GB_MAX = 280;

/* Рожицы. Список правится здесь. Символы < и > внутри них безопасны: кнопки
   создаются скриптом и текст кладётся через textContent, в разметку он
   не попадает ни на каком шаге. */
const KAOMOJI = [':3', '^_^', '>w<', 'uwu', 'owo', '>:3', 'o_o', 'T_T',
                 'x3', '>_<', '._.', ':p'];

function insertFace(body, sync, face) {
    const start = body.selectionStart ?? body.value.length;
    const end = body.selectionEnd ?? body.value.length;
    const next = body.value.slice(0, start) + face + body.value.slice(end);
    // maxlength поля программную вставку не ограничивает, считаем сами
    if (next.length > GB_MAX) return;

    body.value = next;
    const caret = start + face.length;
    body.setSelectionRange(caret, caret);
    body.focus();
    sync();
}

function initFaces(body, sync) {
    const bar = document.getElementById('gb-faces');
    if (!bar) return;

    KAOMOJI.forEach(face => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'gb-face';
        btn.textContent = face;
        btn.addEventListener('click', () => insertFace(body, sync, face));
        bar.appendChild(btn);
    });
}

function initGuestbook() {
    const form = document.getElementById('gb-form');
    if (!form) return;

    const nick = document.getElementById('gb-nick');
    const body = document.getElementById('gb-body');
    const trap = document.getElementById('gb-website');
    const submit = document.getElementById('gb-submit');
    const counter = document.getElementById('gb-count');
    const status = document.getElementById('gb-status');

    const say = (text, bad) => {
        status.textContent = text;
        status.classList.toggle('bad', Boolean(bad));
    };

    // поле растёт под текст: иначе внутри композера торчала бы ручка ресайза
    const grow = () => {
        body.style.height = 'auto';
        body.style.height = body.scrollHeight + 'px';
    };

    const sync = () => {
        const length = body.value.trim().length;
        const left = GB_MAX - length;
        counter.textContent = String(left);
        counter.classList.toggle('low', left <= 30);
        submit.disabled = length === 0 || left < 0;
        grow();
    };

    body.addEventListener('input', sync);
    sync();
    initFaces(body, sync);

    /* Высоту поля считаем по scrollHeight, а он врёт, пока не встал шрифт:
       кривое значение застыло бы в inline-стиле, и при overflow:hidden текст
       было бы не видно. Поэтому пересчитываем после загрузки шрифтов и на
       смене ширины окна. */
    if (document.fonts && document.fonts.ready) {
        document.fonts.ready.then(sync).catch(() => {});
    }
    window.addEventListener('resize', sync);

    form.addEventListener('submit', event => {
        event.preventDefault();
        if (submit.disabled) return;

        submit.disabled = true;
        say('sending…');

        fetch('/guestbook', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                nick: nick.value.trim(),
                body: body.value.trim(),
                website: trap.value
            })
        })
            .then(async res => {
                // текст ошибки приходит в теле, его и показываем как есть
                const data = await res.json().catch(() => ({}));
                if (!res.ok) throw new Error(data.error || 'HTTP ' + res.status);
                return data;
            })
            .then(() => {
                body.value = '';
                sync();
                say('sent, thanks');
            })
            .catch(err => {
                say(err.message, true);
                submit.disabled = false;
            });
    });
}

initGuestbook();

/* --------------------------------------------------------------- tiktok --
   Лента репостов: один пост на кадр, следующий — прокруткой вниз.
   Постов два вида: обычное видео и фото-пост, где кадры листают вбок
   под свою звуковую дорожку.

   Свёрнута по умолчанию, наружу торчит верхушка первого поста. Пока
   свёрнута — ничего не играет и не качается: иначе три десятка роликов
   полезли бы через наш сервер к тому, кто их даже не открывал. */

let tiktokMuted = true;
let tiktokOpen = false;

/* Когда автор выложил видео — в формате треков: 5m, 2h, 3d.
   Именно posted, а не reposted: времени репоста TikTok не отдаёт, и подпись
   не должна обещать больше, чем мы знаем. Считаем здесь, а не на сервере:
   список лежит в кэше час, и готовая строка старела бы прямо на экране. */
function tiktokAge(video) {
    if (!video.createdAt) return '';
    const minutes = Math.floor((Date.now() / 1000 - video.createdAt) / 60);
    if (minutes < 1) return '';
    const age = minutes > 1440 ? Math.floor(minutes / 1440) + 'd'
        : minutes > 60 ? Math.floor(minutes / 60) + 'h'
        : minutes + 'm';
    return `<span class="tiktok-age" title="posted ${age} ago">posted ${age}</span>`;
}

/* Звук общий на всю ленту: кнопка на кадре переключает её целиком, иначе
   после каждого пролистывания его пришлось бы включать заново. Надпись на
   кнопке называет действие, а не состояние: «sound on» — значит нажми,
   чтобы включить. */
function applyTikTokSound() {
    document.querySelectorAll('.tiktok-item video, .tiktok-item audio').forEach(media => {
        media.muted = tiktokMuted;
    });
    document.querySelectorAll('.tiktok-sound').forEach(button => {
        button.classList.toggle('muted', tiktokMuted);
        button.querySelector('.tiktok-sound-label').textContent =
            tiktokMuted ? 'sound on' : 'sound off';
    });
}

/* Со звуком браузер пускает не всегда: правило про автовоспроизведение
   у каждого своё, и отказ приходит уже после запроса. Молча проглотить его
   нельзя — кадр останется стоять картинкой. Поэтому на отказе включаем немой
   режим и пробуем снова: тишина лучше замершего видео, а кнопка при этом
   честно показывает, что звука нет. */
function playTikTok(media) {
    media.play().catch(error => {
        /* Отказать play() может по разным причинам, а немым режимом лечится
           ровно одна: NotAllowedError — браузер не пустил со звуком.

           Всё остальное к звуку отношения не имеет. Чаще всего это
           AbortError: наблюдатель зовёт pause() на уходящем кадре, пока его
           же play() ещё не завершился, и при быстром листании это штатная
           гонка, а не сбой. Раньше она тоже считалась отказом со звуком —
           и звук глох сам посреди листания, на случайном ролике. */
        if (media.muted || !error || error.name !== 'NotAllowedError') {
            return;
        }
        tiktokMuted = true;
        applyTikTokSound();
        media.play().catch(() => {});
    });
}

/* Значок на кнопке звука. Один динамик на оба состояния: волны и перечёркивание
   переключает CSS по классу, поэтому разметка не пересобирается при каждом
   нажатии и кнопка не моргает. */
const SOUND_ICON =
    `<svg class="tiktok-sound-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor"` +
    ` stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">` +
    `<polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"></polygon>` +
    `<g class="waves">` +
    `<path d="M15.54 8.46a5 5 0 0 1 0 7.07"></path>` +
    `<path d="M19.07 4.93a10 10 0 0 1 0 14.14"></path>` +
    `</g>` +
    `<g class="slash">` +
    `<path d="M22.5 9.5l-5 5"></path><path d="M17.5 9.5l5 5"></path>` +
    `</g></svg>`;

function soundButton() {
    return `<button class="tiktok-sound muted" type="button">` +
        SOUND_ICON + `<span class="tiktok-sound-label">sound on</span></button>`;
}

function tiktokMedia(item) {
    return item.querySelector('video') || item.querySelector('audio');
}

function buildVideoPost(video) {
    /* Подложка — размытая копия обложки. Вертикальные среди репостов
       в меньшинстве: есть квадратные, 4:3 и горизонтальные, и без неё
       кадр приходилось бы обрезать. */
    return `<img class="backdrop" src="${esc(video.cover)}" alt="" aria-hidden="true">` +
        `<video preload="none" loop playsinline muted poster="${esc(video.cover)}"` +
        ` src="${esc(video.videoUrl)}"></video>` +
        `<img class="poster" src="${esc(video.cover)}" alt="" loading="lazy">` +
        /* Значок паузы поверх кадра. Нажимают не по нему, а по самому кадру,
           поэтому он ничего не ловит и нужен только как подтверждение,
           что видео стоит, а не зависло. */
        `<div class="tiktok-pause" aria-hidden="true">` +
        `<svg viewBox="0 0 24 24" fill="currentColor">` +
        `<rect x="7" y="5" width="3.4" height="14" rx="1.2"></rect>` +
        `<rect x="13.6" y="5" width="3.4" height="14" rx="1.2"></rect>` +
        `</svg></div>`;
}

function buildPhotoPost(video) {
    const frames = video.images.map(src =>
        `<img src="${esc(src)}" alt="" loading="lazy">`).join('');
    const dots = video.images.map((_, i) =>
        `<i${i === 0 ? ' class="on"' : ''}></i>`).join('');
    /* Звук у фото-поста отдельной дорожкой: видео тут нет вовсе, и без
       этого пост показывался немой картинкой. */
    const audio = video.audioUrl
        ? `<audio preload="none" loop src="${esc(video.audioUrl)}"></audio>` : '';
    const arrows = video.images.length > 1
        ? `<button class="tiktok-arrow prev" type="button" aria-label="previous frame">&lsaquo;</button>` +
          `<button class="tiktok-arrow next" type="button" aria-label="next frame">&rsaquo;</button>`
        : '';
    return `<img class="backdrop" src="${esc(video.cover)}" alt="" aria-hidden="true">` +
        `<div class="tiktok-photos">${frames}</div>` +
        `<div class="tiktok-dots">${dots}</div>` + arrows + audio;
}

/* Листание кадров делаем сами, а не полагаемся на прокрутку: вложенный
   горизонтальный скролл внутри вертикального scroll-snap перехватывается
   родителем, а мышью вбок вообще не покрутить. */
function setupPhotoNav(item, count) {
    const rail = item.querySelector('.tiktok-photos');
    const dots = item.querySelectorAll('.tiktok-dots i');
    const prev = item.querySelector('.tiktok-arrow.prev');
    const next = item.querySelector('.tiktok-arrow.next');
    let at = 0;

    const show = index => {
        at = Math.max(0, Math.min(count - 1, index));
        rail.scrollTo({ left: at * rail.clientWidth, behavior: 'smooth' });
        dots.forEach((dot, i) => dot.classList.toggle('on', i === at));
        if (prev) prev.disabled = at === 0;
        if (next) next.disabled = at === count - 1;
    };

    if (prev) {
        prev.addEventListener('click', event => {
            event.stopPropagation();
            show(at - 1);
        });
    }
    if (next) {
        next.addEventListener('click', event => {
            event.stopPropagation();
            show(at + 1);
        });
    }

    // тап по краю кадра, как в оригинале
    rail.addEventListener('click', event => {
        if (event.target.closest('a, button')) return;
        const box = rail.getBoundingClientRect();
        show(event.clientX - box.left > box.width / 2 ? at + 1 : at - 1);
    });

    // если прокрутить всё-таки удалось — точки не должны врать
    rail.addEventListener('scroll', () => {
        const now = Math.round(rail.scrollLeft / rail.clientWidth);
        if (now !== at) {
            at = now;
            dots.forEach((dot, i) => dot.classList.toggle('on', i === at));
            if (prev) prev.disabled = at === 0;
            if (next) next.disabled = at === count - 1;
        }
    }, { passive: true });

    show(0);
}

/* Описание поста. Свёрнуто до двух строк: развёрнутое целиком у длинных
   подписей закрывало бы полкадра — ровно то, ради чего в TikTok его и
   прячут. Пустого блока нет вовсе: у части репостов подписи нет, и он
   съедал бы место молча. */
function tiktokDesc(video) {
    const text = (video.description || '').trim();
    if (!text) return '';
    return `<p class="tiktok-desc">${esc(text)}</p>`;
}

/* Кнопка появляется, только если текст правда не поместился. Узнать это
   можно исключительно после вёрстки — отсюда замер, а не длина строки:
   сколько строк займёт подпись, зависит от ширины колонки и от того,
   какие в ней слова. */
function setupCaptions(feed) {
    feed.querySelectorAll('.tiktok-desc').forEach(desc => {
        if (desc.scrollHeight <= desc.clientHeight + 1) return;

        const more = document.createElement('button');
        more.className = 'tiktok-more';
        more.type = 'button';
        more.textContent = 'more';
        /* Нажатие не должно доходить до кадра: там оно ставит видео
           на паузу, а разворачивали подпись. */
        more.addEventListener('click', event => {
            event.stopPropagation();
            more.textContent = desc.classList.toggle('open') ? 'less' : 'more';
        });
        desc.after(more);
    });
}

function buildTikTokItem(video, index, total) {
    const item = document.createElement('div');
    item.className = 'tiktok-item';
    const frames = (video.images || []).length;

    item.innerHTML =
        (frames ? buildPhotoPost(video) : buildVideoPost(video)) +
        soundButton() +
        `<div class="tiktok-meta">` +
        tiktokDesc(video) +
        `<div class="tiktok-meta-row">` +
        `<a href="${esc(video.url)}" target="_blank" rel="noopener">@${esc(video.author)}</a>` +
        tiktokAge(video) +
        `<span class="tiktok-count">${index + 1}/${total}</span>` +
        `</div></div>`;

    const media = tiktokMedia(item);

    if (!frames && media) {
        /* Обложку убираем только когда картинка реально пошла: до этого
           у video пустой чёрный кадр, и перелистывание выглядит как провал. */
        media.addEventListener('loadeddata', () => item.classList.add('ready'));

        /* Состояние снимаем с самого видео, а не выставляем в обработчике
           нажатия: на паузу его ставит ещё и наблюдатель, когда кадр уходит
           с экрана, и значок иначе расходился бы с тем, что происходит.
           До первого запуска видео тоже стоит — класс поставлен сразу. */
        item.classList.add('paused');
        media.addEventListener('play', () => item.classList.remove('paused'));
        media.addEventListener('pause', () => item.classList.add('paused'));
        // тап по кадру — пауза, как везде
        item.addEventListener('click', event => {
            if (event.target.closest('a, button')) return;
            if (media.paused) {
                playTikTok(media);
            } else {
                media.pause();
            }
        });
    }

    if (frames > 1) {
        setupPhotoNav(item, frames);
    }

    item.querySelector('.tiktok-sound').addEventListener('click', event => {
        event.stopPropagation();
        tiktokMuted = !tiktokMuted;
        applyTikTokSound();
    });

    return item;
}

function displayTikTok(videos) {
    const section = document.getElementById('tiktok-section');
    const feed = document.getElementById('tiktok-feed');
    const wrap = document.getElementById('tiktok-wrap');
    const toggle = document.getElementById('tiktok-toggle');
    if (!section || !feed || !wrap || !toggle) return;

    if (!videos.length) {
        // не настроено или TikTok не ответил — секции быть не должно вовсе
        section.hidden = true;
        return;
    }

    feed.innerHTML = '';
    videos.forEach((video, index) => feed.appendChild(buildTikTokItem(video, index, videos.length)));
    section.hidden = false;

    /* Играет только то, что видно, и только пока блок развёрнут. */
    const watcher = new IntersectionObserver(entries => {
        entries.forEach(entry => {
            const media = tiktokMedia(entry.target);
            if (!media) return;
            if (entry.isIntersecting && tiktokOpen) {
                media.muted = tiktokMuted;
                playTikTok(media);
            } else {
                media.pause();
            }
        });
    }, { root: feed, threshold: 0.6 });

    feed.querySelectorAll('.tiktok-item').forEach(item => watcher.observe(item));

    /* Через кадр: до вёрстки высоты нулевые, и «не поместилось» не отличить
       от «ещё не измерено». */
    requestAnimationFrame(() => setupCaptions(feed));

    const setOpen = open => {
        tiktokOpen = open;
        wrap.classList.toggle('expanded', tiktokOpen);
        /* Текста у кнопки нет — шеврон переворачивается через CSS
           по aria-expanded, так что состояние и рисуется, и озвучивается
           одним и тем же атрибутом. */
        toggle.setAttribute('aria-expanded', String(tiktokOpen));
        toggle.setAttribute('aria-label', tiktokOpen ? 'hide reposts' : 'show reposts');

        if (tiktokOpen) {
            /* Разворот и есть «включить»: ленту открыли намеренно, смотреть
               её немой незачем. Звук включается именно здесь, потому что
               разворот — это клик: без жеста браузер бы не пустил.
               Кнопка на кадре никуда не делась, звук после этого выключается
               обратно и включается снова сколько угодно. */
            tiktokMuted = false;
            applyTikTokSound();

            // первый пост уже на экране — наблюдатель сам его не дёрнет
            const first = tiktokMedia(feed.querySelector('.tiktok-item'));
            if (first) {
                first.muted = tiktokMuted;
                playTikTok(first);
            }
        } else {
            feed.querySelectorAll('video, audio').forEach(media => media.pause());
            feed.scrollTop = 0;
        }
    };

    toggle.addEventListener('click', () => setOpen(!tiktokOpen));

    /* Свёрнутая лента разворачивается и по нажатию на сам торчащий кадр:
       он для того и торчит. Внутри ленты в этот момент pointer-events
       выключены, так что нажатие достаётся обёртке, а не видео. */
    wrap.addEventListener('click', () => {
        if (!tiktokOpen) {
            setOpen(true);
        }
    });
}

fetch('/tiktok')
    .then(response => response.json())
    .then(displayTikTok)
    .catch(err => console.error('Ошибка при получении репостов TikTok:', err));

/* Все аккаунты Steam — аватарками внутри карточки steam. Нажатие
   растягивает карточку вниз; если аккаунты так и не пришли, разворачивать
   нечего, и нажатие просто открывает основной профиль. */
const STEAM_MAIN = 'https://steamcommunity.com/id/Gnaisel/';

function setupSteamCard(accounts) {
    const toggle = document.getElementById('steam-toggle');
    const list = document.getElementById('steam-accounts');
    const handle = document.getElementById('steam-handle');
    const card = toggle && toggle.closest('.steam-card');
    if (!toggle || !list || !handle || !card) return;

    if (!accounts.length) {
        card.classList.add('no-accounts');
        handle.textContent = '/Gnaisel';
        toggle.removeAttribute('aria-expanded');
        toggle.removeAttribute('aria-controls');
        toggle.addEventListener('click', () => window.open(STEAM_MAIN, '_blank', 'noopener'));
        return;
    }

    /* Справа в карточке — сколько аккаунтов, а пока наведена аватарка —
       чья она: подсказка браузера появляется с задержкой и выглядит чужой. */
    const summary = accounts.length === 1 ? '1 account' : `${accounts.length} accounts`;
    const showName = name => {
        handle.textContent = name || summary;
        handle.classList.toggle('is-name', Boolean(name));
    };
    showName(null);

    list.innerHTML = '';
    accounts.forEach((account, index) => {
        const link = document.createElement('a');
        link.className = 'steam-account';
        link.href = account.profileUrl;
        link.target = '_blank';
        link.rel = 'noopener';
        link.setAttribute('aria-label', account.name);
        link.style.setProperty('--i', index);
        // в свёрнутой карточке невидимые ссылки не должны ловить Tab
        link.tabIndex = -1;
        link.addEventListener('mouseenter', () => showName(account.name));
        link.addEventListener('focus', () => showName(account.name));
        link.addEventListener('mouseleave', () => showName(null));
        link.addEventListener('blur', () => showName(null));

        const img = document.createElement('img');
        img.dataset.src = account.avatarUrl;
        img.alt = '';
        link.appendChild(img);

        list.appendChild(link);
    });

    /* Аватарки качаются, только когда карточку собираются открыть: навели
       на неё, сфокусировали или нажали. Иначе шесть картинок грузились бы
       у каждого, кто зашёл, хотя раскрывают карточку единицы. */
    const loadAvatars = () => list.querySelectorAll('img[data-src]').forEach(img => {
        img.src = img.dataset.src;
        img.removeAttribute('data-src');
    });
    toggle.addEventListener('pointerenter', loadAvatars, { once: true });
    toggle.addEventListener('focus', loadAvatars, { once: true });

    toggle.addEventListener('click', () => {
        loadAvatars();
        const open = !card.classList.contains('open');
        card.classList.toggle('open', open);
        toggle.setAttribute('aria-expanded', String(open));
        list.querySelectorAll('a').forEach(link => { link.tabIndex = open ? 0 : -1; });
        if (!open) showName(null);
    });
}

fetch('/steam/accounts')
    .then(response => response.json())
    .then(setupSteamCard)
    .catch(err => {
        console.error('Ошибка при получении аккаунтов Steam:', err);
        setupSteamCard([]);
    });

/* Подсказка про часы в блоке игр: наведение показывает её через CSS,
   нажатие закрепляет, нажатие мимо или Esc — убирает. */
(function setupHoursNote() {
    const btn = document.getElementById('hours-note-btn');
    if (!btn) return;
    const set = open => btn.setAttribute('aria-expanded', String(open));
    btn.addEventListener('click', event => {
        event.stopPropagation();
        set(btn.getAttribute('aria-expanded') !== 'true');
    });
    document.addEventListener('click', event => {
        if (!btn.parentElement.contains(event.target)) set(false);
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape') set(false);
    });
})();

