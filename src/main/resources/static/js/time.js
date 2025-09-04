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

function updateVisitCounter() {
    fetch("/visitor")
        .then(res => res.text())
        .then(data => {
          document.getElementById('number-of-visits').textContent = data;
        })
        .catch(err => console.error("Ошибка вывода числа посещений"));
}

updateVisitCounter();

updateAge();
setInterval(updateAge, 1000);

document.addEventListener("DOMContentLoaded", () => {
    const quotes = [
        "// Don't forget about me.",
        "// code. sleep. repeat.",
        "// Seven minutes. That's all it takes to begin anew. ",
        "// Seven minutes. That's all it took.",
        "// stay curious"
    ];
    document.getElementById("quote").innerText =
        quotes[Math.floor(Math.random() * quotes.length)];
});

async function fetchTracks() {
    try {
        const response = await fetch('/tracks');
        const tracks = await response.json();
        displayTracks(tracks.slice(0, 11));
    } catch (err) {
        console.error('Ошибка при получении треков:', err);
    }
}

function displayTracks(tracks) {
    const trackList = document.getElementById('track-list');
    trackList.innerHTML = '';

    tracks.forEach(track => {
        const trackEl = document.createElement('div');
        trackEl.className = 'track';

        const imageUrl = track.imageUrl && track.imageUrl.trim()
            ? track.imageUrl
            : '/image/unknown-track.JPG';

        trackEl.innerHTML = `
            <img src="${imageUrl}" alt="Обложка">
            <div class="track-info">
                <div class="track-name">${track.name}</div>
                <div class="track-artist">${track.artist}</div>
            </div>
            ${!track.attr ? `<div class="track-time-later">${track.timeFromLastListen}</div>` : ''}
        `;

        if (track.attr) {
            trackEl.classList.add('playing');
        }

        trackList.appendChild(trackEl);
    });
}

fetchTracks();
setInterval(fetchTracks, 10000);

document.addEventListener("DOMContentLoaded", () => {
    const trackList = document.querySelector('.track-list');

    trackList.addEventListener('wheel', (e) => {
        if (e.deltaY !== 0) {
            trackList.scrollLeft += e.deltaY;
            e.preventDefault();
        }
    });
});

function displayMyGameLib() {
    fetch('/games')
        .then(rep => rep.json())
        .then(data => {
            displayLastGame(data)
            displayGameLib(data.slice(1));
        })
        .catch(err => {
            console.error("Ошибка при получении или отображении игр:", err);

            const gameLib = document.getElementById('game-lib');
            gameLib.innerHTML = '<p>Ошибка загрузки игр.</p>';
        })
}

function displayLastGame(data) {
    if (!data || data.length === 0) {
        console.warn("Нет данных для отображения последней игры.");
        return; 
    }

    const lastGame = data[0];
    const lastGameDiv = document.getElementById('last-game');

    if (!lastGameDiv) {
        console.warn("Элемент с id 'last-game' не найден.");
        return;
    }

    lastGameDiv.innerHTML = '';

    const gameHead = document.createElement('div');
    gameHead.classList.add('game-head');

    gameHead.innerHTML = `
        <img src="${lastGame.img_icon_url}" alt="game-icon">
        <h2 class="last-game-title">${lastGame.name}</h2>`;

    const gameInformation = document.createElement('div');
    gameInformation.classList.add('game-information');

    gameInformation.innerHTML = `
        <h3 class="playtime-forever">Playtime forever: ${lastGame.playtime_forever}</h3>
        <h3 class="playtime-2weeks"> Playtime 2weeks: ${lastGame.playtime_2weeks}</h3>
        <h3 class="playtime-sessions">Last played: ${lastGame.rtime_last_played}</h3>
    `;
    setBannerImage(`${lastGame.banner_url}`);
    displayTotalHours();

    lastGameDiv.appendChild(gameHead);
    lastGameDiv.appendChild(gameInformation);
}

function displayGameLib(data) {
    const gameLib = document.getElementById('game-lib');
    gameLib.innerHTML = ``;

    data.forEach(game => {
        const gameElement = document.createElement('div');
        gameElement.classList.add('game');

        gameElement.innerHTML = `
                    <img src="${game.img_icon_url}" alt="game-icon">
                    <div class="game-info">
                        <div class="game-title">${game.name}</div>
                        <div class="playtime-forever">${game.playtime_forever}</div>
                    </div>`;
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
            // Попробуем преобразовать в число для валидации
            const num = Number(trimmed);
            const display = Number.isFinite(num) ? num.toLocaleString(undefined, { maximumFractionDigits: 2 }) : trimmed;
            totalHoursEl.innerHTML = `<h2 class="playtime">Total hours: ${display}</h2>`;
        })
        .catch(err => {
            console.error(err);
            totalHoursEl.innerHTML = `<h2 class="playtime">Total hours: unavailable</h2>`;
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
setInterval(displayMyGameLib, 60000)
