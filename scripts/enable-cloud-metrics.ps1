param(
    [Parameter(Mandatory=$true)][string]$RemoteWriteUrl,
    [Parameter(Mandatory=$true)][string]$MetricsInstanceId
)
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))
$uri = [Uri]$RemoteWriteUrl
if ($uri.Scheme -ne 'https' -or $uri.Host -notmatch '(^|\.)grafana\.net$' -or $uri.AbsolutePath -ne '/api/prom/push') {
    throw 'Use the exact HTTPS remote_write URL ending in /api/prom/push from your Grafana Cloud metrics details.'
}
if ($MetricsInstanceId -notmatch '^\d+$') { throw 'MetricsInstanceId must be the numeric ID from Grafana Cloud.' }
$secure = Read-Host 'Grafana Cloud metrics:write access policy token' -AsSecureString
if ($secure.Length -eq 0) { throw 'Token was empty.' }
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
$secretDir = Join-Path (Get-Location) '.secrets'
[IO.Directory]::CreateDirectory($secretDir) | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText((Join-Path $secretDir 'grafana-cloud-metrics-token'), $token, $utf8)
$token = $null
$local = [IO.File]::ReadAllText((Resolve-Path 'observability/prometheus/prometheus.yml'))
$cloud = @"

# Send only PayGuard and HTTP request series to Grafana Cloud.
remote_write:
  - url: '$RemoteWriteUrl'
    basic_auth:
      username: '$MetricsInstanceId'
      password_file: /etc/prometheus/cloud-token
    write_relabel_configs:
      - source_labels: [__name__]
        regex: 'payguard.*|http_server_requests_seconds.*|up'
        action: keep
"@
[IO.File]::WriteAllText((Join-Path (Get-Location) 'observability/prometheus/prometheus-cloud.yml'), $local.TrimEnd() + $cloud + "`n", $utf8)
Write-Host 'Cloud configuration saved locally. Start PayGuard to forward metrics; never commit .secrets or prometheus-cloud.yml.'
