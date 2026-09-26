# PayGuard CI/CD



## 1. Purpose



PayGuard uses multiple automation approaches to demonstrate modern CI/CD and DevOps practices.



The repository contains:



- GitHub Actions CI

- Jenkins pipeline-as-code

- Docker image builds

- Kubernetes manifest validation

- Terraform Infrastructure as Code

- Ansible operational verification



These components are intentionally kept in the repository so the complete delivery workflow can be inspected and reproduced.



## 2. Delivery Architecture



```text

Developer

   |

   v

Git Repository

   |

   +---------------------+

   |                     |

   v                     v

GitHub Actions         Jenkins

   |                     |

   v                     v

Java Tests            Java Tests

   |                     |

   v                     v

Docker Build          Package

   |                     |

   v                     v

Kubernetes            Docker Build

Validation               |

                         v

                      Kubernetes

                      Validation

```



## 3. GitHub Actions



Workflow:



```text

.github/workflows/ci.yml

```



The workflow runs for pushes and pull requests targeting `main`.



## 4. Java Test Job



The GitHub Actions test job uses:



- Ubuntu runner

- Java 21

- Maven Wrapper

- PostgreSQL 17 service container



The CI PostgreSQL environment provides the PayGuard database required by integration tests.



The application receives database configuration through environment variables.



The primary test command is:



```text

./mvnw --batch-mode clean test

```



This validates the Spring Boot application and payment integration tests.



## 5. Maven Dependency Caching



GitHub Actions uses the Java setup action with Maven caching enabled.



The cache dependency path points to:



```text

payment-service/pom.xml

```



This reduces repeated Maven dependency downloads across CI executions.



## 6. Docker Build Job



The Docker job depends on successful Java tests.



The build uses:



```text

payment-service/Dockerfile

```



Conceptually:



```text

Tests Pass

    |

    v

Docker Build

    |

    v

payguard-payment-service:ci

```



A failed test therefore prevents the Docker validation stage from representing the application as successfully validated.



## 7. Dockerfile



The payment service uses a multi-stage Docker build.



Builder stage:



```text

Java 21 JDK

   |

Maven Wrapper

   |

Dependencies

   |

Compile / Package

```



Runtime stage:



```text

Java 21 JRE

   |

app.jar

   |

Spring Boot

```



Using a separate runtime stage prevents the full build environment from being required in the final application image.



## 8. Kubernetes Validation



GitHub Actions performs client-side validation of the Kubernetes manifests.



Validated resources include:



```text

kubernetes/namespace.yaml

kubernetes/configmap.yaml

kubernetes/secret.example.yaml

kubernetes/postgres/pvc.yaml

kubernetes/postgres/deployment.yaml

kubernetes/postgres/service.yaml

kubernetes/payment-service/deployment.yaml

kubernetes/payment-service/service.yaml

```



The example Secret is used in CI rather than the ignored local Secret.



## 9. Secret Handling



The real local Kubernetes Secret is:



```text

kubernetes/secret.yaml

```



It is intentionally ignored by Git.



The repository contains:



```text

kubernetes/secret.example.yaml

```



for documentation and validation.



Production credentials should never be stored directly in the repository.



A production deployment would normally use a dedicated secret-management mechanism.



## 10. Jenkins



The root-level:



```text

Jenkinsfile

```



implements pipeline-as-code.



The current Jenkins pipeline is designed around a Windows Jenkins agent because the local PayGuard development environment uses Windows.



## 11. Jenkins Pipeline



Current stages:



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

Post Actions

```



## 12. Checkout



Jenkins retrieves the configured source repository using:



```text

checkout scm

```



This allows the pipeline definition and application source to remain versioned together.



## 13. Jenkins Test Stage



The test stage runs from:



```text

payment-service/

```



using:



```text

mvnw.cmd --batch-mode clean test

```



The Windows Maven Wrapper keeps the build independent from a manually installed global Maven version.



## 14. Jenkins Package Stage



After testing, Jenkins packages the application using:



```text

mvnw.cmd --batch-mode package -DskipTests

```



Tests are skipped during packaging because they were already executed by the preceding stage.



## 15. Jenkins Docker Stage



The pipeline builds:



```text

payguard-payment-service:%BUILD_NUMBER%

```



Using the Jenkins build number demonstrates immutable build-specific image tagging rather than relying only on `latest`.



## 16. Jenkins Kubernetes Validation



The Jenkins pipeline validates the same Kubernetes resources used by GitHub Actions.



This includes:



- Namespace

- ConfigMap

- Example Secret

- PostgreSQL PVC

- PostgreSQL Deployment

- PostgreSQL Service

- Payment Service Deployment

- Payment Service Service



## 17. Jenkins Post Actions



JUnit reports are collected from:



```text

payment-service/target/surefire-reports/*.xml

```



The application JAR is archived from:



```text

payment-service/target/*.jar

```



This makes test results and build artifacts available from the Jenkins build when executed on a configured Jenkins environment.



## 18. GitHub Actions vs Jenkins



PayGuard includes both systems for learning and portfolio demonstration.



GitHub Actions demonstrates repository-native CI.



Jenkins demonstrates self-managed pipeline-as-code.



They are not intended to represent two mandatory production pipelines executing every deployment simultaneously.



## 19. Terraform



Terraform configuration is located under:



```text

terraform/

```



Files include:



```text

versions.tf

provider.tf

variables.tf

main.tf

outputs.tf

.terraform.lock.hcl

```



Terraform uses the HashiCorp Kubernetes provider.



## 20. Terraform Workflow



The verified local workflow is:



```text

terraform fmt -recursive

terraform init

terraform validate

terraform plan

terraform apply

terraform plan

```



The initial plan created two resources.



After applying them, the next plan returned:



```text

No changes. Your infrastructure matches the configuration.

```



This demonstrates Infrastructure as Code idempotency.



## 21. Terraform Isolation



Terraform manages:



```text

payguard-iac

```



rather than immediately taking ownership of the manually deployed:



```text

payguard

```



namespace.



This isolation protects the working environment while Terraform is being introduced.



## 22. Terraform State



Terraform state contains infrastructure metadata and can contain sensitive values.



The following are excluded from Git:



```text

.terraform/

*.tfstate

*.tfstate.*

```



The provider lock file:



```text

.terraform.lock.hcl

```



is intentionally retained in version control.



## 23. Ansible



Ansible files are located under:



```text

ansible/

```



Current structure:



```text

ansible/

├── ansible.cfg

├── inventory.ini

└── playbooks/

    └── verify-payguard.yml

```



## 24. Ansible Verification



The playbook verifies:



- kubectl client availability

- Minikube status

- PayGuard namespace

- payment-service rollout

- PostgreSQL rollout

- PostgreSQL PVC

- Running application pods



This provides an operational verification layer separate from provisioning.



## 25. Ansible Runtime Status



The playbook is present in the repository.



Ansible operational verification was successfully runtime-tested against the configured local environment. It checks cluster status, rollout readiness, PVC, and running pods; it does not deploy the service.



## 26. Intended Delivery Lifecycle



The complete conceptual delivery lifecycle is:



```text

Developer Change

      |

      v

Git Commit

      |

      v

CI Tests

      |

      v

Docker Build

      |

      v

Manifest Validation

      |

      v

Deployment

      |

      v

Kubernetes Health Checks

      |

      v

Prometheus / Grafana

      |

      v

Operational Verification

```



## 27. Failure Behavior



A reliable pipeline must stop when validation fails.



Expected behavior:



```text

Test Failure

    |

    X

No successful downstream build



Docker Build Failure

    |

    X

No successful image validation



Manifest Validation Failure

    |

    X

Deployment configuration must be corrected

```



This prevents known-bad changes from being treated as successfully validated artifacts.



## 28. Current Validation Status



Verified locally:



- Maven Wrapper

- Java tests

- Docker image build/runtime

- Docker Compose runtime

- Kubernetes runtime

- Kubernetes scaling

- Kubernetes rollout

- Kubernetes rollback

- Terraform initialization

- Terraform validation

- Terraform apply

- Terraform idempotency

- Ansible operational verification runtime



Defined but pending execution in their actual external environments:



- GitHub Actions workflow

- Jenkins pipeline

## 29. Future CI/CD Improvements



Possible future enhancements include:



- Container registry publishing

- Image vulnerability scanning

- Software Bill of Materials generation

- GitHub dependency scanning

- GitOps with Argo CD

- Automated deployment promotion

- Automated rollback policies

- Terraform CI plans

- Ansible linting

- Kubernetes policy validation

- Integration/load testing

- Release versioning



These are future enhancements and are not claimed as currently implemented.



## 30. CI/CD Principle



PayGuard follows this principle:



```text

Source code alone is not the deliverable.



A reliable delivery process must also verify:



- Build correctness

- Tests

- Containerization

- Deployment configuration

- Infrastructure configuration

- Runtime health

```





## Hosted reliability demonstration and Pages

`.github/workflows/reliability-demo.yml` starts PostgreSQL 17 and the real Java service on a GitHub-hosted runner. `scripts/ci-demo.py` asserts health, successful payment, provider timeout after charge (UNKNOWN), same-ID retry, automatic reconciliation (SUCCESS), and Prometheus metrics. Run summaries and a downloadable artifact show evidence; no remote provider or payment account is involved. The workflow is available through manual dispatch and relevant pushes.

`.github/workflows/pages.yml` deploys the static `website/` directory to GitHub Pages when the repository Pages source is configured to GitHub Actions. The site shows workflow badges and links to the actual run history; its diagrams explain the architecture, while runtime dashboards remain local.
