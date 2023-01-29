/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

resource "google_logging_metric" "iterators_working_on_entity_by_thread_pool" {
  name = join("_", [local.name_prefix, "iterators_working_on_entity"])
  description = "Owner: Platform commons"
  filter = join("\n", [local.filter_prefix,
    "\"Working on entity\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
    labels {
      key = "thread_pool"
      value_type = "STRING"
      description = "The thread pool the entity is processed in"
    }
  }
  label_extractors = {
    "thread_pool": "EXTRACT(jsonPayload.thread)",
  }
}

resource "google_logging_metric" "iterators_delays" {
  name = join("_", [local.name_prefix, "iterators_delays"])
  description = "Owner: Platform commons"
  filter = join("\n", [
    local.filter_prefix,
    "\"Working on entity\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "DISTRIBUTION"
    unit = "ms"
    labels {
      key = "thread_pool"
      value_type = "STRING"
      description = "The thread pool the entity is processed in"
    }
  }
  value_extractor = "EXTRACT(jsonPayload.harness.delay)"
  bucket_options {
    explicit_buckets {
      bounds = [0, 1000, 30000, 60000, 300000, 1500000, 3000000, 6000000]
    }
  }
  label_extractors = {
    "thread_pool": "EXTRACT(jsonPayload.thread)"
  }
}

resource "google_logging_metric" "iterators_process_time" {
  name = join("_", [local.name_prefix, "iterators_process_time"])
  description = "Owner: Platform commons"
  filter = join("\n", [
    local.filter_prefix,
    "\"Done with entity\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "DISTRIBUTION"
    unit = "ms"
    labels {
      key = "thread_pool"
      value_type = "STRING"
      description = "The thread pool the entity is processed in"
    }
  }
  value_extractor = "EXTRACT(jsonPayload.harness.processTime)"
  bucket_options {
    explicit_buckets {
      bounds = [1000, 30000, 60000, 300000, 1500000, 3000000, 6000000]
    }
  }
  label_extractors = {
    "thread_pool": "EXTRACT(jsonPayload.thread)"
  }
}


resource "google_logging_metric" "iterators_issues" {
  name = join("_", [local.name_prefix, "iterators_issues"])
  description = "Owner: Platform commons"
  filter = join("\n", [local.filter_prefix,
    "MongoPersistenceIterator",
    "severity=\"ERROR\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
    labels {
      key = "thread_pool"
      value_type = "STRING"
      description = "The thread pool the entity is processed in"
    }
  }
  label_extractors = {
    "thread_pool": "EXTRACT(jsonPayload.thread)"
  }
}

resource "google_monitoring_dashboard" "iterators_dashboard" {
  dashboard_json = <<EOF

{
  "displayName": "Iterators - ${var.deployment}",
  "gridLayout": {
    "columns": "2",
    "widgets": [
      {
        "title": "Delay by thread pool",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_iterators_delays\" resource.type=\"k8s_container\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_SUM",
                    "crossSeriesReducer": "REDUCE_PERCENTILE_99",
                    "groupByFields": [
                      "metric.label.\"thread_pool\""
                    ]
                  },
                  "secondaryAggregation": {}
                },
                "unitOverride": "ms"
              },
              "plotType": "LINE",
              "minAlignmentPeriod": "60s"
            }
          ],
          "timeshiftDuration": "0s",
          "yAxis": {
            "label": "y1Axis",
            "scale": "LOG10"
          },
          "chartOptions": {
            "mode": "COLOR"
          }
        }
      },
      {
        "title": "Handle by thread pool",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_iterators_working_on_entity\" resource.type=\"k8s_container\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_RATE",
                    "crossSeriesReducer": "REDUCE_SUM",
                    "groupByFields": [
                      "metric.label.\"thread_pool\""
                    ]
                  }
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
        "title": "Issues",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_iterators_issues\" resource.type=\"k8s_container\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_RATE",
                    "crossSeriesReducer": "REDUCE_SUM",
                    "groupByFields": [
                      "metric.label.\"thread_pool\""
                    ]
                  }
                },
                "unitOverride": "1"
              },
              "plotType": "STACKED_AREA",
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
        "title": "Process time",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_iterators_process_time\" resource.type=\"k8s_container\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_PERCENTILE_99",
                    "crossSeriesReducer": "REDUCE_MAX",
                    "groupByFields": [
                      "metric.label.\"thread_pool\""
                    ]
                  }
                },
                "unitOverride": "ms"
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
      }
    ]
  }
}

EOF
}
