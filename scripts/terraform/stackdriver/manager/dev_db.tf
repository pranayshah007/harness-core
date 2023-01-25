/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

resource "google_logging_metric" "dev_db_index_issues" {
  name = join("_", [local.name_prefix, "dev_db_index_issues"])
  description = "Owner: Platform commons"
  filter = join("\n", [local.filter_prefix,
    "jsonPayload.logger:\"io.harness.mongo.IndexManager\"",
    "severity=\"ERROR\""
  ])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
  }
}

resource "google_logging_metric" "dev_db_factory_issues" {
  name = join("_", [local.name_prefix, "dev_db_factory_issues"])
  description = "Owner: Platform commons"
  filter = join("\n", [local.filter_prefix,
    "jsonPayload.logger=\"io.harness.mongo.HObjectFactory\"",
    "severity=\"ERROR\""
  ])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
  }
}

resource "google_logging_metric" "dev_db_native_iterator_issues" {
  name = join("_", [local.name_prefix, "dev_db_native_iterator_issues"])
  filter = join("\n", [local.filter_prefix,
    "(\"Do not use the query as iterator directly.\")",
    "severity=\"ERROR\""
  ])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
  }
}

resource "google_monitoring_dashboard" "dev_db_dashboard" {
  dashboard_json = <<EOF

{
  "displayName": "Database Issues - ${var.deployment}",
  "gridLayout": {
    "columns": "2",
    "widgets": [
      {
        "title": "Issues with collection indexes",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_dev_db_index_issues\" resource.type=\"k8s_container\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_RATE",
                    "crossSeriesReducer": "REDUCE_SUM"
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
        "title": "Issues with morphia object factory",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_dev_db_factory_issues\" resource.type=\"k8s_container\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_RATE",
                    "crossSeriesReducer": "REDUCE_SUM"
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
