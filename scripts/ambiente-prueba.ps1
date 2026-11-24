# Ambiente prueba. Mongo local + API con Maven en :8080. No despliega nada.
# Uso, desde metrix-backend:  .\scripts\ambiente-prueba.ps1
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function New-LocalJwt {
    $bytes = New-Object byte[] 48
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return [Convert]::ToBase64String($bytes)
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Hace falta Docker para el Mongo de prueba."
}

function Test-DockerEngine {
    cmd /c "docker info >nul 2>nul"
    return $LASTEXITCODE -eq 0
}

if (-not (Test-DockerEngine)) {
    $desktop = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    if (-not (Test-Path $desktop)) {
        Write-Error "Docker Desktop no esta encendido."
    }
    Write-Host "Docker Desktop esta apagado. Abriendolo y esperando al motor..."
    Start-Process $desktop
    $listo = $false
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Seconds 3
        if (Test-DockerEngine) { $listo = $true; break }
    }
    if (-not $listo) {
        Write-Error "Docker Desktop no quedo listo. Abrelo a mano y vuelve a correr el bat."
    }
}

$envFile = Join-Path $root ".env.prueba"
if (-not (Test-Path $envFile)) {
    $jwt = New-LocalJwt
    @"
METRIX_SECURITY_JWT_SECRET_KEY=$jwt
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/metrix_prueba
"@ | Set-Content -Path $envFile -Encoding ascii
    Write-Host "Creado .env.prueba (solo esta maquina, no se commitea)."
}

Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
    $name, $value = $_.Split('=', 2)
    Set-Item -Path "Env:$name" -Value $value
}

Write-Host "Levantando Mongo de prueba en localhost:27017 (base metrix_prueba)..."
docker compose -f docker-compose.prueba.yml up -d
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "API de prueba en http://localhost:8080  |  Swagger en http://localhost:8080/swagger-ui.html"
Write-Host "El frontend de prueba: en otra terminal, frontend-web\scripts\ambiente-prueba.ps1"
mvn spring-boot:run
