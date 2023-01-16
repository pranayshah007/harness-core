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
  name_prefix = join("_", ["x", "mongo", var.deployment])
  widgets_1 = join(",", data.template_file.widget_template_1.*.rendered)
  widgets_2 = join(",", data.template_file.widget_template_2.*.rendered)
  widgets_3 = join(",", data.template_file.widget_template_3.*.rendered)
}
