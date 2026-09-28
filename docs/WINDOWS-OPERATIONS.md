# Windows start, verify, and stop

Run these commands in **Windows PowerShell**, with Docker Desktop running. A PowerShell prompt begins with `PS`. If your prompt begins with `C:\`, enter `powershell -NoProfile` first. Copy commands without the prompt.

## Fresh checkout

```powershell
cd "$HOME\OneDrive\Desktop"
git clone https://github.com/chandanvura/PayGuard.git
cd .\PayGuard
```

If you already cloned the repository, enter its actual directory instead (for example, `cd "$HOME\OneDrive\Desktop\PayGuard\PayGuard"` after cloning from inside an older PayGuard folder), then run `git pull --ff-only`. Confirm that `docker-compose.yml` and `scripts\start.ps1` exist in the current directory.

## Create local credentials only for a new database

First check for an existing credentials file and database volume:

```powershell
Test-Path .env
docker volume ls --format '{{.Name}}' | Select-String 'payguard.*postgres'
```

If `.env` already exists, retain it. If **neither** `.env` nor an old PayGuard PostgreSQL volume exists, create fresh credentials:

```powershell
$dbPassword = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
$grafanaPassword = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
@(
  'POSTGRES_DB=payguard'
  'POSTGRES_USER=payguard'
  "POSTGRES_PASSWORD=$dbPassword"
  "GRAFANA_ADMIN_PASSWORD=$grafanaPassword"
) | Set-Content .env -Encoding ASCII
Remove-Variable dbPassword,grafanaPassword
```

If the volume exists but `.env` is missing, **do not create new credentials and start Compose**: PostgreSQL keeps its earlier password in the volume. Recover the original `.env` if available. Otherwise, the procedure below resets only the database role's password and retains the data. Never commit or share `.env`.

## Existing volume with missing `.env`

Ensure there is no running PayGuard PostgreSQL container (`docker ps -a --filter "name=payguard-postgres"`). This procedure assumes the existing database and role are both named `payguard`. It starts a temporary PostgreSQL 17 container without publishing a port, changes the role password through the local socket, writes a new local `.env`, then stops the temporary container. Stop if any command fails. It does not delete the volume.

```powershell
if (Test-Path .env) { throw 'Keep the existing .env; do not reset credentials.' }
if (docker ps -a --filter 'name=payguard-postgres' --format '{{.Names}}') { throw 'Inspect the existing PostgreSQL container first.' }
$dbPassword = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
$grafanaPassword = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
docker run --rm -d --name payguard-recovery `
  -v payguard_payguard-postgres-data:/var/lib/postgresql/data `
  postgres:17-alpine
if ($LASTEXITCODE -ne 0) { throw 'Recovery container did not start' }
try {
  $ready = $false
  for ($i = 0; $i -lt 30; $i++) {
    docker exec payguard-recovery pg_isready -U payguard -d payguard *> $null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 2
  }
  if (-not $ready) { throw 'Old database did not become ready' }
  "ALTER ROLE payguard WITH PASSWORD '$dbPassword';" |
    docker exec -i payguard-recovery psql -U payguard -d payguard -v ON_ERROR_STOP=1
  if ($LASTEXITCODE -ne 0) { throw 'Password reset failed' }
  @(
    'POSTGRES_DB=payguard'
    'POSTGRES_USER=payguard'
    "POSTGRES_PASSWORD=$dbPassword"
    "GRAFANA_ADMIN_PASSWORD=$grafanaPassword"
  ) | Set-Content .env -Encoding ASCII
} finally {
  docker stop payguard-recovery | Out-Null
}
Remove-Variable dbPassword,grafanaPassword
```

The temporary container uses local socket authentication. If authentication fails, stop and diagnose the existing database configuration; never delete the volume as a workaround. An old Grafana volume may retain its previous admin password even after `.env` changes; see [README security instructions](../README.md#secrets-and-repository-safety) to rotate it.

## Start and verify

From the repository root:

```powershell
.\scripts\start.ps1
.\scripts\run-demo.ps1
```

The start script builds the Java and Go images when absent. The demo waits for API startup and should print `[PASS]` for normal payment, timeout and reconciliation, Go companion health, both Prometheus targets, Loki, and Grafana. The API is at <http://localhost:8081>, Grafana at <http://localhost:3000>, Prometheus at <http://localhost:9090>, and the companion health endpoint at <http://localhost:9101/healthz>. If a check fails, inspect `docker compose ps` and `docker compose logs --tail=100 payment-service sidecar postgres`.

Minikube is optional. Use `start.ps1 -Kubernetes` only when you intend to start the cluster, and `run-demo.ps1 -Kubernetes` to also check its payment-service rollout. A Kubernetes rollout also requires the manifests, images, and secrets described in the [Kubernetes instructions](../README.md#kubernetes); starting Minikube alone does not deploy PayGuard there.

## Stop and return later

```powershell
.\scripts\stop.ps1 -KeepMinikube
```

This stops Compose and retains the existing Minikube cluster. To stop Minikube as well, use `.\scripts\stop.ps1` without the switch. The script preserves Docker volumes and database data; **do not use `docker compose down -v`** if you want to retain them. To restart later, enter the repository directory, run `git pull --ff-only`, then `./scripts/start.ps1` in PowerShell (`.\scripts\start.ps1` also works).
