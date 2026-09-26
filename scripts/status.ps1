$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))
docker compose ps
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose status failed.' }
foreach ($item in @(@('API','http://localhost:8081/actuator/health'),@('Grafana','http://localhost:3000/api/health'),@('Prometheus','http://localhost:9090/-/ready'),@('Loki','http://localhost:3100/ready'))) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $item[1] -TimeoutSec 4
        Write-Host "$($item[0]): HTTP $($response.StatusCode)"
    } catch { Write-Host "$($item[0]): unavailable ($($_.Exception.Message))" }
}
if (Get-Command minikube -ErrorAction SilentlyContinue) {
    minikube status 2>$null
}
