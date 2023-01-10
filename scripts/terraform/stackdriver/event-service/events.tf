resource "google_logging_metric" "ce_publish_requests" {
  name        = join("_", [local.name_prefix, "ce_publish_requests"])
  description = "Number of publish requests received. Owner: CE"
  filter = join("\n", [
    local.filter_prefix,
    "jsonPayload.logger=(\"io.harness.event.grpc.EventPublisherServerImpl\" OR \"io.harness.event.resources.K8sEventResource\")",
    "jsonPayload.message:\"Received publish request\""
  ])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    labels {
      key         = "accountId"
      value_type  = "STRING"
      description = "The accountId"
    }
  }
  label_extractors = {
    "accountId" : "EXTRACT(jsonPayload.harness.accountId)"
  }
}

resource "google_monitoring_alert_policy" "ce_publish_requests_alert_policy" {
  notification_channels = ((var.deployment == "prod" || var.deployment == "freemium" || var.deployment == "prod_failover") ? ["${local.slack_prod_channel}"] :
    ((var.deployment == "qa" || var.deployment == "qa_free" || var.deployment == "stress") ? ["${local.slack_qa_channel}"] :
  ["${local.slack_dev_channel}"]))
  display_name = join("_", [local.name_prefix, "ce_publish_requests_alert_policy"])
  combiner     = "OR"
  conditions {
    display_name = "ce_publish_requests_alert_policy"
    condition_threshold {
      threshold_value = ((var.deployment == "prod" || var.deployment == "freemium" || var.deployment == "prod_failover") ? 100 : 10)
      filter          = "resource.type=\"k8s_container\" AND metric.type=\"logging.googleapis.com/user/${google_logging_metric.ce_publish_requests.id}\""
      duration        = "180s"
      comparison      = "COMPARISON_LT"
      aggregations {
        group_by_fields      = ["resource.label.project_id"]
        alignment_period     = "300s"
        per_series_aligner   = "ALIGN_SUM"
        cross_series_reducer = "REDUCE_SUM"
      }
    }
  }


}
