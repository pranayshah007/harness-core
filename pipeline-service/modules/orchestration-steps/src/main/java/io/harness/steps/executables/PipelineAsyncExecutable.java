/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.executables;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.opaclient.OpaServiceClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.steps.executable.AsyncExecutableWithCapabilities;
import io.harness.utils.PolicyEvalUtils;

import com.google.inject.Inject;

// Async Executable with RBAC, Rollback and postAsyncValidation
@OwnedBy(PIPELINE)
public abstract class PipelineAsyncExecutable extends AsyncExecutableWithCapabilities {
  @Inject OpaServiceClient opaServiceClient;

  // evaluating policies added in advanced section of the steps and updating status and failure info in the step
  // response
  @Override
  public StepResponse postAsyncValidate(
      Ambiance ambiance, StepBaseParameters stepParameters, StepResponse stepResponse) {
    if (Status.SUCCEEDED.equals(stepResponse.getStatus())) {
      return PolicyEvalUtils.evalPolicies(ambiance, stepParameters, stepResponse, opaServiceClient);
    }
    return stepResponse;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {}
}
