param([switch]$KeepMinikube)
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))
if (Get-Command docker -ErrorAction SilentlyContinue) {
    docker info *> $null
    if ($LASTEXITCODE -eq 0) {
        docker compose down
        if ($LASTEXITCODE -ne 0) { throw 'Docker Compose stop failed.' }
    } else { Write-Host 'Docker engine is unavailable; Compose containers are already inaccessible.' }
} else { Write-Host 'Docker command unavailable; skipping Compose.' }
if (-not $KeepMinikube -and (Get-Command minikube -ErrorAction SilentlyContinue)) {
    minikube status *> $null
    if ($LASTEXITCODE -eq 0) {
        minikube stop
        if ($LASTEXITCODE -ne 0) { throw 'Minikube stop failed.' }
    } else { Write-Host 'Minikube is not running.' }
}
Write-Host 'PayGuard stopped. Persistent Docker volumes and Kubernetes data were retained.'
