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

document.addEventListener("DOMContentLoaded", () => {
    const quotes = [
        "// Don't forget",
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
        displayTracks(tracks.slice(0, 6));
    } catch (err) {
        console.error('Ошибка при получении треков:', err);
    }
}

function displayTracks(tracks) {
    const leftColumn = document.getElementById('track-list-left');
    const rightColumn = document.getElementById('track-list-right');
    
    leftColumn.innerHTML = '';
    rightColumn.innerHTML = '';

    const leftTracks = tracks.slice(0, 3); // Первые 3 трека
    const rightTracks = tracks.slice(3, 6); // Следующие 3 трека

    const createTrackElement = (track) => {
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
        `;

        return trackEl;
    };

    leftTracks.forEach(track => {
        leftColumn.appendChild(createTrackElement(track));
    });

    rightTracks.forEach(track => {
        rightColumn.appendChild(createTrackElement(track));
    });
}

fetchTracks();
setInterval(fetchTracks, 10000);