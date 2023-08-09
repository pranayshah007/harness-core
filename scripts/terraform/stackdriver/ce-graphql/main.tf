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
    "resource.type=\"k8s_container\"",
    "resource.labels.project_id=\"qa-setup\""
    "resource.labels.location=\"us-west1\""
    "resource.labels.cluster_name=\"qa-private\"",
    "labels.k8s-pod/app=(\"ce-nextgen\" OR \"batch-processing\")"
  ])

  stress_filter_prefix = join("\n", [
    "resource.type=\"k8s_container\"",
    "resource.labels.project_id=\"qa-setup\""
    "resource.labels.location=\"us-west1\""
    "resource.labels.cluster_name=\"qa-stress\"",
    "labels.k8s-pod/app=(\"ce-nextgen\" OR \"batch-processing\")"
  ])

  prod_filter_prefix = join("\n", [
    "resource.type=\"k8s_container\"",
    "resource.labels.project_id=\"prod-setup-205416\""
    "resource.labels.location=\"us-west1\""
    "resource.labels.cluster_name=\"prod-private-uswest1-primary\"",
    "labels.k8s-pod/app=(\"ce-nextgen\" OR \"batch-processing\")"
  ])

  dev_filter_prefix = join("\n", [
    "resource.type=\"k8s_container\"",
    "resource.labels.project_id=\"ccm-play\""
    "resource.labels.location=\"us-central1-c\""
    "resource.labels.cluster_name=\"ce-dev\"",
    "labels.k8s-pod/app=(\"ce-nextgen\" OR \"batch-processing\")"
  ])

  filter_prefix = (var.deployment == "qa" ? local.qa_filter_prefix :
      (var.deployment == "dev" ? local.dev_filter_prefix :
        (var.deployment == "stress" ? local.stress_filter_prefix :
          (var.deployment == "prod" ? local.prod_filter_prefix :
  local.prod_filter_prefix))))

  name_prefix = join("_", ["x", var.deployment])

  # prod and freemium is #ce-alerts channel. dev and qa is #ce-alerts-test channel
  slack_prod_channel = "projects/${var.projectId}/notificationChannels/10185135917587539827"
  slack_dev_channel = "projects/${var.projectId}/notificationChannels/13768296773189683769"
  slack_qa_channel = "projects/${var.projectId}/notificationChannels/11524768178699293863"

  # prod is ce-alerts@harness.io, rest is ce-alerts-qa@harness.io
  email_prod_channel = "projects/${var.projectId}/notificationChannels/16286565924796139541"
  email_dev_channel  = "projects/${var.projectId}/notificationChannels/17855665763510449367"
  email_qa_channel   = "projects/${var.projectId}/notificationChannels/9385478850545552747"

}
