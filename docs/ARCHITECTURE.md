# PayGuard Architecture



## 1. Overview



PayGuard is a local-first payment reliability platform designed to demonstrate backend engineering, DevOps, Kubernetes, observability, and SRE practices.



The system combines:



- Java 21 and Spring Boot

- PostgreSQL 17

- Flyway database migrations

- Docker and Docker Compose

- Kubernetes and Minikube

- Prometheus

- Grafana

- Loki

- Grafana Alloy

- GitHub Actions

- Jenkins

- Terraform

- Ansible



The architecture is intentionally designed to demonstrate failure handling, recovery, persistence, reconciliation, observability, and deployment automation rather than only CRUD operations.



---



## 2. High-Level Architecture



```text

                         Client

                           |

                           v

                  +------------------+

                  |  Payment API     |

                  |  Spring Boot     |

                  +--------+---------+

                           |

             +-------------+-------------+

             |                           |

             v                           v

    +------------------+       +------------------+

    | PostgreSQL 17    |       | Fake Payment     |

    | Payment State    |       | Provider         |

    +------------------+       +------------------+

             ^

             |

             |

    +----------------------+

    | Reconciliation       |

    | Worker               |

    +----------------------+

## 3. Payment Service

The main application is implemented using Java 21 and Spring Boot under `payment-service/`.

Primary responsibilities include:

- Creating and retrieving payments
- Idempotency handling
- Payment state management
- PostgreSQL persistence
- Payment-provider communication
- Provider failure simulation
- Reconciliation of uncertain payments
- Application metrics
- Health endpoints
- Centralized exception handling

Important components include:

```text
PaymentController
PaymentService
PaymentTransactionService
PaymentRepository
PaymentReconciliationWorker
PaymentMetrics
FailureSimulationController
GlobalExceptionHandler
FakePaymentProvider
```

## 4. Payment Processing Flow

```text
Client
   |
   v
PaymentController
   |
   v
PaymentService
   |
   +------> Idempotency handling
   |
   v
PaymentTransactionService
   |
   +------> PostgreSQL
   |
   v
PaymentProvider
   |
   v
FakePaymentProvider
   |
   v
Provider result
   |
   v
Update payment state
   |
   v
PostgreSQL
   |
   v
API Response
```

Idempotency protects the payment operation from accidental duplicate requests using the same idempotency key.

## 5. Database

PayGuard uses PostgreSQL 17 for durable payment state.

Schema changes are managed using Flyway.

Current migrations:

```text
V1__create_payments_table.sql
V2__add_reconciliation_metadata.sql
V3__backfill_exhausted_payments.sql
```

Hibernate schema validation is used with Flyway-managed schema evolution.

This keeps database changes explicit and reproducible instead of allowing Hibernate to modify production-style schemas automatically.

## 6. Payment Reconciliation

Payment-provider communication may fail or produce a state requiring later verification.

PayGuard therefore includes a reconciliation workflow.

```text
Payment requiring reconciliation
              |
              v
PaymentReconciliationWorker
              |
              v
Provider status lookup
              |
              v
Update reconciliation metadata
              |
              v
Update payment state
              |
              v
PostgreSQL
```

The payment model tracks:

- Reconciliation attempts
- Next reconciliation time
- Reconciliation exhaustion state

This models a realistic payment system where provider communication cannot always be treated as immediately successful or failed.

## 7. Docker Architecture

Docker Compose provides the complete local development environment.

Services include:

```text
payguard-payment-service
payguard-postgres
payguard-prometheus
payguard-grafana
payguard-loki
payguard-alloy
```

Current host ports:

| Component | Port |
|---|---:|
| Payment Service | 8081 |
| PostgreSQL | 15432 |
| Grafana | 3000 |
| Prometheus | 9090 |
| Loki | 3100 |

Inside the container network, services communicate using Docker DNS/service names rather than host ports.

For example, containerized PostgreSQL remains reachable on port `5432`.

## 8. Kubernetes Architecture

PayGuard also runs locally on Minikube.

Main application namespace:

```text
payguard
```

Architecture:

```text
+------------------------------------------------+
|               Namespace: payguard              |
|                                                |
|  +------------------------------------------+  |
|  | payment-service Deployment               |  |
|  |                                          |  |
|  | Startup Probe                            |  |
|  | Readiness Probe                          |  |
|  | Liveness Probe                           |  |
|  +--------------------+---------------------+  |
|                       |                        |
|                       v                        |
|             payment-service Service            |
|                                                |
|  +------------------------------------------+  |
|  | PostgreSQL Deployment                    |  |
|  |                                          |  |
|  | Readiness: pg_isready                    |  |
|  | Liveness:  pg_isready                    |  |
|  +--------------------+---------------------+  |
|                       |                        |
|                       v                        |
|                 postgres Service               |
|                       |                        |
|                       v                        |
|                 postgres-data PVC              |
+------------------------------------------------+
```

## 9. Kubernetes Health Management

The payment service uses three Kubernetes health mechanisms.

### Startup Probe

Allows Spring Boot enough time to initialize before Kubernetes begins normal health evaluation.

### Readiness Probe

Controls whether the pod is ready to receive traffic.

### Liveness Probe

Allows Kubernetes to detect an unhealthy application and restart it.

PostgreSQL uses:

```text
pg_isready
```

for readiness and liveness verification.

## 10. Kubernetes Self-Healing

Self-healing was manually tested.

A running payment-service pod was deliberately deleted.

The Deployment controller automatically created a replacement pod and restored the desired state.

The replacement reached:

```text
READY: 1/1
STATUS: Running
```

This demonstrates the difference between managing an individual container and declaring the desired application state through Kubernetes.

## 11. Horizontal Scaling

The payment service was manually scaled from one replica to two replicas.

Both replicas became healthy:

```text
payment-service ... 1/1 Running
payment-service ... 1/1 Running
```

After testing, the Deployment was returned to one replica to conserve local resources.

The architecture keeps durable payment state in PostgreSQL so application replicas remain replaceable.

## 12. Rolling Deployment and Rollback

A pod-template environment change was used to trigger a real Kubernetes rollout.

Kubernetes created a new ReplicaSet and gradually replaced the previous application pods.

Rollout history showed multiple revisions.

A rollback was then performed using:

```text
kubectl rollout undo deployment/payment-service -n payguard
```

Kubernetes restored the previous ReplicaSet successfully.

The Deployment was subsequently returned to its normal configuration.

## 13. Persistent Storage

PostgreSQL uses the `postgres-data` PersistentVolumeClaim.

Verified configuration:

```text
Name: postgres-data
Capacity: 1Gi
Access Mode: RWO
Storage Class: standard
```

Persistence was tested independently of the application lifecycle.

An existing payment was retrieved successfully after the payment-service pod was deleted and replaced.

This demonstrates separation between stateless application compute and durable database state.

## 14. Kubernetes Configuration

Runtime configuration is provided using a Kubernetes ConfigMap.

Current configuration includes:

```text
DB_URL=jdbc:postgresql://postgres:5432/payguard
FAILURE_SIMULATION_ENABLED=true
```

Sensitive database values are supplied using the `payguard-db-secret` Kubernetes Secret.

The local real-secret manifest is excluded from Git:

```text
kubernetes/secret.yaml
```

The repository contains:

```text
kubernetes/secret.example.yaml
```

for documentation and manifest validation.

## 15. Observability Architecture

PayGuard provides both metrics and centralized logs.

### Metrics Flow

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

### Logging Flow

```text
Application / Container Logs
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

Grafana dashboards are provisioned from repository configuration rather than relying only on manually created dashboards.

## 16. SRE Architecture

Prometheus contains recording and alerting rules for payment reliability.

Recording rules include:

```text
payguard:sli_payment_requests:rate5m
payguard:sli_payment_errors:rate5m
payguard:sli_availability:ratio5m
payguard:sli_error:ratio5m
payguard:slo_availability:burn_rate5m
payguard:sli_latency_250ms:ratio5m
```

Configured alerts include:

```text
PayGuardAvailabilityBurnRateWarning
PayGuardAvailabilityBurnRateCritical
PayGuardLatencySLOViolation
```

This provides the basis for measuring reliability through SLIs and SLOs rather than only inspecting logs after an incident.

See `SRE.md` for details.

## 17. CI/CD Architecture

GitHub Actions provides repository CI.

```text
Push / Pull Request
        |
        v
     Checkout
        |
        v
 Java 21 Setup
        |
        v
 PostgreSQL Service
        |
        v
   Maven Tests
      /     \
     v       v
Docker     Kubernetes
Build      Validation
```

A Jenkins pipeline is also provided for demonstrating traditional pipeline-as-code.

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

GitHub Actions and Jenkins demonstrate two common CI pipeline approaches around the same application.

## 18. Terraform Architecture

Terraform uses the HashiCorp Kubernetes provider.

Terraform intentionally manages a separate namespace:

```text
payguard-iac
```

instead of immediately taking ownership of the existing `payguard` resources.

Current Terraform-managed resources include:

- Kubernetes namespace
- PayGuard ConfigMap
- Resource labels
- Terraform outputs

The following lifecycle was successfully tested:

```text
terraform init
terraform validate
terraform plan
terraform apply
terraform plan
```

After applying the resources, the second plan returned:

```text
No changes. Your infrastructure matches the configuration.
```

This demonstrates Terraform idempotency.

Terraform state is intentionally excluded from Git.

The provider lock file is retained for reproducible provider selection.

## 19. Ansible Architecture

Ansible is used as an operational verification layer.

Repository structure:

```text
ansible/
├── ansible.cfg
├── inventory.ini
└── playbooks/
    └── verify-payguard.yml
```

The verification playbook checks:

- kubectl availability
- Minikube status
- PayGuard namespace
- payment-service rollout
- PostgreSQL rollout
- PostgreSQL PVC
- Pod state

Ansible is intended to execute from a Linux/WSL control environment.

The playbook exists, but runtime execution remains pending because Ansible is not installed in the current Ubuntu WSL environment.

## 20. Resource-Conscious Design

PayGuard is designed to run on a development laptop rather than requiring a paid cloud environment.

Kubernetes resource requests and limits are defined for the main workloads.

The payment service currently uses:

```text
Request:
  CPU:    100m
  Memory: 256Mi

Limit:
  CPU:    750m
  Memory: 512Mi
```

PostgreSQL currently uses:

```text
Request:
  CPU:    100m
  Memory: 128Mi

Limit:
  CPU:    500m
  Memory: 384Mi
```

The application is normally returned to one replica after scaling demonstrations to reduce resource usage.

## 21. Separation of Concerns

PayGuard separates the platform into several engineering layers:

```text
Application
    |
    +--> Business / payment reliability logic
    |
Database
    |
    +--> Durable state + Flyway migrations
    |
Containers
    |
    +--> Reproducible runtime
    |
Kubernetes
    |
    +--> Desired state + recovery + scaling
    |
Observability
    |
    +--> Metrics + logs + dashboards
    |
SRE
    |
    +--> SLIs + SLOs + alerts
    |
CI/CD
    |
    +--> Automated validation
    |
Terraform
    |
    +--> Infrastructure as Code
    |
Ansible
    |
    +--> Operational verification
```

## 22. Architecture Goals

PayGuard demonstrates several production-oriented design principles:

1. Keep application instances replaceable.
2. Store durable state outside application containers.
3. Manage database evolution through migrations.
4. Protect payment operations from duplicate requests.
5. Model provider failures explicitly.
6. Reconcile uncertain payment states.
7. Expose health endpoints to the orchestrator.
8. Measure service behavior with metrics.
9. Centralize logs for troubleshooting.
10. Define reliability signals and alerts.
11. Automate application validation.
12. Represent infrastructure as code.
13. Make operational checks reproducible.
14. Keep local resource consumption manageable.

## 23. Verified Architecture Status

Verified locally:

- Java 21 / Spring Boot application
- PostgreSQL integration
- Flyway migrations
- Payment persistence
- Docker image
- Docker Compose
- Prometheus
- Grafana
- Loki
- Grafana Alloy
- Kubernetes Deployment
- Kubernetes Services
- Startup/readiness/liveness probes
- PostgreSQL PVC
- Kubernetes self-healing
- Payment persistence after pod replacement
- Horizontal replica scaling
- Rolling Deployment
- Rollback
- Terraform initialization
- Terraform validation
- Terraform provisioning
- Terraform idempotency

Defined but requiring runtime/external validation:

- GitHub Actions execution after repository push
- Jenkins pipeline execution on a Jenkins agent
- Ansible playbook execution after configuring the WSL control environment


