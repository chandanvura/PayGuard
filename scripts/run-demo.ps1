$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..'))
& (Join-Path $PSScriptRoot 'demo.ps1')
if (-not $?) { throw 'PayGuard demonstration failed.' }
