/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

locals {
  notification_channels = ((var.deployment == "prod" || var.deployment == "freemium" || var.deployment == "prod_failover") ? ["${local.slack_prod_channel}", "${local.email_prod_channel}"] :
((var.deployment == "qa" || var.deployment == "qa_free" || var.deployment == "stress") ? ["${local.slack_qa_channel}", "${local.email_qa_channel}"] :
["${local.slack_dev_channel}", "${local.email_dev_channel}"]))
}

resource "google_monitoring_alert_policy" "cvng_alert_policy_data_collection" {
  notification_channels = local.notification_channels
  display_name = join("_", [local.name_prefix, "cvng_alert_policy_data_collection"])
  combiner     = "OR"
  conditions {
    display_name = "data_collectiontask_running_time_alert"
    condition_threshold {
      filter = "metric.type=\"custom.googleapis.com/opencensus/data_collection_task_running_time\" resource.type=\"k8s_container\""
      comparison = "COMPARISON_GT"
      duration = "300s"
      threshold_value = 180000
      aggregations {
        alignment_period = "300s"
        cross_series_reducer = "REDUCE_PERCENTILE_99"
        per_series_aligner = "ALIGN_DELTA"
      }
    }
  }
}
