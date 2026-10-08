# Validation evidence

On 8 October 2026, commit `0befc2a1d0c39f5e69cdd38d42f2cc9d1eabe203` passed all three workflows:

- [Infrastructure runtime validation](https://github.com/chandanvura/PayGuard/actions/runs/37754273454)
- [Java, Go, Docker, PowerShell syntax, and Kubernetes schema CI](https://github.com/chandanvura/PayGuard/actions/runs/37754273510)
- [Hosted Compose monitoring and Pages deployment](https://github.com/chandanvura/PayGuard/actions/runs/37754273530)

## Scope of actual execution

| Component | Check that ran |
|---|---|
| Go companion | Failure/recovery, invalid URL, timeout, counters, initial health, methods/routes; `go test -race` and `go vet` |
| Kubernetes | Created a temporary kind cluster; built/loaded Java and Go images; deployed repository manifests; waited for both containers to be ready |
| Payment behavior | Real Java API and PostgreSQL: normal payment, timeout after simulated charge, idempotent retry, reconciliation, metrics |
| Pod replacement | Deleted the payment pod, waited for its replacement, checked the new companion, retrieved the same SUCCESS payment from PostgreSQL |
| Terraform | Initialized provider, validated configuration, applied namespace/configuration resources, obtained a no-change plan with detailed exit code 0 |
| Ansible | Executed the existing operational playbook against the temporary cluster: nodes, namespace, deployments, PVC, and pods |
| Monitoring/site | Temporary Compose stack, Prometheus export, Grafana capture, Pages deployment |

The first infrastructure run exposed a test connection issue after pod replacement. The checker now retries closed HTTP connections, and the workflow stops the previous port-forward before reconnecting. The rerun passed the same persistence assertion.

## Remaining limits

- The Kubernetes runtime test uses **kind**, not the user's specific Minikube installation.
- The Windows Jenkinsfile now builds both application images, but no configured Windows Jenkins controller/agent was available for execution. A successful GitHub Actions run does not prove a Jenkins run.
- Terraform still represents namespace/configuration resources; it does not deploy all application workloads.
- Ansible verifies running resources; it does not deploy the platform.
- These tests do not constitute a full security audit or exhaustive coverage of all possible failures. Dedicated vulnerability scanning and load/resource-limit tests remain outside this evidence.
- GitHub runners are temporary. The public monitoring pages contain dated evidence, not continuously running API/monitoring servers.
