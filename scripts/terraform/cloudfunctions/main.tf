variable "credentialsFile" {
  type = string
}

variable "deployment" {
  type = string
}

variable "projectId" {
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
  region = var.region
}

