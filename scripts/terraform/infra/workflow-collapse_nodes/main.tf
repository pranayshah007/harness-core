/*
 * Copyright 2019 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

variable "workflow-collapse_nodes" {}

locals {
    generic_instances = list(var.workflow-collapse_nodes ? "1" : "",
                             var.workflow-collapse_nodes ? "2" : "",
                             var.workflow-collapse_nodes ? "3" : "",
                             var.workflow-collapse_nodes ? "4" : "",
                             var.workflow-collapse_nodes ? "5" : "",
                             var.workflow-collapse_nodes ? "6" : "",
                             var.workflow-collapse_nodes ? "7" : "",
                             var.workflow-collapse_nodes ? "8" : "",
                             var.workflow-collapse_nodes ? "9" : "",
                             var.workflow-collapse_nodes ? "10" : "")
}

output "generic_instances" {
  value = compact(local.generic_instances)
}
