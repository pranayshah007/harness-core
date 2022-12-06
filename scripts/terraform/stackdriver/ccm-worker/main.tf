
variable "deployment" {
  type = string
}

variable "projectId" {
  type = string
}

locals {
  qa_filter_prefix = join("\n", [
    "resource.type=\"k8s_container\"",
    "resource.labels.cluster_name=\"qa-private\"",
    "resource.labels.container_name=\"ccm-worker\"",
    "resource.labels.namespace_name=\"harness-qa\""
  ])

  qa_free_filter_prefix = join("\n", [
    "resource.type=\"k8s_container\"",
    "resource.labels.cluster_name=\"qa-private\"",
    "resource.labels.container_name=\"ccm-worker\"",
    "resource.labels.namespace_name=\"harness-free\""
  ])

  prod_filter_prefix = join("\n", [
    "resource.type=\"k8s_container\"",
    "resource.labels.cluster_name=\"prod-private-uswest1-primary\"",
    "resource.labels.container_name=\"ccm-worker\"",
    "resource.labels.namespace_name=\"harness\""
  ])

  prod_failover_filter_prefix = join("\n", [
    "resource.type=\"k8s_container\"",
    "resource.labels.cluster_name=\"prod-private-uswest2-failover\"",
    "resource.labels.container_name=\"ccm-worker\"",
    "resource.labels.namespace_name=\"harness\""
  ])

  prod_free_filter_prefix = join("\n", [
    "resource.type=\"k8s_container\"",
    "resource.labels.cluster_name=\"prod-private-uswest1-primary\"",
    "resource.labels.container_name=\"ccm-worker\"",
    "resource.labels.namespace_name=\"harness-free\""
  ])

  dev_filter_prefix = join("\n", [
    "resource.type=\"k8s_container\"",
    "resource.labels.cluster_name=\"ce-dev\"",
    "resource.labels.container_name=\"ccm-worker\"",
    "resource.labels.namespace_name=\"harness\""
  ])

  stress_filter_prefix = join("\n", [
    "resource.type=\"k8s_container\"",
    "resource.labels.cluster_name=\"qa-stress\"",
    "resource.labels.container_name=\"ccm-worker\"",
  ])

  filter_prefix = (var.deployment == "qa" ? local.qa_filter_prefix :
  (var.deployment == "qa_free" ? local.qa_free_filter_prefix :
  (var.deployment == "stress" ? local.stress_filter_prefix :
  (var.deployment == "prod" ? local.prod_filter_prefix :
  (var.deployment == "freemium" ? local.prod_free_filter_prefix :
  (var.deployment == "prod_failover" ? local.prod_failover_filter_prefix :
  (var.deployment == "dev" ? local.dev_filter_prefix :
  local.qa_filter_prefix)))))))

  name_prefix = join("_", ["x", var.deployment])

  # prod and freemium is #ce-alerts channel. dev and qa is #ce-alerts-test channel
  slack_prod_channel = "projects/${var.projectId}/notificationChannels/10185135917587539827"
  slack_dev_channel  = "projects/${var.projectId}/notificationChannels/13768296773189683769"
  slack_qa_channel   = "projects/${var.projectId}/notificationChannels/11524768178699293863" #2940114166643309654 for ce-qa

  # prod is ce-alerts@harness.io, rest is ce-alerts-qa@harness.io
  email_prod_channel = "projects/${var.projectId}/notificationChannels/16286565924796139541"
  email_dev_channel  = "projects/${var.projectId}/notificationChannels/17855665763510449367"
  email_qa_channel   = "projects/${var.projectId}/notificationChannels/9385478850545552747"
}
