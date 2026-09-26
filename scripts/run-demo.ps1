param([switch]$Kubernetes)
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))
& (Join-Path $PSScriptRoot 'demo.ps1') -Kubernetes:$Kubernetes
if (-not $?) { throw 'PayGuard demonstration failed.' }
