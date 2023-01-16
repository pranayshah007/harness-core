/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

resource "google_logging_metric" "wait_engine_duplicate_issues" {
  name = join("_", [local.name_prefix, "wait_engine_duplicate_issues"])
  description = "Owner: CDC orchestration"
  filter = join("\n", [local.filter_prefix,
    "Unexpected rate of DuplicateKeyException per correlation"])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
    labels {
      key = "delegate"
      value_type = "STRING"
      description = "The class of the entity to operate over"
    }
  }
  label_extractors = {
    "delegate": "EXTRACT(jsonPayload.harness.delegateId)"
  }
}
