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

// Вызываем функцию при загрузке страницы
updateTime();

// Обновляем время периодически
setInterval(updateTime, 1000);