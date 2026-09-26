resource "kubernetes_namespace_v1" "payguard" {
  metadata {
    name = var.namespace

    labels = {
      "app.kubernetes.io/name"       = "payguard"
      "app.kubernetes.io/managed-by" = "terraform"
      "environment"                  = var.environment
    }
  }
}

resource "kubernetes_config_map_v1" "payguard" {
  metadata {
    name      = "payguard-config"
    namespace = kubernetes_namespace_v1.payguard.metadata[0].name

    labels = {
      "app.kubernetes.io/name"       = "payguard"
      "app.kubernetes.io/managed-by" = "terraform"
    }
  }

  data = {
    DB_URL                     = "jdbc:postgresql://postgres:5432/payguard"
    FAILURE_SIMULATION_ENABLED = "false"
  }
}
