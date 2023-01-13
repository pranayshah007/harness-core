/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

resource "google_logging_metric" "ce_event_publish_fails" {
  name = join("_", [local.name_prefix, "ce_event_publish_fails"])
  description = "Number of times publish call failed. Owner: CE"
  filter = join("\n", [
    local.filter_prefix,
    "jsonPayload.logger=\"io.harness.event.client.impl.tailer.ChronicleEventTailer\"",
    "jsonPayload.message=\"Exception during message publish\""
  ])
  metric_descriptor {
    metric_kind = "DELTA"
    value_type = "INT64"
    labels {
      key = "accountId"
      value_type = "STRING"
      description = "The accountId"
    }
  }
  label_extractors = {
    "accountId" : "EXTRACT(labels.accountId)"
  }
}
