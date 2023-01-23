/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

resource "google_logging_metric" "ce_event_drops" {
  name        = join("_", [local.name_prefix, "ce_event_drops"])
  description = "Number of events dropped. Owner: CE"
  filter = join("\n", [
    local.filter_prefix,
    "jsonPayload.logger=\"io.harness.event.client.impl.appender.ChronicleEventAppender\"",
    "jsonPayload.message=\"Dropping message as queue is not healthy\""
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
    "accountId" : "EXTRACT(labels.accountId)"
  }
}

resource "google_monitoring_alert_policy" "ce_event_drops" {
  count                 = var.deployment == "prod" ? 1 : 0
  enabled               = var.deployment == "prod" ? true : false
  notification_channels = var.deployment == "prod" ? ["projects/${var.projectId}/notificationChannels/10185135917587539827"] : []
  display_name          = join("_", [local.name_prefix, "ce_event_drops"])
  combiner              = "OR"
  conditions {
    display_name = "ce_event_drop"
    condition_threshold {
      filter     = "resource.type=\"global\" AND metric.type=\"logging.googleapis.com/user/${local.name_prefix}_ce_event_drops\""
      duration   = "60s"
      comparison = "COMPARISON_GT"
      aggregations {
        group_by_fields    = ["metric.labels.accountId"]
        alignment_period   = "60s"
        per_series_aligner = "ALIGN_COUNT"
      }
    }
  }
}

// Metrics Based on k8s versions installed on the cluster
resource "google_logging_metric" "k8s_version" {
  name        = join("_", [local.name_prefix, "k8s_version"])
  description = "K8s version associated with every K8sTasks except INSTANCE_SYNC. Owner: CE"
  filter = join("\n", [
    local.filter_prefix,
    "jsonPayload.logger=\"software.wings.delegatetasks.k8s.taskhandler.K8sVersionTaskHandler\""
  ])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    labels {
      key         = "cloudProvider"
      value_type  = "STRING"
      description = "Cloud Provider"
    }
    labels {
      key         = "version"
      value_type  = "STRING"
      description = "K8s Version of the cluster in format <majorVersion:minorVersion>"
    }
    labels {
      key         = "ccEnabled"
      value_type  = "BOOL"
      description = "Cloud Cost is Enabled or Not <true:false>"
    }
    labels {
      key         = "accountId"
      value_type  = "STRING"
      description = "The accountId"
    }
  }
  label_extractors = {
    "cloudProvider" : "EXTRACT(jsonPayload.harness.cloudProvider)"
    "version" : "EXTRACT(jsonPayload.harness.version)"
    "ccEnabled" : "EXTRACT(jsonPayload.harness.ccEnabled)"
    "accountId" : "EXTRACT(jsonPayload.harness.accountId)"
  }
}
