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
  slack_prod_channel = "projects/${var.projectId}/notificationChannels/6704964416716156071"
  slack_dev_channel = "projects/${var.projectId}/notificationChannels/6704964416716156071"
  slack_qa_channel = "projects/${var.projectId}/notificationChannels/6704964416716156071"

  # prod is ce-alerts@harness.io, rest is ce-alerts-qa@harness.io
  email_prod_channel = "projects/${var.projectId}/notificationChannels/6704964416716156071"
  email_dev_channel  = "projects/${var.projectId}/notificationChannels/6704964416716156071"
  email_qa_channel   = "projects/${var.projectId}/notificationChannels/6704964416716156071"
}
