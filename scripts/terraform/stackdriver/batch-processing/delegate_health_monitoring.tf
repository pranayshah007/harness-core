resource "google_logging_metric" "ce_delegate_health_monitoring" {
  name        = join("_", [local.name_prefix, "delegate_health_monitoring"])
  description = "Delegate health check based on last received published message. Owner: CE"
  filter = join("\n", [
    local.filter_prefix,
    "jsonPayload.logger=\"io.harness.batch.processing.tasklet.DelegateHealthCheckTasklet\"",
    "jsonPayload.message:\"Delegate health check failed for clusterId\"",
    "jsonPayload.harness.batchJobType:\"DELEGATE_HEALTH_CHECK\""
  ])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    labels {
      key         = "accountId"
      value_type  = "STRING"
      description = "CE accountId"
    }
  }
  label_extractors = {
    "accountId" : "EXTRACT(jsonPayload.harness.accountId)",
  }
}

resource "google_monitoring_alert_policy" "ce_delegate_health_monitoring_alert_policy" {
  notification_channels = ((var.deployment == "prod" || var.deployment == "freemium" || var.deployment == "prod_failover") ? ["${local.slack_prod_channel}"] :
    ((var.deployment == "qa" || var.deployment == "qa_free" || var.deployment == "stress") ? ["${local.slack_qa_channel}"] :
  ["${local.slack_dev_channel}"]))
  enabled = false
  display_name = join("_", [local.name_prefix, "ce_delegate_health_monitoring"])
  combiner     = "OR"
  conditions {
    display_name = "ce_delegate_health_monitoring"
    condition_threshold {
      threshold_value = 0
      filter          = "resource.type=\"k8s_container\" AND metric.type=\"logging.googleapis.com/user/${google_logging_metric.ce_delegate_health_monitoring.id}\""
      duration        = "180s"
      comparison      = "COMPARISON_GT"
      aggregations {
        group_by_fields      = ["metric.labels.accountId"]
        alignment_period     = "600s"
        per_series_aligner   = "ALIGN_SUM"
        cross_series_reducer = "REDUCE_SUM"
      }
    }
  }
}
