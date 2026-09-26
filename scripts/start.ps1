param([switch]$Kubernetes)
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))
if (-not (Test-Path .env)) { throw 'Missing .env. Create it locally with POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD; never commit it.' }
docker compose up -d
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose failed.' }
if ($Kubernetes) {
    minikube start
    if ($LASTEXITCODE -ne 0) { throw 'Minikube failed.' }
}
& (Join-Path $PSScriptRoot 'status.ps1')
