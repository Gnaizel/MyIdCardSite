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

/* У Fortnite appid нет (0), так что ключ по одному appid склеил бы любые
   две игры без него. */
function gameKey(game) {
    return game.appid ? String(game.appid) : 'name:' + (game.name ?? '');
}

function displayMyGameLib() {
    fetch('/games')
        .then(rep => {
            if (!rep.ok) throw new Error('HTTP ' + rep.status);
            return rep.json();
        })
        .then(data => {
            /* Самую свежую игру больше не выбрасываем из списка: без неё
               нумерация начиналась со второй, и «01» стояло не у той игры,
               что показана крупно слева. */
            gamePlaying = data.some(game => game.playingNow);
            gamesByKey = new Map(data.map(game => [gameKey(game), game]));
            displayGameLib(data);
            /* Выбранная кликом игра остаётся выбранной и после опроса:
               раньше каждые полминуты крупный блок сам перескакивал
               обратно на первую. */
            showGame((selectedGameKey && gamesByKey.get(selectedGameKey)) || data[0]);
            /* Общие часы одни на весь блок и от выбранной игры не зависят,
               поэтому запрашиваются вместе со списком, а не на каждый клик. */
            displayTotalHours();
            // индикатор Discord зависит от того, играю ли в Steam, — сверяем сразу
            displayPresence();
        })
        .catch(err => {
            console.error("Ошибка при получении или отображении игр:", err);
            displayPresence();

            /* Одна неудачная попытка не должна стирать уже нарисованный
               список: ошибку показываем, только если показывать больше нечего. */
            const gameLib = document.getElementById('game-lib');
            if (gameLib && !gameLib.querySelector('.game')) {
                gameLib.innerHTML = '<p>Ошибка загрузки игр.</p>';
            }
        });
}

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

   Рисуем её, только когда Steam её дал: она появляется лишь у игр со своим
   лобби и лишь пока в него пускают. Строка при этом есть всегда и просто
   раскрывается и сворачивается: раньше она вставлялась рывком, и карточка
   вздрагивала. В свёрнутом виде места она не занимает.
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

    /* Игру берём по ключу в момент клика, а не замыкаем при создании:
       плитка живёт между опросами, и замкнутые данные устарели бы. */
    el.addEventListener('click', () => {
        const game = gamesByKey.get(el.dataset.key);
        if (!game) return;
        // клик по первой — снова «следить за самой свежей»
        const first = document.querySelector('#game-lib .game');
        selectedGameKey = first === el ? null : el.dataset.key;
        showGame(game);
    });
    return el;
}

function paintGameItem(el, game, animate) {
    el.dataset.key = gameKey(game);

    const img = el.querySelector('img');
    const icon = game.img_icon_url ?? '';
    // src трогаем только при смене, иначе иконка моргает на каждом опросе
    if (img.getAttribute('src') !== icon) img.setAttribute('src', icon);

    const title = el.querySelector('.game-title');
    setText(title, game.name, false);
    title.title = game.name ?? '';

    setText(el.querySelector('.playtime-forever'), game.playtime_forever, animate);
    el.classList.toggle('playing', Boolean(game.playingNow));
}

/* Плитки переиспользуются по ключу игры. Если порядок поменялся — например,
   запущенная игра поднялась наверх, — плитки доезжают до новых мест,
   а не перескакивают. */
function displayGameLib(data) {
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
        paintGameItem(el, game, !fresh);
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

function displayTotalHours() {
    const num = document.querySelector('#playtime-in-total .num');
    const unit = document.querySelector('#playtime-in-total .unit');
    if (!num) return;

    fetch('games-total-hours')
        .then(resp => {
            if (!resp.ok) throw new Error('Network response was not ok');
            return resp.text();
        })
        .then(data => {
            const trimmed = data.trim();
            const value = Number(trimmed);
            const display = Number.isFinite(value)
                ? value.toLocaleString(undefined, { maximumFractionDigits: 2 })
                : trimmed;
            setText(num, display, true);
            if (unit) unit.hidden = false;
        })
        .catch(err => {
            console.error(err);
            // уже показанное число лучше, чем «unavailable» из-за одного сбоя
            if (num.textContent === '--') setText(num, 'unavailable', false);
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
   меняется редко, а индикатор должен гаснуть без большой задержки.
   Скрытая вкладка не опрашивает вовсе. */
const GAME_POLL_PLAYING = 30000;
const GAME_POLL_IDLE = 120000;
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

/* Во что играю прямо сейчас — по презенсу Discord.

   Нужно для игр, которых нет в Steam. У Riot живого статуса для Valorant
   не отдаёт ни одна открытая ручка: в официальном API его нет, а внутренняя
   просит токены, которые добываются только из запущенного клиента. Discord
   же определяет запущенную игру сам, и его презенс приходит обычным GET.

   Показываем, только когда Steam молчит: если играю в игру из списка,
   индикатор уже горит на её карточке, и второй такой же рядом — шум. */
function displayPresence() {
    const row = document.getElementById('playing-now');
    const name = document.getElementById('playing-now-game');
    if (!row || !name) return;

    fetch('/presence')
        // 204 — «не играю»: тела нет вовсе, разбирать нечего
        .then(response => (response.status === 204 ? null : response.json()))
        .then(data => {
            if (!data || !data.game || gamePlaying) {
                row.classList.remove('shown');
                return;
            }
            /* Строка раскрывается плавно, а не выпрыгивает: блок под ней
               сдвигается вниз вместе с ней, без рывка. Если игра сменилась,
               пока строка уже видна, вспыхивает только название. */
            setText(name, data.game, row.classList.contains('shown'));
            /* details Discord заполняет не у всех игр — тогда подсказка
               повторяет название, и это лучше пустого title. */
            name.title = data.details || data.game;
            row.classList.add('shown');
        })
        .catch(() => {
            /* Сервис чужой: замолчал — просто не показываем строку,
               а не пишем об этом на странице. */
            row.classList.remove('shown');
        });
}

/* Первый запрос делает загрузка списка игр: без неё неизвестно, играю ли
   в Steam, и строка могла бы мелькнуть зря. */
/* Тот же темп, что у списка игр в простое: статус должен гаснуть без
   большой задержки, но чужой сервис дёргать чаще незачем. */
setInterval(() => {
    if (!document.hidden) displayPresence();
}, 45000);

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
