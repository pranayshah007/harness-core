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

provider "google" {
  credentials = var.credentialsFile
  project = var.projectId
}

module "manager" {
  source = "./manager"
  deployment = var.deployment
}

module "delegate-agent" {
  source = "./delegate-agent"
  deployment = var.deployment
  projectId = var.projectId
}

module "delegate-watcher" {
  source = "./delegate-watcher"
  deployment = var.deployment
}

module "event-service" {
  source = "./event-service"
  deployment = var.deployment
  projectId = var.projectId
}

module "batch-processing" {
  source = "./batch-processing"
  deployment = var.deployment
  projectId = var.projectId
}

module "ccm-worker" {
  source = "./ccm-worker"
  deployment = var.deployment
  projectId = var.projectId
}

module "ccm-dkron" {
  source = "./ccm-dkron"
  deployment = var.deployment
  projectId = var.projectId
}

module "mongo-dashboards" {
  source = "./mongo"
  deployment = var.deployment
}

module "ce-graphql" {
  source = "./ce-graphql"
  deployment = var.deployment
  projectId = var.projectId
}
module "cvng" {
  source = "./cvng"
  deployment = var.deployment
  projectId = var.projectId
}

module "ccm-cloudfunctions" {
  source = "./ccm-cloudfunctions"
  deployment = var.deployment
  projectId = var.projectId
}
