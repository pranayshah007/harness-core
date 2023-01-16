/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

resource "google_logging_metric" "perpetual_task_delay" {
  name        = join("_", [local.name_prefix, "perpetual_task_delay"])
  description = "Owner: Platform commons"
  filter = join("\n",
    [local.filter_prefix,
  "\"first poll from this delegate for task\" OR \"update for task\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "DISTRIBUTION"
    unit        = "ms"
  }
  value_extractor = "EXTRACT(jsonPayload.harness.delay)"
  bucket_options {
    explicit_buckets {
      bounds = [0, 1000, 3000, 15000, 30000, 60000, 300000, 1500000, 3000000, 6000000]
    }
  }
}

resource "google_monitoring_dashboard" "delegate_perpetual_tasks_dashboard" {
  dashboard_json = <<EOF

{
  "displayName": "Delegate Perpetual Tasks - ${var.deployment}",
  "gridLayout": {
    "columns": "2",
    "widgets": [
      {
        "title": "Heatmap of perpetual task assigment",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_agent_perpetual_task_delay\" resource.type=\"global\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_SUM",
                    "crossSeriesReducer": "REDUCE_SUM"
                  },
                  "secondaryAggregation": {}
                },
                "unitOverride": "ms"
              },
              "plotType": "HEATMAP",
              "minAlignmentPeriod": "60s"
            }
          ],
          "timeshiftDuration": "0s",
          "yAxis": {
            "label": "y1Axis",
            "scale": "LINEAR"
          },
          "chartOptions": {
            "mode": "COLOR"
          }
        }
      }
    ]
  }
}

EOF
}
