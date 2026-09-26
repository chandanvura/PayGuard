param([switch]$Kubernetes)
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))
if (-not (Test-Path .env)) { throw 'Missing .env. Create it locally with POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD; never commit it.' }
if (-not (Select-String -Path .env -Pattern '^GRAFANA_ADMIN_PASSWORD=' -Quiet)) {
    $random = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Fill($random)
    $password = [Convert]::ToHexString($random)
    Add-Content -Path .env -Value "GRAFANA_ADMIN_PASSWORD=$password"
    Write-Host 'Generated a local Grafana admin password in .env.'
}
if (-not (docker image inspect payguard-payment-service:local 2>$null)) {
    docker build -t payguard-payment-service:local ./payment-service
    if ($LASTEXITCODE -ne 0) { throw 'Payment service image build failed.' }
}
if (Test-Path observability/prometheus/prometheus-cloud.yml) {
    if (-not (Test-Path .secrets/grafana-cloud-metrics-token)) { throw 'Cloud token missing; rerun enable-cloud-metrics.ps1.' }
    docker compose -f docker-compose.yml -f docker-compose.cloud.yml up -d
} else {
    docker compose up -d
}
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose failed.' }
if ($Kubernetes) {
    minikube start
    if ($LASTEXITCODE -ne 0) { throw 'Minikube failed.' }
}
& (Join-Path $PSScriptRoot 'status.ps1')
