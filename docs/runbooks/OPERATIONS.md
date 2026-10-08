# PayGuard Operations Runbook

For current first-time setup, PowerShell start/demo/stop commands, image builds, and retained-volume credential recovery, use the [Windows operations guide](../WINDOWS-OPERATIONS.md). The commands below cover additional operational scenarios; enter your actual clone directory rather than assuming the example path. Current validation evidence is recorded in [VALIDATION.md](../VALIDATION.md).



## 1. Purpose



This runbook contains the primary commands used to start, verify, troubleshoot, recover, and stop the PayGuard local platform.



Environment:



```text

Windows 11

PowerShell

Docker Desktop

Minikube

Kubernetes

Java 21

PostgreSQL 17

```



Project root:



```text

C:\Users\srich\OneDrive\Desktop\PayGuard

```



## 2. Start PayGuard



Open PowerShell:



```powershell

cd C:\Users\srich\OneDrive\Desktop\PayGuard

```



Start the Docker Compose environment:



```powershell

docker compose up -d

```



Verify:



```powershell

docker compose ps

```



Start Minikube:



```powershell

minikube start

```



Verify:



```powershell

minikube status

kubectl config current-context

kubectl get nodes

```



Expected Kubernetes context:



```text

minikube

```



## 3. Verify Kubernetes



Check namespace resources:



```powershell

kubectl get pods -n payguard -o wide

kubectl get deployment -n payguard

kubectl get service -n payguard

kubectl get pvc -n payguard

```



Expected primary workloads:



```text

payment-service

postgres

```



Payment service should normally show:



```text

READY 1/1

STATUS Running

```



PostgreSQL should also be:



```text

READY 1/1

STATUS Running

```



## 4. Application Health



Docker Compose application:



```powershell

Invoke-RestMethod http://localhost:8081/actuator/health

```



For Kubernetes, use the configured service/port-forward path being used by the local environment.



Health endpoints include:



```text

/actuator/health

/actuator/health/liveness

/actuator/health/readiness

```



## 5. Kubernetes Rollout Verification



Check payment-service:



```powershell

kubectl rollout status deployment/payment-service -n payguard --timeout=120s

```



Check PostgreSQL:



```powershell

kubectl rollout status deployment/postgres -n payguard --timeout=120s

```



## 6. Inspect Application Pods



```powershell

kubectl get pods -n payguard -l app=payment-service -o wide

```



Describe the Deployment:



```powershell

kubectl describe deployment payment-service -n payguard

```



Describe a specific pod:



```powershell

kubectl describe pod <POD_NAME> -n payguard

```



## 7. Application Logs



List the payment-service pod:



```powershell

$pod = kubectl get pods -n payguard -l app=payment-service -o jsonpath='{.items[0].metadata.name}'

$pod

```



View logs:



```powershell

kubectl logs -n payguard $pod --tail=100

```



Follow logs:



```powershell

kubectl logs -n payguard $pod -f

```



## 8. PostgreSQL Verification



Check PostgreSQL pod:



```powershell

kubectl get pods -n payguard -l app=postgres

```



Check PVC:



```powershell

kubectl get pvc postgres-data -n payguard

```



Expected PVC state:



```text

Bound

```



## 9. Test Kubernetes Self-Healing



Get the application pod:



```powershell

$pod = kubectl get pods -n payguard -l app=payment-service -o jsonpath='{.items[0].metadata.name}'

```



Delete it:



```powershell

kubectl delete pod -n payguard $pod

```



Watch Kubernetes recreate it:



```powershell

kubectl get pods -n payguard -w

```



Stop watching with:



```text

Ctrl+C

```



Then verify:



```powershell

kubectl wait --for=condition=Ready pod -l app=payment-service -n payguard --timeout=120s

```



## 10. Scale Payment Service



Scale to two replicas:



```powershell

kubectl scale deployment payment-service -n payguard --replicas=2

```



Wait:



```powershell

kubectl rollout status deployment/payment-service -n payguard --timeout=120s

```



Verify:



```powershell

kubectl get pods -n payguard -l app=payment-service -o wide

```



Return to the normal low-resource configuration:



```powershell

kubectl scale deployment payment-service -n payguard --replicas=1

```



## 11. Rollout History



```powershell

kubectl rollout history deployment/payment-service -n payguard

```



## 12. Rollback



If a rollout causes problems:



```powershell

kubectl rollout undo deployment/payment-service -n payguard

```



Then:



```powershell

kubectl rollout status deployment/payment-service -n payguard --timeout=180s

```



Verify:



```powershell

kubectl get pods -n payguard -l app=payment-service -o wide

```



## 13. ConfigMap



Inspect configuration:



```powershell

kubectl get configmap payguard-config -n payguard -o yaml

```



Important configuration includes:



```text

DB_URL

FAILURE_SIMULATION_ENABLED

```



## 14. Kubernetes Secret



Verify that the Secret exists without displaying secret values:



```powershell

kubectl get secret payguard-db-secret -n payguard

```



Do not commit:



```text

kubernetes/secret.yaml

```



Only the example manifest should be version-controlled:



```text

kubernetes/secret.example.yaml

```



## 15. Docker Troubleshooting



Check containers:



```powershell

docker compose ps

```



Check payment-service logs:



```powershell

docker compose logs payment-service --tail=100

```



Check PostgreSQL logs:



```powershell

docker compose logs postgres --tail=100

```



Check Prometheus:



```powershell

docker compose logs prometheus --tail=100

```



Check Grafana:



```powershell

docker compose logs grafana --tail=100

```



Check Loki:



```powershell

docker compose logs loki --tail=100

```



Check Alloy:



```powershell

docker compose logs alloy --tail=100

```



## 16. Observability



Local interfaces:



```text

Grafana:

http://localhost:3000



Prometheus:

http://localhost:9090



Loki:

http://localhost:3100

```



Prometheus should scrape the payment service and evaluate PayGuard SLI/SLO rules.



Grafana provides dashboards for metrics and logs.



## 17. Prometheus Rules



Rule file:



```text

observability/prometheus/rules/payguard-slo.yml

```



Important alerts:



```text

PayGuardAvailabilityBurnRateWarning

PayGuardAvailabilityBurnRateCritical

PayGuardLatencySLOViolation

```



## 18. Java Tests



From the repository root:



```powershell

cd payment-service

.mvnw.cmd clean test

cd ..

```



A failed test must be investigated before treating the application as ready for delivery.



## 19. Docker Build



Build manually:



```powershell

docker build -t payguard-payment-service:local .payment-service

```



Verify:



```powershell

docker images payguard-payment-service

```



## 20. Kubernetes Manifest Validation



Run from repository root:



```powershell

kubectl apply --dry-run=client -f kubernetes/namespace.yaml

kubectl apply --dry-run=client -f kubernetes/configmap.yaml

kubectl apply --dry-run=client -f kubernetes/secret.example.yaml

kubectl apply --dry-run=client -f kubernetes/postgres/pvc.yaml

kubectl apply --dry-run=client -f kubernetes/postgres/deployment.yaml

kubectl apply --dry-run=client -f kubernetes/postgres/service.yaml

kubectl apply --dry-run=client -f kubernetes/payment-service/deployment.yaml

kubectl apply --dry-run=client -f kubernetes/payment-service/service.yaml

```



## 21. Terraform Validation



Move to Terraform:



```powershell

cd terraform

```



Format:



```powershell

terraform fmt -recursive

```



Validate:



```powershell

terraform validate

```



Check infrastructure drift:



```powershell

terraform plan

```



A healthy unchanged environment should return:



```text

No changes. Your infrastructure matches the configuration.

```



Return:



```powershell

cd ..

```



## 22. Terraform Safety



Do not commit:



```text

terraform.tfstate

terraform.tfstate.*

.terraform/

```



Keep:



```text

.terraform.lock.hcl

```



Do not run `terraform destroy` against an environment unless destruction is intentional.



## 23. Ansible



Ansible verification playbook:



```text

ansible/playbooks/verify-payguard.yml

```



The playbook is intended to run from Linux/WSL.



The playbook was runtime-validated successfully against the configured local environment. Re-run it when verifying a fresh local cluster; it reads state and does not deploy workloads.



## 24. Common Failure: CreateContainerConfigError



Check:



```powershell

kubectl describe pod <POD_NAME> -n payguard

```



Typical causes include:



- Missing ConfigMap

- Missing Secret

- Incorrect configuration key

- Missing referenced resource



Verify:



```powershell

kubectl get configmap -n payguard

kubectl get secret -n payguard

```



## 25. Common Failure: Application Not Ready



Check:



```powershell

kubectl get pods -n payguard

kubectl describe deployment payment-service -n payguard

kubectl logs -n payguard -l app=payment-service --tail=100

```



Investigate:



- Database connectivity

- Flyway migration failures

- Health probe failures

- Application startup exceptions

- Missing environment variables



## 26. Common Failure: Database Connectivity



Kubernetes database URL should use the Kubernetes Service:



```text

jdbc:postgresql://postgres:5432/payguard

```



The Windows host Docker Compose connection uses the mapped host port:



```text

localhost:15432

```



Do not confuse host networking with Kubernetes/Docker internal service networking.



## 27. Common Failure: Metrics API Not Available



If:



```powershell

kubectl top pods -n payguard

```



returns:



```text

Metrics API not available

```



the cluster does not currently have a working Kubernetes Metrics API provider.



This does not mean Prometheus is broken.



`kubectl top` and Prometheus are separate monitoring paths.



A future improvement is enabling metrics-server for Minikube.



## 28. Emergency Recovery Sequence



For an application deployment problem:



```text

1. Check pods

2. Check rollout

3. Check logs

4. Check health endpoints

5. Check ConfigMap/Secret references

6. Check database

7. Inspect observability

8. Roll back if the new Deployment caused the issue

9. Verify service recovery

```



Commands:



```powershell

kubectl get pods -n payguard -o wide



kubectl rollout status deployment/payment-service -n payguard



kubectl logs -n payguard -l app=payment-service --tail=100



kubectl rollout history deployment/payment-service -n payguard

```



If rollback is required:



```powershell

kubectl rollout undo deployment/payment-service -n payguard

kubectl rollout status deployment/payment-service -n payguard --timeout=180s

```



## 29. Shutdown



Stop Docker Compose:



```powershell

docker compose down

```



Stop Minikube:



```powershell

minikube stop

```



This preserves the Minikube cluster for later reuse.



Use destructive cleanup commands only when data removal is intentional.



## 30. Daily Startup



Normal development startup:



```powershell

cd C:\Users\srich\OneDrive\Desktop\PayGuard



docker compose up -d



minikube start



docker compose ps



kubectl get pods -n payguard

```



## 31. Daily Shutdown



```powershell

cd C:\Users\srich\OneDrive\Desktop\PayGuard



docker compose down



minikube stop

```



## 32. Operational Principle



```text

Observe before changing.



Verify the failing layer.



Recover using the smallest safe action.



Verify recovery with health, metrics, logs, and application behavior.

```



