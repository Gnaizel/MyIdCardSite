# Локальный запуск сайта: .\run-local.ps1
# Поднимает базу в Docker, собирает jar и стартует приложение на 8080.

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# Ключи из .env уезжают в переменные окружения — Spring подхватит их сам.
if (Test-Path .env) {
    Get-Content .env | ForEach-Object {
        if ($_ -match '^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*)$') {
            $name = $Matches[1]
            $value = $Matches[2].Trim().Trim('"')
            # Пустое значение пропускаем: заданная, но пустая переменная
            # перебивает дефолт из application.yaml и роняет старт.
            if ($value -ne '') { Set-Item -Path "env:$name" -Value $value }
        }
    }
    Write-Host "[ok] ключи загружены из .env" -ForegroundColor Green
} else {
    Write-Host "[!] .env не найден - секции last.fm, steam и github работать не будут." -ForegroundColor Yellow
    Write-Host "    Скопируй .env.example в .env и впиши ключи." -ForegroundColor Yellow
}

Write-Host "[..] поднимаю базу" -ForegroundColor Cyan
docker compose up -d visitor-db
if ($LASTEXITCODE -ne 0) { throw "docker не ответил - запущен ли Docker Desktop?" }

Write-Host "[..] собираю jar" -ForegroundColor Cyan
mvn -B -q clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "сборка упала" }

Write-Host "[ok] стартую на http://localhost:8080" -ForegroundColor Green
java -jar target/MySite.jar --spring.profiles.active=local
