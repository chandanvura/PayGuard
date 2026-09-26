# PayGuard SRE and Reliability



## 1. Purpose



PayGuard applies Site Reliability Engineering practices to a payment-processing workload.



The goal is not only to determine whether the application is running, but also to measure whether payments are being processed reliably and within acceptable latency.



The SRE layer covers:



- Service health

- Payment availability

- Payment errors

- Request latency

- SLIs

- SLOs

- Error budgets

- Burn-rate monitoring

- Alerting

- Metrics

- Centralized logs

- Kubernetes health and recovery

- Operational troubleshooting



## 2. Observability Architecture



PayGuard uses metrics and logs as its primary observability signals.



Metrics flow:



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



Logging flow:



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



## 3. Application Metrics



The Spring Boot payment service exposes application metrics through Micrometer and Actuator.



`PaymentMetrics` provides payment-specific instrumentation.



Prometheus collects these metrics and evaluates recording and alerting rules.



This allows reliability to be measured from payment behavior rather than only infrastructure state.



## 4. Service Level Indicators



An SLI is a measurable indicator of service behavior.



PayGuard tracks payment-oriented indicators including:



- Request rate

- Error rate

- Availability ratio

- Error ratio

- Latency compliance

- SLO burn rate



Current Prometheus recording rules include:



```text

payguard:sli\_payment\_requests:rate5m

payguard:sli\_payment\_errors:rate5m

payguard:sli\_availability:ratio5m

payguard:sli\_error:ratio5m

payguard:slo\_availability:burn\_rate5m

payguard:sli\_latency\_250ms:ratio5m

```



## 5. Availability SLI



The availability SLI represents the ratio of successful payment requests relative to total payment requests over the configured evaluation window.



Conceptually:



```text

Availability =

Successful Payment Requests

---------------------------

Total Payment Requests

```



The recording rule:



```text

payguard:sli\_availability:ratio5m

```



provides a five-minute availability view.



## 6. Error SLI



Payment errors are measured independently.



The relevant recording rules include:



```text

payguard:sli\_payment\_errors:rate5m

payguard:sli\_error:ratio5m

```



This allows PayGuard to distinguish traffic volume from unsuccessful payment behavior.



## 7. Latency SLI



PayGuard includes a latency SLI based on a 250 ms threshold.



The recording rule is:



```text

payguard:sli\_latency\_250ms:ratio5m

```



The purpose is to measure the proportion of payment requests satisfying the latency objective rather than relying only on average latency.



## 8. Service Level Objectives



An SLO defines the target reliability expected from an SLI.



PayGuard's Prometheus rules provide the foundation for availability and latency objectives.



The exact executable definitions remain in:



```text

observability/prometheus/rules/payguard-slo.yml

```



This file should be treated as the authoritative source for the configured Prometheus expressions and thresholds.



## 9. Error Budget



An error budget represents the amount of unreliability permitted by an SLO.



Conceptually:



```text

Error Budget = 1 - SLO Target

```



For example, if a service were configured with a 99.9% availability objective:



```text

1 - 0.999 = 0.001

```



which corresponds to a 0.1% error budget.



The example explains the concept; the repository's Prometheus rule file remains authoritative for PayGuard's actual configured target.



## 10. Burn Rate



Burn rate describes how quickly the service consumes its allowed error budget.



PayGuard records:



```text

payguard:slo\_availability:burn\_rate5m

```



A burn rate greater than the acceptable level indicates that reliability degradation is consuming the error budget too quickly.



Burn-rate monitoring is more useful than alerting on isolated failures because it relates failures to the service's reliability objective.



## 11. Alerts



The current Prometheus rule file contains:



```text

PayGuardAvailabilityBurnRateWarning

PayGuardAvailabilityBurnRateCritical

PayGuardLatencySLOViolation

```



### Availability Burn Rate Warning



Indicates elevated consumption of the availability error budget.



### Availability Burn Rate Critical



Indicates a more severe availability reliability condition.



### Latency SLO Violation



Indicates that payment latency behavior is violating the configured latency reliability condition.



The exact thresholds and durations are defined in:



```text

observability/prometheus/rules/payguard-slo.yml

```



## 12. Health Endpoints



Spring Boot Actuator provides application health endpoints.



Kubernetes uses them for workload management.



The payment service defines:



- Startup probe

- Readiness probe

- Liveness probe



### Startup



Used during application initialization.



### Readiness



Determines whether the pod should receive traffic.



### Liveness



Determines whether the application should be restarted.



This separates application startup, traffic eligibility, and runtime health.



## 13. PostgreSQL Health



PostgreSQL uses:



```text

pg\_isready

```



for Kubernetes readiness and liveness checks.



This allows Kubernetes to verify database availability independently from the application.



## 14. Kubernetes Self-Healing



PayGuard's self-healing behavior was manually tested.



A payment-service pod was deliberately deleted.



The Deployment controller automatically created a replacement.



The replacement became healthy without manual recreation.



This demonstrates desired-state reconciliation provided by Kubernetes.



## 15. Persistence Reliability



Application pods are treated as replaceable.



Payment state is stored in PostgreSQL.



PostgreSQL uses:



```text

postgres-data

```



as its PersistentVolumeClaim.



A previously created payment remained retrievable after the payment-service pod was deleted and recreated.



This confirms that durable state does not depend on an individual application pod.



## 16. Reconciliation Reliability



External payment providers can fail, timeout, or return uncertain results.



PayGuard models this through:



```text

PaymentReconciliationWorker

```



Payment reconciliation metadata includes:



- Attempt count

- Next reconciliation time

- Exhaustion state



This allows uncertain payment states to be revisited instead of silently discarded.



## 17. Idempotency



Duplicate requests are an important reliability problem for payment systems.



PayGuard uses an idempotency key to prevent repeated requests from intentionally creating separate payment operations.



The integration test suite includes duplicate-request behavior.



Idempotency improves reliability when clients retry requests because of timeouts or uncertain network conditions.



## 18. Failure Simulation



PayGuard contains controlled provider failure simulation.



This makes it possible to exercise:



- Failure handling

- Reconciliation

- Metrics

- Logging

- Error responses



without relying on a real payment provider.



Failure simulation should be disabled for normal execution unless a reliability scenario is intentionally being tested.



## 19. Grafana



Grafana is the visualization layer for PayGuard.



Provisioned dashboards include:



```text

payguard-overview.json

payguard-logs.json

```



The overview dashboard is intended for service and payment metrics.



The logs dashboard provides centralized log exploration through Loki.



## 20. Prometheus



Prometheus is responsible for:



- Scraping application metrics

- Storing time-series data

- Evaluating recording rules

- Evaluating alert rules



Configuration:



```text

observability/prometheus/prometheus.yml

```



SLO rules:



```text

observability/prometheus/rules/payguard-slo.yml

```



## 21. Loki and Alloy



Grafana Alloy collects logs and sends them to Loki.



Loki stores and queries log streams.



Grafana provides the visualization and exploration interface.



The architecture is:



```text

Workload

   |

   v

Alloy

   |

   v

Loki

   |

   v

Grafana

```



## 22. Incident Investigation Flow



A practical investigation flow is:



```text

Alert

  |

  v

Check Grafana SLI/SLO dashboard

  |

  v

Check Prometheus metric/rule

  |

  v

Identify affected workload

  |

  v

Check Kubernetes pod/deployment state

  |

  v

Inspect Loki logs

  |

  v

Check PostgreSQL if persistence is involved

  |

  v

Mitigate

  |

  v

Verify recovery through metrics

```



## 23. Example Kubernetes Investigation



Check pods:



```powershell

kubectl get pods -n payguard -o wide

```



Check the Deployment:



```powershell

kubectl get deployment payment-service -n payguard

```



Describe the workload:



```powershell

kubectl describe deployment payment-service -n payguard

```



Check application logs:



```powershell

kubectl logs -n payguard -l app=payment-service --tail=100

```



Check rollout status:



```powershell

kubectl rollout status deployment/payment-service -n payguard

```



## 24. Recovery



If a deployment introduces a problem, Kubernetes rollout history can be inspected:



```powershell

kubectl rollout history deployment/payment-service -n payguard

```



A previous Deployment revision can be restored using:



```powershell

kubectl rollout undo deployment/payment-service -n payguard

```



PayGuard's rollback mechanism has been manually exercised successfully.



## 25. Resource Monitoring



The Kubernetes manifests define resource requests and limits.



Payment service:



```text

Request:

  CPU: 100m

  Memory: 256Mi



Limit:

  CPU: 750m

  Memory: 512Mi

```



PostgreSQL:



```text

Request:

  CPU: 100m

  Memory: 128Mi



Limit:

  CPU: 500m

  Memory: 384Mi

```



`kubectl top` currently requires a Kubernetes Metrics API provider such as metrics-server.



During the previous verification:



```text

kubectl top pods -n payguard

```



returned:



```text

Metrics API not available

```



Therefore Kubernetes resource metrics through `kubectl top` are not currently claimed as operational.



## 26. Reliability Testing Completed



The following reliability scenarios have been manually exercised:



- Application startup

- PostgreSQL connectivity

- Payment creation

- Payment retrieval

- Pod deletion

- Automatic pod recreation

- Payment retrieval after pod replacement

- Scaling to two application replicas

- Rolling Deployment

- Rollback

- Scaling back to one replica

- PostgreSQL PVC verification

- Terraform idempotency



## 27. Reliability Improvements



Future improvements can include:



- Kubernetes metrics-server

- Alertmanager notification routing

- Multi-window burn-rate alerts

- Distributed tracing

- OpenTelemetry

- Automated load testing

- Chaos testing

- Database backup/restore exercises

- Automated SLO verification in CI

- Additional provider failure scenarios



These are future enhancements and are not claimed as currently implemented.



## 28. SRE Principle



The central reliability principle of PayGuard is:



```text

Do not ask only:

"Is the process running?"



Also ask:

"Are payments being processed successfully,

within the expected latency,

and can the system recover from failure?"

```


