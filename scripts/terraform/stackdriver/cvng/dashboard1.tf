/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

resource "google_monitoring_dashboard" "cvng_dashboard_1" {
  dashboard_json = <<EOF

{
  "displayName": "CVNG dashboard-1-${var.deployment}",
  "gridLayout": {
    "columns": "2",
    "widgets": [
  {
    "title": "Data collection Task metrics - data_collection_task_total_time",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_DELTA"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/data_collection_task_total_time\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_PERCENTILE_99",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_DELTA"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Data collection Task metrics - data_collection_task_wait_time",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_DELTA"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/data_collection_task_wait_time\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_PERCENTILE_99",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_DELTA"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Data collection Task metrics - data_collection_task_running_time",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_DELTA"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/data_collection_task_running_time\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_PERCENTILE_99",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_DELTA"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Data collection Task metrics - data_collection_task_failed_count",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_RATE"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/data_collection_task_failed_count\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_COUNT"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Data collection Task metrics - data_collection_task_success_count",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_RATE"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/data_collection_task_success_count\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_COUNT"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Task count metrics - verification_job_instance_timeout_count",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_RATE"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/verification_job_instance_timeout_count\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_COUNT"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Task count metrics - verification_job_instance_failed_count",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_RATE"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/verification_job_instance_failed_count\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_COUNT"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Task count metrics - verification_job_instance_success_count",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_RATE"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/verification_job_instance_success_count\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_COUNT"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Task count metrics - verification_job_instance_verification_passed_count",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_RATE"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/verification_job_instance_verification_passed_count\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_COUNT"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Task count metrics - verification_job_instance_verification_failed_count",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_RATE"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/verification_job_instance_verification_failed_count\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_COUNT"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Task count metrics - learning_engine_task_timeout_count",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_RATE"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/learning_engine_task_timeout_count\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_COUNT"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Task count metrics - learning_engine_task_failed_count",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_RATE"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/learning_engine_task_failed_count\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_COUNT"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Task count metrics - learning_engine_task_success_count",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_RATE"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/learning_engine_task_success_count\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_COUNT"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Api Call Log metrics - api_call_execution_time",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_DELTA"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/api_call_execution_time\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_PERCENTILE_99",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_DELTA"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Api Call Log metrics - api_call_response_code_1xx",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_RATE"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/api_call_response_code_1xx\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_COUNT"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Api Call Log metrics - api_call_response_code_2xx",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_RATE"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/api_call_response_code_2xx\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_COUNT"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Api Call Log metrics - api_call_response_code_3xx",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_RATE"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/api_call_response_code_3xx\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_COUNT"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Api Call Log metrics - api_call_response_code_4xx",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_RATE"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/api_call_response_code_4xx\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_COUNT"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Api Call Log metrics - api_call_response_code_5xx",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_RATE"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/api_call_response_code_5xx\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_COUNT"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Task duration metrics - learning_engine_task_total_time",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_DELTA"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/learning_engine_task_total_time\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_PERCENTILE_99",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_DELTA"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Task duration metrics - learning_engine_task_wait_time",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_DELTA"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/learning_engine_task_wait_time\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_PERCENTILE_99",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_DELTA"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Task duration metrics - learning_engine_task_running_time",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_DELTA"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/learning_engine_task_running_time\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_PERCENTILE_99",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_DELTA"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "Task duration metrics - verification_job_instance_extra_time",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_DELTA"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/verification_job_instance_extra_time\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_PERCENTILE_99",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_DELTA"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "CVNG tasks status count - cvng_step_task_non_final_status_count",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_MEAN"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/cvng_step_task_non_final_status_count\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_MEAN"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "CVNG tasks status count - cvng_step_task_in_progress_count",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_MEAN"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/cvng_step_task_in_progress_count\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_MEAN"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "CVNG tasks status count - analysis_state_machine_non_final_status_count",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_MEAN"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/analysis_state_machine_non_final_status_count\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_MEAN"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "CVNG tasks status count - analysis_state_machine_created_count",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_MEAN"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/analysis_state_machine_created_count\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_MEAN"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "CVNG tasks status count - analysis_state_machine_running_count",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_MEAN"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/analysis_state_machine_running_count\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_MEAN"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "CVNG tasks status count - analysis_state_machine_retry_count",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_MEAN"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/analysis_state_machine_retry_count\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_MEAN"
              }
            }
          },
          "plotType": "LINE"
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
    "title": "CVNG tasks status count - analysis_state_machine_transition_count",
    "xyChart": {
      "dataSets": [
        {
          "timeSeriesQuery": {
            "apiSource": "DEFAULT_CLOUD",
            "timeSeriesFilter": {
              "aggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_NONE",
                "perSeriesAligner": "ALIGN_MEAN"
              },
              "filter": "metric.type=\"custom.googleapis.com/opencensus/analysis_state_machine_transition_count\" resource.type=\"k8s_container\"",
              "secondaryAggregation": {
                "alignmentPeriod": "60s",
                "crossSeriesReducer": "REDUCE_SUM",
                "groupByFields": [
                  "metric.label.\"environment\""
                ],
                "perSeriesAligner": "ALIGN_MEAN"
              }
            }
          },
          "plotType": "LINE"
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
