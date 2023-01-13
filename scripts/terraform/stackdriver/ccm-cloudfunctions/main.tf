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
  name_prefix = join("_", ["x", var.deployment])

  # prod and freemium is #ce-alerts channel. dev and qa is #ce-alerts-test channel
  slack_prod_channel = "projects/${var.projectId}/notificationChannels/10185135917587539827"
  slack_dev_channel  = "projects/${var.projectId}/notificationChannels/13768296773189683769"
  slack_qa_channel   = "projects/${var.projectId}/notificationChannels/11524768178699293863"
  slack_ceqa_channel   = "projects/${var.projectId}/notificationChannels/2940114166643309654"

  # prod is ce-alerts@harness.io, rest is ce-alerts-qa@harness.io
  email_prod_channel = "projects/${var.projectId}/notificationChannels/16286565924796139541"
  email_dev_channel  = "projects/${var.projectId}/notificationChannels/17855665763510449367"
  email_qa_channel   = "projects/${var.projectId}/notificationChannels/9385478850545552747"
  email_ceqa_channel   = "projects/${var.projectId}/notificationChannels/17367544434138981620"

}
