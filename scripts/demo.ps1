param([switch]$Kubernetes)
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))
$base = 'http://localhost:8081/api/v1/payments'
function Assert-Equal($label, $actual, $expected) {
    if ($actual -ne $expected) { throw "$label expected $expected but got $actual" }
    Write-Host "[PASS] $label : $actual"
}
$health = Invoke-RestMethod 'http://localhost:8081/actuator/health' -TimeoutSec 10
Assert-Equal 'API health' $health.status 'UP'
$body = @{ customerId='CUSTOMER-DEMO'; amount=149900; currency='INR' } | ConvertTo-Json
$normal = Invoke-RestMethod -Method POST -Uri $base -Headers @{'Idempotency-Key'="DEMO-$([guid]::NewGuid())"} -ContentType 'application/json' -Body $body
Assert-Equal 'normal payment' $normal.status 'SUCCESS'
$key = "DEMO-TIMEOUT-$([guid]::NewGuid())"
$unknown = Invoke-RestMethod -Method POST -Uri $base -Headers @{'Idempotency-Key'=$key} -ContentType 'application/json' -Body $body
Assert-Equal 'timeout after charge' $unknown.status 'UNKNOWN'
$retry = Invoke-RestMethod -Method POST -Uri $base -Headers @{'Idempotency-Key'=$key} -ContentType 'application/json' -Body $body
Assert-Equal 'same ID on retry' $retry.id $unknown.id
$recovered = $null
for ($i=1; $i -le 24; $i++) {
    $recovered = Invoke-RestMethod "$base/$($unknown.id)" -TimeoutSec 10
    if ($recovered.status -eq 'SUCCESS') { break }
    Start-Sleep -Seconds 5
}
Assert-Equal 'automatic reconciliation' $recovered.status 'SUCCESS'
$metrics = (Invoke-WebRequest -UseBasicParsing 'http://localhost:8081/actuator/prometheus' -TimeoutSec 10).Content
if ($metrics -notmatch 'payguard_') { throw 'PayGuard Prometheus metrics missing' }
Write-Host '[PASS] PayGuard metrics exposed'
$targets = Invoke-RestMethod 'http://localhost:9090/api/v1/targets' -TimeoutSec 10
if (-not @($targets.data.activeTargets | Where-Object { $_.health -eq 'up' }).Count) { throw 'No healthy Prometheus target' }
Write-Host '[PASS] Prometheus target UP'
$rules = Invoke-RestMethod 'http://localhost:9090/api/v1/rules' -TimeoutSec 10
if (-not @($rules.data.groups | ForEach-Object { $_.rules } | Where-Object { $_.name -eq 'payguard:sli_availability:ratio5m' }).Count) { throw 'Availability SLI rule missing' }
Write-Host '[PASS] SLI/SLO rules loaded'
$loki = Invoke-WebRequest -UseBasicParsing 'http://localhost:3100/ready' -TimeoutSec 10
Assert-Equal 'Loki ready HTTP' $loki.StatusCode 200
$grafana = Invoke-RestMethod 'http://localhost:3000/api/health' -TimeoutSec 10
Assert-Equal 'Grafana database' $grafana.database 'ok'
docker compose ps
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose status failed' }
if ($Kubernetes) {
    kubectl rollout status deployment/payment-service -n payguard --timeout=120s
    if ($LASTEXITCODE -ne 0) { throw 'Kubernetes payment-service rollout failed' }
    kubectl get pods,pvc -n payguard
    if ($LASTEXITCODE -ne 0) { throw 'Kubernetes resources unavailable' }
}
Write-Host "[PASS] PayGuard reliability demo complete. Payment ID: $($unknown.id)"
