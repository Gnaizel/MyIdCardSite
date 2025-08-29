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