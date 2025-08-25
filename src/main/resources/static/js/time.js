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

function updateAge() {
    fetch("/old")
        .then(res => res.text())
        .then(age => {
            document.getElementById("old").textContent = age;
        })
        .catch(err => console.error("Ошибка при запросе возраста:", err));
}

updateTime();
updateAge();

setInterval(updateAge, 1000)
setInterval(updateTime, 1000);