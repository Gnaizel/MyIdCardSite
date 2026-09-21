/* Названия треков и игр приходят извне и попадают в innerHTML — экранируем. */
function esc(value) {
    return String(value ?? '').replace(/[&<>"']/g, ch => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[ch]);
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

function displayGithub(data) {
    const set = (id, text) => {
        const el = document.getElementById(id);
        if (el) el.textContent = text;
    };

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

    heatmap.innerHTML = '';
    months.innerHTML = '';

    const days = Array.isArray(data.days) ? data.days : [];
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

    list.innerHTML = artists.map(artist => `
        <li>
            <span class="rot-name" title="${esc(artist.name)}">${esc(artist.name)}</span>
            <span class="rot-bar"><i style="width: ${Math.round((artist.plays / max) * 100)}%"></i></span>
            <span class="rot-plays">${esc(artist.plays)}</span>
        </li>`).join('');

    if (sub) {
        sub.textContent = data.totalPlays + ' plays across '
            + data.artistCount + ' artists · last 12 months';
    }
    if (note) {
        const top = artists[0];
        note.innerHTML = `<b>${esc(top.name)}</b> is ${esc(top.share)}% of my year`;
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

function displayMyGameLib() {
    fetch('/games')
        .then(rep => rep.json())
        .then(data => {
            /* Самую свежую игру больше не выбрасываем из списка: без неё
               нумерация начиналась со второй, и «01» стояло не у той игры,
               что показана крупно слева. */
            gamePlaying = data.some(game => game.playingNow);
            displayGameLib(data);
            showGame(data[0]);
            /* Общие часы одни на весь блок и от выбранной игры не зависят,
               поэтому запрашиваются один раз, а не на каждый клик. */
            displayTotalHours();
        })
        .catch(err => {
            console.error("Ошибка при получении или отображении игр:", err);

            const gameLib = document.getElementById('game-lib');
            gameLib.innerHTML = '<p>Ошибка загрузки игр.</p>';
        })
}

/* Ссылку рисуем, только когда Steam её дал: она появляется лишь у игр
   со своим лобби и лишь пока в него пускают. У одиночных игр её не будет
   никогда, и это нормально, а не поломка.
   rel и target не нужны: steam:// открывает не вкладку, а сам клиент. */
function joinLink(game) {
    if (!game.joinUrl) return '';
    return `<a class="join" href="${esc(game.joinUrl)}">join</a>`;
}

/* Крупный блок показывает выбранную игру, а не только самую свежую:
   по клику в списке сюда приезжает статистика любой из них. */
function showGame(game) {
    if (!game) {
        console.warn("Нет данных для отображения игры.");
        return;
    }

    const lastGameDiv = document.getElementById('last-game');
    if (!lastGameDiv) {
        console.warn("Элемент с id 'last-game' не найден.");
        return;
    }

    lastGameDiv.innerHTML = '';
    /* Классом на блоке, а не на самом эквалайзере: так же, как у трека,
       и CSS остаётся одним правилом на оба списка. */
    lastGameDiv.classList.toggle('playing', Boolean(game.playingNow));

    const gameHead = document.createElement('div');
    gameHead.classList.add('game-head');

    gameHead.innerHTML = `
        <img src="${esc(game.img_icon_url)}" alt="" loading="lazy">
        <h2 class="last-game-title" title="${esc(game.name)}">${esc(game.name)}</h2>
        <span class="live">in game</span>${joinLink(game)}`;

    const gameInformation = document.createElement('div');
    gameInformation.classList.add('game-information');

    gameInformation.innerHTML = `
        <h3 class="playtime-forever">playtime forever: ${esc(game.playtime_forever)}</h3>
        <h3 class="playtime-2weeks">playtime 2 weeks: ${esc(game.playtime_2weeks)}</h3>
        <h3 class="playtime-sessions">last played: ${esc(game.rtime_last_played)}</h3>
    `;
    setBannerImage(`${game.banner_url}`);

    lastGameDiv.appendChild(gameHead);
    lastGameDiv.appendChild(gameInformation);

    markSelected(game.appid);
}

/* Подсветка в списке должна совпадать с тем, что показано крупно, иначе
   непонятно, чью статистику сейчас видишь. */
function markSelected(appid) {
    document.querySelectorAll('#game-lib .game').forEach(el => {
        const active = String(el.dataset.appid) === String(appid);
        el.classList.toggle('is-active', active);
        el.setAttribute('aria-pressed', active ? 'true' : 'false');
    });
}

function displayGameLib(data) {
    const gameLib = document.getElementById('game-lib');
    gameLib.innerHTML = ``;

    data.forEach(game => {
        /* Кнопка, а не div: таб, Enter и пробел начинают работать сами,
           и не нужно городить обработчики клавиатуры руками. */
        const gameElement = document.createElement('button');
        gameElement.type = 'button';
        gameElement.classList.add('game');
        gameElement.dataset.appid = game.appid;

        gameElement.innerHTML = `
                    <img src="${esc(game.img_icon_url)}" alt="" loading="lazy">
                    <div class="game-info">
                        <div class="game-title" title="${esc(game.name)}">${esc(game.name)}</div>
                        <div class="playtime-forever">${esc(game.playtime_forever)}</div>
                    </div>
                    <span class="live">in game</span>`;
        gameElement.classList.toggle('playing', Boolean(game.playingNow));
        gameElement.addEventListener('click', () => showGame(game));
        gameLib.appendChild(gameElement);
    })
}

function displayTotalHours() {
    const totalHoursEl = document.getElementById('playtime-in-total');
    if (!totalHoursEl) return;

    totalHoursEl.innerHTML = '';

    fetch('games-total-hours')
        .then(resp => {
            if (!resp.ok) throw new Error('Network response was not ok');
            return resp.text();
        })
        .then(data => {
            const trimmed = data.trim();
            const num = Number(trimmed);
            const display = Number.isFinite(num) ? num.toLocaleString(undefined, { maximumFractionDigits: 2 }) : trimmed;
            totalHoursEl.innerHTML = `
                <img src="/image/clock.png" alt="" aria-hidden="true">
                <h2 class="playtime">all time: <span class="num">${esc(display)}</span> h</h2>
            `;
        })
        .catch(err => {
            console.error(err);
            totalHoursEl.innerHTML = `<h2 class="playtime">all time: <span class="num">unavailable</span></h2>`;
        });
}

function setBannerImage(imageUrl) {
    const lastGame = document.getElementById('last-game');

    const oldBanner = lastGame.querySelector('.banner-image');
    if (oldBanner) oldBanner.remove();

    const banner = document.createElement('div');
    banner.className = 'banner-image';
    banner.style.backgroundImage = `url('${imageUrl}')`;

    lastGame.insertBefore(banner, lastGame.firstChild);

    setTimeout(() => {
        banner.style.opacity = '1';
    }, 10);
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
   Репосты тянутся с профиля. Обложки подписаны и живут около двух суток,
   поэтому бэк обновляет список раз в час — здесь достаточно спросить один
   раз при загрузке. */

function displayTikTok(videos) {
    const section = document.getElementById('tiktok-section');
    const grid = document.getElementById('tiktok-grid');
    if (!section || !grid) return;

    if (!videos.length) {
        // не настроено или TikTok не ответил — секции быть не должно вовсе
        section.hidden = true;
        return;
    }

    grid.innerHTML = '';
    videos.forEach(video => {
        const item = document.createElement('a');
        item.className = 'tiktok-item';
        item.href = video.url;
        item.target = '_blank';
        item.rel = 'noopener';
        item.title = video.description || ('@' + video.author);
        item.innerHTML =
            `<img src="${esc(video.cover)}" alt="" loading="lazy">` +
            `<span class="tiktok-author">@${esc(video.author)}</span>`;
        grid.appendChild(item);
    });
    section.hidden = false;
}

fetch('/tiktok')
    .then(response => response.json())
    .then(displayTikTok)
    .catch(err => console.error('Ошибка при получении репостов TikTok:', err));
