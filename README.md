# PayGuard

PayGuard is a local-first payment reliability platform built to demonstrate backend engineering, DevOps, Kubernetes, observability, SRE practices, CI/CD, and Infrastructure as Code without requiring a paid cloud account.

The project models payment processing where duplicate requests, provider failures, retries, reconciliation, persistence, observability, deployment failures, and recovery must be handled reliably.

## What PayGuard Demonstrates

- Java 21 + Spring Boot payment API
- Lightweight Go companion probe in Compose and the Kubernetes payment pod
- Python automation for reliability demonstrations, monitoring export, and sidecar checks
- PostgreSQL 17 persistence
- Flyway database migrations
- Idempotent payment processing
- Simulated payment-provider failures
- Payment reconciliation workflow
- Prometheus application metrics
- Grafana dashboards
- Loki centralized logging
- Grafana Alloy log collection
- SLI/SLO recording and alerting rules
- Docker and Docker Compose
- Kubernetes on Minikube
- Liveness, readiness, and startup probes
- Kubernetes self-healing
- PersistentVolumeClaims
- Horizontal replica scaling
- Rolling deployments and rollback
- GitHub Actions CI
- Jenkins pipeline-as-code
- Terraform-managed Kubernetes resources
- Ansible operational verification playbook

## Architecture

```text
                    Client
                      |
                      v
              +----------------+
              | Payment API    |
              | Spring Boot    |
              +-------+--------+
                      |
          +-----------+-----------+
          |                       |
          v                       v
 +----------------+       +----------------+
 | PostgreSQL     |       | Fake Payment   |
 | Payment State  |       | Provider       |
 +----------------+       +----------------+
          ^
          |
 +----------------------+
 | Reconciliation Worker|
 +----------------------+

Observability:

Payment Service ---> Prometheus ---> Grafana
       |
       +-----------> Alloy ---> Loki ---> Grafana

Deployment:

Source
  |
  +--> GitHub Actions
  |
  +--> Jenkins
  |
Docker Image
  |
Kubernetes / Minikube
  |
  +--> payment-service
  +--> PostgreSQL + PVC

Infrastructure / Operations:

Terraform ---> Kubernetes resources
Ansible   ---> Operational verification
```

See `docs/ARCHITECTURE.md` for the detailed architecture.

## Technology Stack

| Area | Technology |
|---|---|
| Language | Java 21 |
| Companion | Go (standard library only) |
| Automation | Python 3 (standard library for demo and sidecar check) |
| Framework | Spring Boot |
| Build | Maven Wrapper |
| Database | PostgreSQL 17 |
| Database Migration | Flyway |
| Containers | Docker / Docker Compose |
| Orchestration | Kubernetes / Minikube |
| Metrics | Prometheus |
| Dashboards | Grafana |
| Logs | Loki + Grafana Alloy |
| CI | GitHub Actions |
| Pipeline | Jenkins |
| Infrastructure as Code | Terraform |
| Configuration / Operations | Ansible |

## Go companion and Python automation

`sidecar/` contains a small Go HTTP health observer. Every five seconds it checks the Spring Boot health endpoint and exposes `/healthz` and Prometheus `/metrics` on port 9101. It shares the API network namespace in Compose and its pod network in Kubernetes. It does not process payments, reconcile records, or restart the API; Kubernetes and Compose own restarts. Its counters reset when the companion restarts. The Compose host mapping is restricted to `127.0.0.1`.

`scripts/ci-demo.py` checks real payment, timeout, idempotency and reconciliation behavior. `scripts/export-monitoring.py` exports dated monitoring evidence. `scripts/check-sidecar.py` verifies the running Go companion in the hosted Compose workflow. Hosted monitoring still runs temporarily on GitHub Actions and publishes a dated capture; the public site is not a permanent API or monitoring server.

The hosted workflow builds both images and exercises the companion. CI builds and checks its Go code. Kubernetes manifests include the companion, but this change has only schema validation there; a live Minikube rollout is a separate runtime check.

## Payment Reliability

PayGuard is designed around payment reliability rather than simple CRUD operations.

### Idempotency

Payment requests use an idempotency key to protect against accidental duplicate payment creation.

### Provider Failure Simulation

The fake payment provider allows failure scenarios to be reproduced locally without requiring an external payment gateway.

### Reconciliation

PayGuard includes a reconciliation worker for payments requiring follow-up processing.

Reconciliation metadata includes:

- Reconciliation attempts
- Next reconciliation time
- Reconciliation exhaustion state

Database evolution is managed through:

```text
V1__create_payments_table.sql
V2__add_reconciliation_metadata.sql
V3__backfill_exhausted_payments.sql
```

## One-command Windows operation

From the repository root in PowerShell:

```powershell
.\scripts\start.ps1
.\scripts\status.ps1
.\scripts\run-demo.ps1
.\scripts\stop.ps1
```

Use `start.ps1 -Kubernetes` to also start Minikube. `stop.ps1` stops Compose and Minikube by default; use `-KeepMinikube` to leave the cluster running. Stopping retains persistent volumes; neither command deletes database data. `start.ps1` requires a local `.env` containing `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`, and generates `GRAFANA_ADMIN_PASSWORD` there if missing. Keep that file out of Git. The local demo checks Compose services by default. Use `run-demo.ps1 -Kubernetes` to also verify the Minikube payment-service rollout. On a fresh machine, `start.ps1` builds the application image if it is missing.

## Portfolio site and hosted demonstration

The static [PayGuard portfolio site](https://chandanvura.github.io/PayGuard/) has separate [architecture](https://chandanvura.github.io/PayGuard/architecture.html), [learning](https://chandanvura.github.io/PayGuard/learning.html), [monitoring](https://chandanvura.github.io/PayGuard/monitoring.html), and [demo](https://chandanvura.github.io/PayGuard/demo.html) pages. It is deployed daily by `.github/workflows/hosted-monitoring.yml` with real, timestamped [Prometheus readings and Grafana capture](https://chandanvura.github.io/PayGuard/observability/latest/prometheus.html). Repository settings must have **Pages → Build and deployment → Source: GitHub Actions**.

The [Reliability demo workflow](https://github.com/chandanvura/PayGuard/actions/workflows/reliability-demo.yml) can be launched with **Run workflow**. It starts a temporary PostgreSQL service and Spring Boot process on a GitHub runner, verifies the timeout, idempotent retry, automatic reconciliation, health, and metrics, then publishes a run summary and 30-day evidence artifact. No laptop or paid host is involved. GitHub-hosted CI is a short-lived demonstration, not a persistent live payment API. The fake provider never charges real money.

## Running with Docker Compose

Start the platform (the script creates a Grafana password in your ignored `.env` if needed):

```powershell
.\scripts\start.ps1
```

Check the containers:

```powershell
docker compose ps
```

Current local services:

| Service | Address |
|---|---|
| Payment Service | http://localhost:8081 |
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |
| Loki | http://localhost:3100 |
| PostgreSQL | localhost:15432 |

Check application health:

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
```

Stop the environment:

```powershell
docker compose down
```

## Kubernetes

PayGuard runs locally using Minikube.

Start Minikube:

```powershell
minikube start
```

Verify the cluster:

```powershell
kubectl get nodes
kubectl get pods -n payguard
kubectl get pvc -n payguard
```

The payment service has startup, readiness, and liveness probes.

PostgreSQL uses `pg_isready` for readiness and liveness checks.

### Kubernetes Reliability Tests

The environment has been manually tested for:

- Pod deletion and automatic recreation
- Payment persistence after application pod replacement
- PostgreSQL PVC persistence
- Scaling from one to two payment-service replicas
- Rolling Deployment updates
- Deployment rollback
- Scaling back to one replica

Example scaling:

```powershell
kubectl scale deployment payment-service -n payguard --replicas=2
kubectl rollout status deployment/payment-service -n payguard
```

Rollback:

```powershell
kubectl rollout undo deployment/payment-service -n payguard
```

## Persistent Storage

PostgreSQL uses the `postgres-data` PersistentVolumeClaim.

Verified configuration:

```text
Capacity: 1Gi
Access Mode: RWO
Storage Class: standard
```

Durability was tested by retrieving an existing payment successfully after replacing the application pod.

## Optional online Grafana dashboard

To host the **real Grafana panels** for public viewing, follow [Grafana Cloud setup](docs/GRAFANA-CLOUD.md). The free Cloud stack receives selected metrics through Prometheus `remote_write` when PayGuard is running locally. Import `observability/grafana/payguard-cloud-dashboard.json`, then share the Grafana dashboard externally. This needs your Grafana Cloud account, metrics instance ID, and a private `metrics:write` token; no credentials belong in this repository. GitHub Pages itself cannot host Prometheus or Grafana servers.

## Observability

PayGuard includes a complete local metrics and logging stack.

### Metrics

```text
Spring Boot
    |
    v
Micrometer
    |
    v
Prometheus
    |
    v
Grafana
```

### Logs

```text
Container Logs
     |
     v
Grafana Alloy
     |
     v
Loki
     |
     v
Grafana
```

Provisioned dashboards include:

```text
payguard-overview.json
payguard-logs.json
```

## SLI and SLO Monitoring

Prometheus recording rules include:

```text
payguard:sli_payment_requests:rate5m
payguard:sli_payment_errors:rate5m
payguard:sli_availability:ratio5m
payguard:sli_error:ratio5m
payguard:slo_availability:burn_rate5m
payguard:sli_latency_250ms:ratio5m
```

Alerts include:

```text
PayGuardAvailabilityBurnRateWarning
PayGuardAvailabilityBurnRateCritical
PayGuardLatencySLOViolation
```

See `docs/SRE.md` for the SRE design.

## Testing

Run the Java test suite:

```powershell
cd payment-service
.\mvnw.cmd clean test
```

Test classes include:

```text
PaymentServiceApplicationTests
PaymentServiceIntegrationTest
```

## GitHub Actions

The repository contains:

```text
.github/workflows/ci.yml
```

The CI workflow performs:

```text
Checkout
   |
   v
Java 21 Setup
   |
   v
Maven Tests
   |
   +-----------> Docker Build
   |
   +-----------> Kubernetes Manifest Validation
```

## Jenkins

The repository also contains a `Jenkinsfile`.

Pipeline stages:

```text
Checkout
   |
   v
Test
   |
   v
Package
   |
   v
Docker Build
   |
   v
Kubernetes Validation
   |
   v
JUnit + Artifact Archival
```

See `docs/CI-CD.md`.

## Terraform

Terraform uses the HashiCorp Kubernetes provider.

Terraform intentionally manages an isolated namespace:

```text
payguard-iac
```

This prevents the IaC demonstration from taking ownership of the manually deployed `payguard` environment.

Verified Terraform workflow:

```powershell
terraform init
terraform validate
terraform plan
terraform apply
terraform plan
```

After applying the configuration, the final plan returned:

```text
No changes. Your infrastructure matches the configuration.
```

Terraform state and the `.terraform` directory are excluded from Git.

The Terraform provider lock file is retained for reproducible provider selection.

## Ansible

The repository contains:

```text
ansible/
├── ansible.cfg
├── inventory.ini
└── playbooks/
    └── verify-payguard.yml
```

The playbook is designed to verify:

- kubectl availability
- Minikube status
- PayGuard namespace
- payment-service rollout
- PostgreSQL rollout
- PostgreSQL PVC
- Application pods

The playbook is intended to execute from a Linux/WSL Ansible control environment.

Ansible operational verification has been runtime-tested successfully from the configured control environment. See `docs/CI-CD.md` for the scope of that check.

## Secrets and Repository Safety

Local Compose ports bind to `127.0.0.1`; they are unavailable to other machines on the network. Grafana admin credentials belong in `.env` (`GRAFANA_ADMIN_PASSWORD`), which is ignored by Git. On an existing Grafana volume, changing the environment variable does not reset the existing admin account. To rotate an existing Grafana password after `start.ps1`, run:

```powershell
$grafanaPassword = (Get-Content .env | Where-Object { $_ -match "^GRAFANA_ADMIN_PASSWORD=" } | Select-Object -Last 1) -replace "^GRAFANA_ADMIN_PASSWORD=", ""
docker compose exec -T grafana grafana cli admin reset-admin-password $grafanaPassword
Remove-Variable grafanaPassword
```

The local Kubernetes Secret is excluded from Git:

```text
kubernetes/secret.yaml
```

The repository contains the example configuration:

```text
kubernetes/secret.example.yaml
```

The following should never be committed:

- Real passwords
- API tokens
- `.env` files
- Terraform state
- Kubernetes kubeconfig files
- Private credentials

## Repository Structure

```text
PayGuard/
├── .github/
│   └── workflows/
│       └── ci.yml
├── ansible/
│   └── playbooks/
├── docs/
│   └── runbooks/
├── kubernetes/
│   ├── payment-service/
│   └── postgres/
├── observability/
│   ├── alloy/
│   ├── grafana/
│   ├── loki/
│   └── prometheus/
├── payment-service/
├── terraform/
├── docker-compose.yml
├── Jenkinsfile
└── README.md
```

## Engineering Questions Demonstrated

PayGuard is designed to demonstrate practical engineering questions:

- How do we prevent duplicate payment processing?
- What happens when a payment provider fails?
- How are uncertain payments reconciled?
- What happens when an application pod dies?
- Does payment data survive pod replacement?
- Can the application scale horizontally?
- Can Kubernetes perform a rolling deployment?
- Can a failed deployment be rolled back?
- How are availability and latency measured?
- How are logs centralized?
- How can infrastructure be represented as code?
- How can operational verification be automated?

## Documentation

Detailed documentation is available in:

- `docs/ARCHITECTURE.md`
- `docs/SRE.md`
- `docs/CI-CD.md`
- `docs/runbooks/OPERATIONS.md`

## Current Status

### Verified Locally

- Spring Boot payment service
- PostgreSQL integration
- Flyway migrations
- Payment persistence
- Idempotency implementation
- Failure simulation
- Reconciliation functionality
- Automated Java tests
- Docker image
- Docker Compose environment
- Prometheus
- Grafana
- Loki
- Grafana Alloy
- Kubernetes deployment
- Health probes
- Kubernetes self-healing
- PVC persistence
- Replica scaling
- Rolling update
- Rollback
- Terraform provisioning
- Terraform idempotency
- Ansible operational verification runtime

### Defined but Pending Runtime Validation

- GitHub Actions reliability demo and Pages deployment, successfully executed on commit `105a1fa`
- Jenkins execution on an actual Jenkins agent

## Purpose

PayGuard is a portfolio project focused on demonstrating how backend development, DevOps, observability, Infrastructure as Code, and SRE practices work together around a realistic payment-reliability problem.
