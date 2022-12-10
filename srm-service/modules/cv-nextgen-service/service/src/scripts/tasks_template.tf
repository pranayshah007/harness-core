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