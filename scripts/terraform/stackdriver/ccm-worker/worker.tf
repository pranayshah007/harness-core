resource "google_logging_metric" "ccm_worker_health_metric" {
  name        = join("_", [local.name_prefix, "ccm_worker"])
  description = "Health of CCM Worker. Owner: CCM"
  filter = join("\n", [
    local.filter_prefix,
    "jsonPayload.message:\"Panic recovered.\"",
  ])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
  }
}

resource "google_monitoring_alert_policy" "ccm_worker_health_metric_alert_policy" {
  notification_channels = ((var.deployment == "prod" || var.deployment == "freemium" || var.deployment == "prod_failover")
  ? ["${local.slack_prod_channel}"]
  : ((var.deployment == "qa" || var.deployment == "qa_free" || var.deployment == "stress")
  ? ["${local.slack_qa_channel}"]
  : ["${local.slack_dev_channel}"]))

  display_name = join("_", [local.name_prefix, "ccm_worker_health"])
  combiner     = "OR"
  conditions {
    display_name = "ccm_worker_health"
    condition_threshold {
      threshold_value = 0
      filter          = "resource.type=\"k8s_container\" AND metric.type=\"logging.googleapis.com/user/${google_logging_metric.ccm_worker_health_metric.id}\""
      duration        = "180s"
      comparison      = "COMPARISON_GT"
      aggregations {
        group_by_fields      = ["resource.label.project_id"]
        alignment_period     = "300s"
        per_series_aligner   = "ALIGN_SUM"
        cross_series_reducer = "REDUCE_SUM"
      }
    }
  }
}
