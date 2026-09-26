variable "kubeconfig_path" {
  description = "Path to the Kubernetes kubeconfig file."
  type        = string
  default     = "~/.kube/config"
}

variable "kubernetes_context" {
  description = "Kubernetes context Terraform will use."
  type        = string
  default     = "minikube"
}

variable "namespace" {
  description = "Namespace used by PayGuard."
  type        = string
  default     = "payguard-iac"
}

variable "environment" {
  description = "Deployment environment."
  type        = string
  default     = "local"
}
