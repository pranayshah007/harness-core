/*
 * Copyright 2022 Harness Inc. All rights reserved.
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


resource "google_monitoring_dashboard" "cvng_dashboard_$batch_no" {
  dashboard_json = <<EOF

{
  "displayName": "CVNG dashboard-$batch_no-${var.deployment}",
  "gridLayout": {
    "columns": "2",
    "widgets": $json_array
  }
}


  EOF
}
