/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.executables;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.opaclient.OpaServiceClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.steps.executable.SyncExecutableWithCapabilities;
import io.harness.utils.PolicyEvalUtils;

import com.google.inject.Inject;

// Pipeline Sync Executable With RBAC and postSyncValidation
@OwnedBy(HarnessTeam.PIPELINE)
public abstract class PipelineSyncExecutable extends SyncExecutableWithCapabilities {
  @Inject OpaServiceClient opaServiceClient;

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {}

  // evaluating policies added in advanced section of the steps and updating status and failure info in the step
  // response
  @Override
  public StepResponse postSyncValidate(
      Ambiance ambiance, StepBaseParameters stepParameters, StepResponse stepResponse) {
    if (Status.SUCCEEDED.equals(stepResponse.getStatus())) {
      return PolicyEvalUtils.evalPolicies(ambiance, stepParameters, stepResponse, opaServiceClient);
    }
    return stepResponse;
  }
}
