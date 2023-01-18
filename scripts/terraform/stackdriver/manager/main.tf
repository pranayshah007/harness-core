/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

variable "deployment" {
  type = string
}

locals {
  qa_filter_prefix = join("\n", [
    "resource.labels.cluster_name=\"qa-private\"",
    "resource.labels.container_name=\"manager-qa\""
  ])

  stress_filter_prefix = join("\n", [
    "resource.labels.cluster_name=\"qa-stress\"",
    "resource.labels.container_name=\"manager-stress\""
  ])

  prod_filter_prefix = join("\n", [
    "resource.labels.cluster_name=\"prod-private-uswest1-primary\"",
    "resource.labels.container_name=\"manager\"",
    "resource.labels.namespace_name:\"manager-prod-\""
  ])

  freemium_filter_prefix = join("\n", [
    "resource.type=\"k8s_container\"",
    "resource.labels.container_name=\"manager\"",
    "resource.labels.namespace_name:\"manager-free-\""
  ])

  filter_prefix = (var.deployment == "qa"     ? local.qa_filter_prefix :
                  (var.deployment == "stress" ? local.stress_filter_prefix :
                  (var.deployment == "prod"   ? local.prod_filter_prefix :
                                                local.freemium_filter_prefix)))

  name_prefix = join("_", ["x", var.deployment])
}
