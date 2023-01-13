/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

resource "google_logging_metric" "ccm_failed_cloudfunction_runs_metric" {
  project     = "${var.projectId}"
  name        = join("_", [local.name_prefix, "ccm_failed_cloudfunction_runs_metric"])
  description = "Number of failed CCM CloudFunction runs. Owner: CCM"
  filter = "resource.type=\"cloud_function\" resource.labels.function_name:\"-terraform\" severity=ERROR"
  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    labels {
      key         = "function_name"
      value_type  = "STRING"
      description = "CF Name"
    }
  }
  label_extractors = {
    "function_name" : "EXTRACT(resource.labels.function_name)"
  }
}

resource "google_monitoring_alert_policy" "ccm_failed_cloudfunction_runs_policy" {
  project = "${var.projectId}"
  notification_channels = ((var.deployment == "prod" || var.deployment == "freemium" || var.deployment == "prod_failover") ? ["${local.slack_prod_channel}"] :
  ((var.deployment == "qa" || var.deployment == "qa_free" || var.deployment == "stress") ? ["${local.slack_qa_channel}"] :
  ((var.deployment == "ceqa") ? ["${local.slack_ceqa_channel}"] :
  ["${local.slack_dev_channel}"])))

  enabled      = true
  display_name = join("_", [local.name_prefix, "ccm_failed_cloudfunction_runs_policy"])
  combiner     = "OR"
  conditions {
    display_name = "ccm_failed_cloudfunction_runs_policy"
    condition_threshold {
      threshold_value = 0
      filter          = "resource.type=\"cloud_function\" AND metric.type=\"logging.googleapis.com/user/${google_logging_metric.ccm_failed_cloudfunction_runs_metric.id}\""
      duration        = "180s"
      comparison      = "COMPARISON_GT"
      aggregations {
        group_by_fields      = ["resource.labels.function_name"]
        alignment_period     = "300s"
        per_series_aligner   = "ALIGN_SUM"
        cross_series_reducer = "REDUCE_SUM"
      }
    }
  }


}
