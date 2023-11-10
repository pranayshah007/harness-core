/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

variable "credentialsFile" {
  type = string
}

variable "deployment" {
  type = string
}

variable "projectId" {
  type = string
}

variable "projectIdSecondary" {
  type = string
}

variable "region" {
  type = string
}

provider "google" {
  credentials = var.credentialsFile
  project = var.projectId
}

provider "google-beta" {
  credentials = var.credentialsFile
  project = var.projectId
  region = var.region
}

module "ce-cloudfunctions" {
  source = "./ce"
  deployment = var.deployment
  projectId = var.projectId
  projectIdSecondary = var.projectIdSecondary
  region = var.region
}
