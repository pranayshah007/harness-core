/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

variable "deployment" {
  type = string
}

variable "projectId" {
  type = string
}

locals {
  qa_filter_prefix = join("\n", [
    "labels.managerHost=\"qa.harness.io\"",
    "labels.app=\"delegate\""
  ])

  stress_filter_prefix = join("\n", [
    "labels.managerHost=\"stress.harness.io\"",
    "labels.app=\"delegate\""
  ])

  prod_filter_prefix = join("\n", [
    "labels.managerHost=\"app.harness.io\"",
    "labels.app=\"delegate\""
  ])

  freemium_filter_prefix = join("\n", [
    "labels.managerHost=\"app.harness.io/gratis\"",
    "labels.app=\"delegate\""
  ])

  filter_prefix = (var.deployment == "qa" ? local.qa_filter_prefix :
    (var.deployment == "stress" ? local.stress_filter_prefix :
      (var.deployment == "prod" ? local.prod_filter_prefix :
  local.freemium_filter_prefix)))

  name_prefix = join("_", ["x", var.deployment, "agent"])
}

resource "google_monitoring_dashboard" "delegate_dashboard" {
  dashboard_json = <<EOF

{
  "displayName": "Delegate Agent - ${var.deployment}",
  "gridLayout": {
    "columns": "2",
    "widgets": [
      {
        "title": "Delegate new processe starting",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_agent_starting\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_RATE"
                  },
                  "secondaryAggregation": {}
                },
                "unitOverride": "1"
              },
              "plotType": "LINE",
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
      },
      {
        "title": "Delegate new processe starting by account",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_agent_starting_by_account\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_RATE",
                    "crossSeriesReducer": "REDUCE_SUM",
                    "groupByFields": [
                      "metric.label.\"accountId\""
                    ]
                  },
                  "secondaryAggregation": {}
                },
                "unitOverride": "1"
              },
              "plotType": "STACKED_BAR",
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
      },
      {
        "title": "Delegate new process restarting",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_delegate_process_restarted\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_RATE",
                    "crossSeriesReducer": "REDUCE_SUM"
                  },
                  "secondaryAggregation": {}
                },
                "unitOverride": "1"
              },
              "plotType": "LINE",
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
      },
      {
        "title": "Delegate new process restarting by account",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_delegate_process_restarted\" resource.type=\"k8s_container\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_RATE",
                    "crossSeriesReducer": "REDUCE_SUM",
                    "groupByFields": [
                      "metric.label.\"accountId\""
                    ]
                  },
                  "secondaryAggregation": {}
                },
                "unitOverride": "1"
              },
              "plotType": "STACKED_BAR",
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
