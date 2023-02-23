/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

resource "google_monitoring_dashboard" "mongo_dashboard_custom_metrics_1" {
  dashboard_json = <<EOF

  {
  "displayName": " Mongo Custom Metrics 1 - ${var.deployment}",
  "gridLayout": {
    "columns": "2",
    "widgets": [
      ${local.widgets_1}
    ]
  }
}

EOF
}

resource "google_monitoring_dashboard" "mongo_dashboard_custom_metrics_2" {
  dashboard_json = <<EOF

  {
  "displayName": " Mongo Custom Metrics 2 - ${var.deployment}",
  "gridLayout": {
    "columns": "2",
    "widgets": [
      ${local.widgets_2}
    ]
  }
}

EOF
}

resource "google_monitoring_dashboard" "mongo_dashboard_custom_metrics_3" {
  dashboard_json = <<EOF

  {
  "displayName": " Mongo Custom Metrics 3 - ${var.deployment}",
  "gridLayout": {
    "columns": "2",
    "widgets": [
      ${local.widgets_3}
    ]
  }
}

EOF
}
