/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.core.failurestrategy.v1;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PIPELINE)
public interface NGFailureActionTypeConstantsV1 {
  String IGNORE = "ignore";
  String RETRY = "retry";
  String ABORT = "abort";
  String STAGE_ROLLBACK = "stage-rollback";
  String MANUAL_INTERVENTION = "manual-intervention";
  String MARK_AS_SUCCESS = "success";
  String PIPELINE_ROLLBACK = "pipeline-rollback";
  String MARK_AS_FAILURE = "fail";
  String RETRY_STEP_GROUP = "retry-step-group";
}
