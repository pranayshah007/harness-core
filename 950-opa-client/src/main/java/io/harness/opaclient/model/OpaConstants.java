/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.opaclient.model;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PIPELINE)
public interface OpaConstants {
  String OPA_EVALUATION_TYPE_CONNECTOR = "connector";
  String OPA_EVALUATION_TYPE_SECRET = "secret";

  String OPA_EVALUATION_ACTION_CONNECTOR_SAVE = "onsave";

  String OPA_EVALUATION_TYPE_PIPELINE = "pipeline";
  String OPA_EVALUATION_ACTION_PIPELINE_RUN = "onrun";
  String OPA_EVALUATION_ACTION_SAVE = "onsave";

  String OPA_STATUS_PASS = "pass";
  String OPA_STATUS_WARNING = "warning";
  String OPA_STATUS_ERROR = "error";
  String OPA_EVALUATION_TYPE_TEMPLATE = "template";
}
