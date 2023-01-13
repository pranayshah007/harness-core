/*
 * Copyright 2019 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

variable "workflow-scale" {}

locals {
    generic_instances = list(var.workflow-scale ? "1" : "",
                             var.workflow-scale ? "2" : "",
                             var.workflow-scale ? "3" : "",
                             var.workflow-scale ? "4" : "",
                             var.workflow-scale ? "5" : "",
                             var.workflow-scale ? "6" : "",
                             var.workflow-scale ? "7" : "",
                             var.workflow-scale ? "8" : "",
                             var.workflow-scale ? "9" : "",
                             var.workflow-scale ? "10" : "",
                             var.workflow-scale ? "11" : "",
                             var.workflow-scale ? "12" : "")
}

output "generic_instances" {
  value = compact(local.generic_instances)
}
