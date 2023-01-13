/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

resource "google_logging_metric" "delegate_tasks_queue_time" {
  name = join("_", [local.name_prefix, "delegate_tasks_queue_time"])
  description = "Owner: Platform delegate"
  filter = join("\n", [local.filter_prefix,
    "\"Task assigned to delegate\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "DISTRIBUTION"
    unit = "ms"
  }
  value_extractor = "EXTRACT(jsonPayload.harness.delay)"
  bucket_options {
    explicit_buckets {
      bounds = [0, 1000, 2000, 3000, 5000, 8000, 13000, 21000, 34000, 55000, 89000, 144000, 233000, 377000]
    }
  }
}

resource "google_logging_metric" "delegate_tasks_rebroadcast" {
  name = join("_", [local.name_prefix, "delegate_tasks_rebroadcast"])
  description = "Owner: Platform delegate"
  filter = join("\n", [local.filter_prefix,
    "\"Rebroadcast queued task\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
    labels {
      key = "accountId"
      value_type = "STRING"
      description = "The accountId"
    }
  }
  label_extractors = {
    "accountId": "EXTRACT(jsonPayload.harness.accountId)"
  }
}

resource "google_logging_metric" "delegate_tasks_response" {
  name = join("_", [local.name_prefix, "delegate_tasks_response"])
  description = "Owner: Platform delegate"
  filter = join("\n", [local.filter_prefix,
    "\"response received for task\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
    labels {
      key = "responseCode"
      value_type = "STRING"
      description = "The responseCode"
    }
  }
  label_extractors = {
    "responseCode": "REGEXP_EXTRACT(jsonPayload.message, \"responseCode \\\\[(.+)\\\\]\")",
  }
}

resource "google_logging_metric" "delegate_tasks_creation_by_type" {
  name = join("_", [local.name_prefix, "delegate_tasks_creation_by_type"])
  description = "Owner: Platform delegate"
  filter = join("\n", [local.filter_prefix,
    "\"Queueing async\" OR \"Executing sync\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
    labels {
      key = "taskType"
      value_type = "STRING"
      description = "The type of the task"
    }
    labels {
      key = "taskGroup"
      value_type = "STRING"
      description = "The group of the task"
    }
  }
  label_extractors = {
    "taskType": "EXTRACT(jsonPayload.harness.taskType)",
    "taskGroup": "EXTRACT(jsonPayload.harness.taskGroup)"
  }
}

resource "google_logging_metric" "delegate_tasks_creation_by_mode" {
  name = join("_", [local.name_prefix, "delegate_tasks_creation_by_mode"])
  description = "Owner: Platform delegate"
  filter = join("\n", [local.filter_prefix,
    "\"Queueing async\" OR \"Executing sync\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
    labels {
      key = "syncAsync"
      value_type = "STRING"
      description = "Is the task sync or async"
    }
  }
  label_extractors = {
    "syncAsync": "REGEXP_EXTRACT(jsonPayload.message, \"(sync|async)\")",
  }
}

resource "google_logging_metric" "delegate_tasks_acquire_by_owner" {
  name = join("_", [local.name_prefix, "delegate_tasks_acquire_by_owner"])
  description = "Owner: Platform delegate"
  filter = join("\n", [local.filter_prefix,
    "\"Acquiring delegate task\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
    labels {
      key = "accountId"
      value_type = "STRING"
      description = "The accountId"
    }
  }
  label_extractors = {
    "accountId": "EXTRACT(jsonPayload.harness.accountId)"
  }
}

resource "google_logging_metric" "delegate_tasks_acquire_by_type" {
  name = join("_", [local.name_prefix, "delegate_tasks_acquire_by_type"])
  description = "Owner: Platform delegate"
  filter = join("\n", [local.filter_prefix,
    "\"eligible to execute task\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
    labels {
      key = "taskType"
      value_type = "STRING"
      description = "The type of the task"
    }
    labels {
      key = "taskGroup"
      value_type = "STRING"
      description = "The group of the task"
    }
  }
  label_extractors = {
    "taskType": "EXTRACT(jsonPayload.harness.taskType)",
    "taskGroup": "EXTRACT(jsonPayload.harness.taskGroup)"
  }
}

resource "google_logging_metric" "delegate_tasks_creation_by_rank" {
  name = join("_", [local.name_prefix, "delegate_tasks_creation_by_rank"])
  description = "Owner: Platform delegate"
  filter = join("\n", [local.filter_prefix,
    "\"Queueing async\" OR \"Executing sync\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
    labels {
      key = "rank"
      value_type = "STRING"
      description = "The rank of the task"
    }
  }
  label_extractors = {
    "rank": "EXTRACT(jsonPayload.harness.rank)",
  }
}

resource "google_logging_metric" "delegate_tasks_no_first_pick" {
  name = join("_", [local.name_prefix, "delegate_tasks_no_first_pick"])
  description = "Owner: Platform delegate"
  filter = join("\n", [local.filter_prefix,
    "\"No first attempt delegate was picked\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
    labels {
      key = "taskType"
      value_type = "STRING"
      description = "The type of the task"
    }
    labels {
      key = "taskGroup"
      value_type = "STRING"
      description = "The group of the task"
    }
  }
  label_extractors = {
    "taskType": "EXTRACT(jsonPayload.harness.taskType)",
    "taskGroup": "EXTRACT(jsonPayload.harness.taskGroup)"
  }
}

resource "google_logging_metric" "delegate_tasks_validate" {
  name = join("_", [local.name_prefix, "delegate_tasks_validate"])
  description = "Owner: Platform delegate"
  filter = join("\n", [local.filter_prefix,
    "\"Delegate to validate\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
    labels {
      key = "taskType"
      value_type = "STRING"
      description = "The type of the task"
    }
    labels {
      key = "taskGroup"
      value_type = "STRING"
      description = "The group of the task"
    }
  }
  label_extractors = {
    "taskType": "EXTRACT(jsonPayload.harness.taskType)",
    "taskGroup": "EXTRACT(jsonPayload.harness.taskGroup)"
  }
}

resource "google_logging_metric" "delegate_tasks_assigning" {
  name = join("_", [local.name_prefix, "delegate_tasks_assigning"])
  description = "Owner: Platform delegate"
  filter = join("\n", [local.filter_prefix,
    "\"Assigning sync task to delegate\" OR \"Assigning async task to delegate\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
    labels {
      key = "taskType"
      value_type = "STRING"
      description = "The type of the task"
    }
    labels {
      key = "taskGroup"
      value_type = "STRING"
      description = "The group of the task"
    }
  }
  label_extractors = {
    "taskType": "EXTRACT(jsonPayload.harness.taskType)",
    "taskGroup": "EXTRACT(jsonPayload.harness.taskGroup)"
  }
}

resource "google_logging_metric" "delegate_no_eligible_by_account" {
  name = join("_", [local.name_prefix, "delegate_no_eligible_by_account"])
  description = "Owner: Platform delegate"
  filter = join("\n", [local.filter_prefix,
    "\"delegates active but no delegates are eligible to execute task\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
    labels {
      key = "accountId"
      value_type = "STRING"
      description = "The accountId"
    }
  }
  label_extractors = {
    "accountId": "EXTRACT(jsonPayload.harness.accountId)"
  }
}

resource "google_logging_metric" "delegate_no_eligible_by_task" {
  name = join("_", [local.name_prefix, "delegate_no_eligible_by_task"])
  description = "Owner: Platform delegate"
  filter = join("\n", [local.filter_prefix,
    "\"delegates active but no delegates are eligible to execute task\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
    labels {
      key = "taskType"
      value_type = "STRING"
      description = "The type of the task"
    }
    labels {
      key = "taskGroup"
      value_type = "STRING"
      description = "The group of the task"
    }
  }
  label_extractors = {
    "taskType": "EXTRACT(jsonPayload.harness.taskType)",
    "taskGroup": "EXTRACT(jsonPayload.harness.taskGroup)"
  }
}

resource "google_logging_metric" "delegate_process_restarted" {
  name = join("_", [local.name_prefix, "delegate_process_restarted"])
  description = "Owner: Platform delegate"
  filter = join("\n", [local.filter_prefix,
    "\"Delegate restarted\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
    labels {
      key = "accountId"
      value_type = "STRING"
      description = "The accountId"
    }
  }
  label_extractors = {
    "accountId": "EXTRACT(jsonPayload.harness.accountId)"
  }
}

resource "google_monitoring_dashboard" "delegate_tasks_dashboard" {
  dashboard_json = <<EOF

{
  "displayName": "Delegate Tasks - ${var.deployment}",
  "gridLayout": {
    "columns": "2",
    "widgets": [
      {
        "title": "Heatmap of task assigment",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_delegate_tasks_queue_time\" resource.type=\"k8s_container\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_DELTA",
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
      },
      {
        "title": "Task Creation, Response, and Rebroadcast",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_delegate_tasks_rebroadcast\" resource.type=\"k8s_container\"",
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
            },
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_delegate_tasks_response\" resource.type=\"k8s_container\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_RATE",
                    "crossSeriesReducer": "REDUCE_SUM",
                    "groupByFields": [
                      "metric.label.\"responseCode\""
                    ]
                  },
                  "secondaryAggregation": {}
                },
                "unitOverride": "1"
              },
              "plotType": "LINE",
              "minAlignmentPeriod": "60s"
            },
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_delegate_tasks_creation_by_type\" resource.type=\"k8s_container\"",
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
        "title": "Task Acquire and Validate",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_delegate_tasks_acquire_by_owner\" resource.type=\"k8s_container\"",
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
            },
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_delegate_tasks_validate\" resource.type=\"k8s_container\"",
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
            },
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_delegate_tasks_no_first_pick\" resource.type=\"k8s_container\"",
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
        "title": "Delegate Task Creation by Sync Async",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_delegate_tasks_creation_by_mode\" resource.type=\"k8s_container\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_RATE",
                    "crossSeriesReducer": "REDUCE_SUM",
                    "groupByFields": [
                      "metric.label.\"syncAsync\""
                    ]
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
        "title": "Delegate Task Creation by Rank",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_delegate_tasks_creation_by_rank\" resource.type=\"k8s_container\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_RATE",
                    "crossSeriesReducer": "REDUCE_SUM",
                    "groupByFields": [
                      "metric.label.\"rank\""
                    ]
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
        "title": "Delegate Task Acquire by Account",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_delegate_tasks_acquire_by_owner\" resource.type=\"k8s_container\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_RATE",
                    "crossSeriesReducer": "REDUCE_SUM",
                    "groupByFields": [
                      "metric.label.\"accountId\""
                    ]
                  },
                  "secondaryAggregation": {},
                  "pickTimeSeriesFilter": {
                    "rankingMethod": "METHOD_MAX",
                    "numTimeSeries": 5,
                    "direction": "TOP"
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
        "title": "Delegate Task Acquire by Task Group",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_delegate_tasks_acquire_by_type\" resource.type=\"k8s_container\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_RATE",
                    "crossSeriesReducer": "REDUCE_SUM",
                    "groupByFields": [
                      "metric.label.\"taskGroup\""
                    ]
                  },
                  "secondaryAggregation": {},
                  "pickTimeSeriesFilter": {
                    "rankingMethod": "METHOD_MAX",
                    "numTimeSeries": 5,
                    "direction": "TOP"
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
        "title": "Delegate Task Validation by Task Group",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_delegate_tasks_validate\" resource.type=\"k8s_container\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_RATE",
                    "crossSeriesReducer": "REDUCE_SUM",
                    "groupByFields": [
                      "metric.label.\"taskGroup\""
                    ]
                  },
                  "secondaryAggregation": {},
                  "pickTimeSeriesFilter": {
                    "rankingMethod": "METHOD_MAX",
                    "numTimeSeries": 5,
                    "direction": "TOP"
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
        "title": "Delegate Task no first pick by Task Group",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_delegate_tasks_no_first_pick\" resource.type=\"k8s_container\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_RATE",
                    "crossSeriesReducer": "REDUCE_SUM",
                    "groupByFields": [
                      "metric.label.\"taskGroup\""
                    ]
                  },
                  "secondaryAggregation": {},
                  "pickTimeSeriesFilter": {
                    "rankingMethod": "METHOD_MAX",
                    "numTimeSeries": 5,
                    "direction": "TOP"
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
        "title": "Delegate Task Assignment by Task Group",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_delegate_tasks_assigning\" resource.type=\"k8s_container\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_RATE",
                    "crossSeriesReducer": "REDUCE_SUM",
                    "groupByFields": [
                      "metric.label.\"taskGroup\""
                    ]
                  },
                  "secondaryAggregation": {},
                  "pickTimeSeriesFilter": {
                    "rankingMethod": "METHOD_MAX",
                    "numTimeSeries": 5,
                    "direction": "TOP"
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
        "title": "Delegate Rebroadcast by Account",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_delegate_tasks_rebroadcast\" resource.type=\"k8s_container\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_RATE",
                    "crossSeriesReducer": "REDUCE_SUM",
                    "groupByFields": [
                      "metric.label.\"accountId\""
                    ]
                  },
                  "secondaryAggregation": {},
                  "pickTimeSeriesFilter": {
                    "rankingMethod": "METHOD_MAX",
                    "numTimeSeries": 10,
                    "direction": "TOP"
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
        "title": "No Eligible Delegates by Account",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_delegate_no_eligible_by_account\" resource.type=\"k8s_container\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_RATE",
                    "crossSeriesReducer": "REDUCE_SUM",
                    "groupByFields": [
                      "metric.label.\"accountId\""
                    ]
                  },
                  "secondaryAggregation": {},
                  "pickTimeSeriesFilter": {
                    "rankingMethod": "METHOD_MAX",
                    "numTimeSeries": 5,
                    "direction": "TOP"
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
        "title": "No Eligible Delegates by Task",
        "xyChart": {
          "dataSets": [
            {
              "timeSeriesQuery": {
                "timeSeriesFilter": {
                  "filter": "metric.type=\"logging.googleapis.com/user/x_${var.deployment}_delegate_no_eligible_by_task\" resource.type=\"k8s_container\"",
                  "aggregation": {
                    "perSeriesAligner": "ALIGN_RATE",
                    "crossSeriesReducer": "REDUCE_SUM",
                    "groupByFields": [
                      "metric.label.\"taskType\""
                    ]
                  },
                  "secondaryAggregation": {},
                  "pickTimeSeriesFilter": {
                    "rankingMethod": "METHOD_MAX",
                    "numTimeSeries": 5,
                    "direction": "TOP"
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
      }
    ]
  }
}


EOF
}
