/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.executable;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.common.rollback.RollbackExecutableUtility;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
// Async Executable With RBAC, Rollback and postAsyncValidation
public abstract class AsyncExecutableWithCapabilities implements AsyncExecutableWithRbac<StepBaseParameters> {
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public void handleFailure(Ambiance ambiance, StepBaseParameters stepParameters, AsyncExecutableResponse response,
      Map<String, String> metadata) {
    RollbackExecutableUtility.publishRollbackInfo(ambiance, stepParameters, metadata, executionSweepingOutputService);
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepBaseParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    StepResponse stepResponse = handleAsyncResponseInternal(ambiance, stepParameters, responseDataMap);
    return postAsyncValidate(ambiance, stepParameters, stepResponse);
  }

  public abstract StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepBaseParameters stepParameters, Map<String, ResponseData> responseDataMap);

  // evaluating policies added in advanced section of the steps and updating status and failure info in the step
  // response
  public StepResponse postAsyncValidate(
      Ambiance ambiance, StepBaseParameters stepParameters, StepResponse stepResponse) {
    return stepResponse;
  }
}
