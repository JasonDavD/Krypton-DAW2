#!/usr/bin/env pwsh
# scripts/setup-tests.ps1
#
# Configura ~/.testcontainers.properties para que los tests de integracion
# (Testcontainers + MySQL) encuentren el daemon de Docker en ESTA maquina.
#
# Por que existe:
#   Docker Desktop con Engine 29+ no escucha en el pipe por defecto que busca
#   Testcontainers (//./pipe/docker_engine). Este script detecta el endpoint
#   REAL de tu Docker y lo escribe en tu carpeta home.
#
#   Es configuracion POR-MAQUINA: por eso NO va en el repo (cada uno tiene su
#   propio Docker). El script la genera sola, sin hardcodear el setup de nadie.
#
# Uso:
#   pwsh ./scripts/setup-tests.ps1
#   # si PowerShell bloquea la ejecucion:
#   powershell -ExecutionPolicy Bypass -File ./scripts/setup-tests.ps1

$ErrorActionPreference = 'Stop'

Write-Host "==> Configurando Testcontainers para esta maquina..." -ForegroundColor Cyan

# 1. Verificar que Docker este disponible
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: no se encontro 'docker' en el PATH." -ForegroundColor Red
    Write-Host "       Instala Docker Desktop y volve a correr este script." -ForegroundColor Red
    exit 1
}

# 2. Detectar el endpoint real del daemon de Docker (lo que escucha ESTA maquina)
try {
    $detectedHost = (docker context inspect --format '{{.Endpoints.docker.Host}}' 2>$null | Out-String).Trim()
} catch {
    $detectedHost = ''
}

if ([string]::IsNullOrWhiteSpace($detectedHost)) {
    Write-Host "ERROR: Docker no respondio. Esta Docker Desktop abierto y corriendo?" -ForegroundColor Red
    Write-Host "       Abrilo, espera a que diga 'running', y volve a intentar." -ForegroundColor Red
    exit 1
}

Write-Host "    Docker host detectado: $detectedHost" -ForegroundColor Green

# 3. Ruta del archivo en la home del usuario actual
$propsPath   = Join-Path $HOME '.testcontainers.properties'
$desiredLine = "docker.host=$detectedHost"

# 4. Crear o actualizar, preservando cualquier otra config que ya tengas
if (Test-Path $propsPath) {
    $lines   = @(Get-Content $propsPath)
    $current = $lines | Where-Object { $_ -match '^\s*docker\.host\s*=' } | Select-Object -First 1

    if ($current -and $current.Trim() -eq $desiredLine) {
        Write-Host "==> Ya estaba configurado correctamente. Nada que hacer. OK" -ForegroundColor Green
        exit 0
    }

    if ($current) {
        # Reemplaza solo la linea docker.host; el resto queda intacto
        $newLines = $lines | ForEach-Object {
            if ($_ -match '^\s*docker\.host\s*=') { $desiredLine } else { $_ }
        }
    } else {
        # Agrega la linea sin tocar lo demas
        $newLines = $lines + $desiredLine
    }
    Set-Content -Path $propsPath -Value $newLines -Encoding ascii
    Write-Host "==> Actualizado: $propsPath" -ForegroundColor Green
} else {
    Set-Content -Path $propsPath -Value $desiredLine -Encoding ascii
    Write-Host "==> Creado: $propsPath" -ForegroundColor Green
}

Write-Host ""
Write-Host "Listo. Ahora los tests de integracion deberian levantar los contenedores solos." -ForegroundColor Cyan
Write-Host "Probalo con:  cd backend; ./mvnw test" -ForegroundColor Cyan
