param([switch]$StopMinikube)
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))
docker compose down
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose stop failed.' }
if ($StopMinikube) {
    minikube stop
    if ($LASTEXITCODE -ne 0) { throw 'Minikube stop failed.' }
}
Write-Host 'Stopped. Persistent Docker volumes and Kubernetes data were retained.'
