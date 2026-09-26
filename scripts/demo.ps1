$ErrorActionPreference = "Stop"

$base = "http://localhost:8081/api/v1/payments"

Write-Host "`n======================================"
Write-Host "       PAYGUARD RELIABILITY DEMO"
Write-Host "======================================"

# ------------------------------------------------
# 1. PLATFORM HEALTH
# ------------------------------------------------

Write-Host "`n[1] Application Health"

$health = Invoke-RestMethod "http://localhost:8081/actuator/health"
Write-Host "Spring Boot:" $health.status

# ------------------------------------------------
# 2. NORMAL PAYMENT
# ------------------------------------------------

Write-Host "`n[2] Normal Payment"

$normalKey = "DEMO-$([guid]::NewGuid())"

$body = @{
    customerId = "CUSTOMER-DEMO"
    amount     = 149900
    currency   = "INR"
} | ConvertTo-Json

$normal = Invoke-RestMethod `
    -Method POST `
    -Uri $base `
    -Headers @{ "Idempotency-Key" = $normalKey } `
    -ContentType "application/json" `
    -Body $body

Write-Host "Payment ID :" $normal.id
Write-Host "Status     :" $normal.status

# ------------------------------------------------
# 3. DISTRIBUTED SYSTEM FAILURE
# ------------------------------------------------

Write-Host "`n[3] Simulating Provider Timeout AFTER Charge"

$timeoutKey = "DEMO-TIMEOUT-$([guid]::NewGuid())"

$timeout = Invoke-RestMethod `
    -Method POST `
    -Uri $base `
    -Headers @{ "Idempotency-Key" = $timeoutKey } `
    -ContentType "application/json" `
    -Body $body

Write-Host "Payment ID :" $timeout.id
Write-Host "Initial    :" $timeout.status

# ------------------------------------------------
# 4. WAIT FOR RECONCILIATION
# ------------------------------------------------

Write-Host "`n[4] Waiting for automatic reconciliation..."

$recovered = $null

for ($i = 1; $i -le 8; $i++) {

    Start-Sleep -Seconds 5

    $recovered = Invoke-RestMethod "$base/$($timeout.id)"

    Write-Host "Attempt $i ->" $recovered.status

    if ($recovered.status -eq "SUCCESS") {
        break
    }
}

Write-Host "`nFinal Status            :" $recovered.status
Write-Host "Reconciliation Attempts :" $recovered.reconciliationAttempts

# ------------------------------------------------
# 5. IDEMPOTENCY
# ------------------------------------------------

Write-Host "`n[5] Retrying SAME payment"

$retry = Invoke-RestMethod `
    -Method POST `
    -Uri $base `
    -Headers @{ "Idempotency-Key" = $timeoutKey } `
    -ContentType "application/json" `
    -Body $body

Write-Host "Original ID :" $timeout.id
Write-Host "Retry ID    :" $retry.id
Write-Host "Same ID     :" ($timeout.id -eq $retry.id)

# ------------------------------------------------
# 6. PROMETHEUS
# ------------------------------------------------

Write-Host "`n[6] Prometheus Target"

$targets = Invoke-RestMethod "http://localhost:9090/api/v1/targets"

$targets.data.activeTargets |
    Select-Object @{N="Job";E={$_.labels.job}},health |
    Format-Table -AutoSize

# ------------------------------------------------
# 7. SLO / ALERTS
# ------------------------------------------------

Write-Host "`n[7] SLI / SLO Rules"

$rules = Invoke-RestMethod "http://localhost:9090/api/v1/rules"

$rules.data.groups |
    ForEach-Object { $_.rules } |
    Select-Object name,type,health,state |
    Format-Table -AutoSize

# ------------------------------------------------
# 8. LOKI
# ------------------------------------------------

Write-Host "`n[8] Loki"

$loki = (Invoke-WebRequest `
    -UseBasicParsing `
    "http://localhost:3100/ready").StatusCode

Write-Host "Loki HTTP:" $loki

# ------------------------------------------------
# 9. GRAFANA
# ------------------------------------------------

Write-Host "`n[9] Grafana"

$grafana = Invoke-RestMethod "http://localhost:3000/api/health"

Write-Host "Grafana DB:" $grafana.database

# ------------------------------------------------
# 10. DOCKER
# ------------------------------------------------

Write-Host "`n[10] Docker"

docker compose ps

# ------------------------------------------------
# 11. KUBERNETES
# ------------------------------------------------

Write-Host "`n[11] Kubernetes"

kubectl get pods,pvc -n payguard

# ------------------------------------------------
# RESULT
# ------------------------------------------------

Write-Host "`n======================================"
Write-Host "       PAYGUARD DEMO COMPLETE"
Write-Host "======================================"

Write-Host @"

Demonstrated:

âœ“ Spring Boot REST API
âœ“ PostgreSQL persistence
âœ“ Payment processing
âœ“ Distributed-system uncertainty
âœ“ Automatic reconciliation
âœ“ Idempotency / duplicate protection
âœ“ Docker
âœ“ Kubernetes
âœ“ Prometheus
âœ“ SLI / SLO
âœ“ Loki
âœ“ Grafana

"@
