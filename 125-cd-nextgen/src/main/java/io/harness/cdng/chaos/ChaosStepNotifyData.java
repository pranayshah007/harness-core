/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.chaos;

import io.harness.pms.sdk.core.data.Outcome;
import io.harness.tasks.ResponseData;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@FieldNameConstants(innerTypeName = "ChaosStepNotifyDataKeys")
@Builder
public class ChaosStepNotifyData implements ResponseData, Outcome {
  String phase;
  String experimentRunId;
  Double resiliencyScore;
  Integer faultsPassed;
  Integer faultsFailed;
  Integer faultsAwaited;
  Integer faultsStopped;
  Integer faultsNa;
  Integer totalFaults;

  public boolean isSuccess() {
    return phase != null
        && (phase.equalsIgnoreCase("completed") || phase.equalsIgnoreCase("completed_with_probe_failure")
            || phase.equalsIgnoreCase("completed_with_error"));
  }
}
