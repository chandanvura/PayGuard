output "namespace" {
  description = "Kubernetes namespace managed by Terraform."
  value       = kubernetes_namespace_v1.payguard.metadata[0].name
}

output "config_map" {
  description = "PayGuard ConfigMap managed by Terraform."
  value       = kubernetes_config_map_v1.payguard.metadata[0].name
}
