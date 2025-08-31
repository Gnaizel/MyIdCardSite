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
