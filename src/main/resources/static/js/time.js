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
        "// keep it simple",
        "// code. sleep. repeat.",
        "// less is more",
        "// backend > frontend",
        "// stay curious"
    ];
    document.getElementById("quote").innerText =
        quotes[Math.floor(Math.random() * quotes.length)];
});

async function fetchTracks() {
    try {
        const response = await fetch('/tracks');
        const tracks = await response.json();
        displayTracks(tracks.slice(0, 5));
    } catch (err) {
        console.error('Ошибка при получении треков:', err);
    }
}

    function displayTracks(tracks) {
        const container = document.getElementById('track-list');
        container.innerHTML = '';
    
        tracks.forEach(track => {
            const trackEl = document.createElement('div');
            trackEl.className = 'track';

            const imageUrl = track.imageUrl && track.imageUrl.trim()
                ? track.imageUrl
                : '/image/unknown-track.png';

            trackEl.innerHTML = `
                <img src="${imageUrl}" alt="Обложка">
                <div class="track-info">
                    <div class="track-name">${track.name}</div>
                    <div class="track-artist">${track.artist}</div>
                </div>
            `;

            container.appendChild(trackEl);
        });
    }

fetchTracks();
setInterval(fetchTracks, 10000);