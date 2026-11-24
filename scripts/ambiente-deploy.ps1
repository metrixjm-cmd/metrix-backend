# Ambiente deploy. Construye la imagen de produccion y la arranca en local.
# No hace push, no llama a Cloud Run y no usa Atlas.
# Uso, desde metrix-backend:  .\scripts\ambiente-deploy.ps1
# Bajarlo:                    .\scripts\ambiente-deploy.ps1 -Down
param([switch]$Down)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function New-LocalJwt {
    $bytes = New-Object byte[] 48
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    return [Convert]::ToBase64String($bytes)
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Hace falta Docker para ensayar el deploy."
}

if ($Down) {
    docker compose -f docker-compose.deploy.yml down
    Write-Host "Ambiente deploy apagado. Los datos siguen en el volumen metrix-deploy-data."
    exit 0
}

$envFile = Join-Path $root ".env.deploy"
if (-not (Test-Path $envFile)) {
    $jwt = New-LocalJwt
    "METRIX_SECURITY_JWT_SECRET_KEY=$jwt" | Set-Content -Path $envFile -Encoding ascii
    Write-Host "Creado .env.deploy (solo esta maquina, no se commitea)."
}

Write-Host "Construyendo la misma imagen del Dockerfile y levantandola en :8081..."
docker compose -f docker-compose.deploy.yml up --build -d
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$deadline = (Get-Date).AddMinutes(8)
$status = "000"
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
    $body = '{"numeroUsuario":"test","password":"test"}'
    $status = curl.exe -s -o NUL -w "%{http_code}" "http://localhost:8081/api/v1/auth/login" -X POST -H "Content-Type: application/json" -d $body
    Write-Host "Chequeo de arranque: HTTP $status"
    if ($status -ne "000") { break }
}

if ($status -eq "000") {
    Write-Host "La API no respondio. Ultimas lineas del contenedor:"
    docker logs --tail 80 metrix-deploy-api
    exit 1
}

Write-Host "Deploy local en pie: http://localhost:8081"
Write-Host "Swagger esta apagado, igual que en produccion. El chequeo es el mismo POST /api/v1/auth/login del workflow."
Write-Host "Este ensayo no desplego a Cloud Run."
