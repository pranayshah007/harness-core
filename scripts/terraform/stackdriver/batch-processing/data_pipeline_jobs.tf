/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

resource "google_logging_metric" "ce_failed_data_pipeline_jobs" {
  name        = join("_", [local.name_prefix, "ce_failed_data_pipeline_jobs"])
  description = "Number of failed Data Pipeline jobs. Owner: CE"
  filter = join("\n", [
    local.filter_prefix,
    "jsonPayload.logger=\"io.harness.batch.processing.service.impl.BillingDataPipelineHealthStatusServiceImpl\"",
    "jsonPayload.message:\"Transfer Failed:\""
  ])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
  }
}

resource "google_monitoring_alert_policy" "ce_failed_data_pipeline_jobs" {
  notification_channels = ((var.deployment == "prod" || var.deployment == "freemium" || var.deployment == "prod_failover") ? ["${local.slack_prod_channel}"] :
    ((var.deployment == "qa" || var.deployment == "qa_free" || var.deployment == "stress") ? ["${local.slack_qa_channel}"] :
  ["${local.slack_dev_channel}"]))

  enabled      = false
  display_name = join("_", [local.name_prefix, "ce_failed_data_pipeline_jobs"])
  combiner     = "OR"
  conditions {
    display_name = "ce_failed_data_pipeline_jobs"
    condition_threshold {
      threshold_value = 0
      filter          = "resource.type=\"k8s_container\" AND metric.type=\"logging.googleapis.com/user/${google_logging_metric.ce_failed_data_pipeline_jobs.id}\""
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
