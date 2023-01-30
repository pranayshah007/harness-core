/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

resource "google_logging_metric" "starting" {
  name        = join("_", [local.name_prefix, "starting"])
  description = "Owner: Platform delegate"
  filter = join("\n", [local.filter_prefix,
  "\"Starting Delegate\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    labels {
      key         = "version"
      value_type  = "STRING"
      description = "Per version"
    }
  }
  label_extractors = {
    "version" : "EXTRACT(labels.version)"
  }
}

resource "google_logging_metric" "starting_by_account" {
  name        = join("_", [local.name_prefix, "starting_by_account"])
  description = "Owner: Platform delegate"
  filter = join("\n", [local.filter_prefix,
  "\"Starting Delegate\""])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
    labels {
      key         = "accountId"
      value_type  = "STRING"
      description = "The accountId"
    }
  }
  label_extractors = {
    "accountId" : "EXTRACT(labels.accountId)"
  }
}
