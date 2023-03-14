/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.executable;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.opaclient.OpaServiceClient;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.tasks.ResponseData;
import io.harness.utils.PolicyEvalUtils;

import com.google.inject.Inject;
import java.util.Map;

// Async Executable With RBAC and postAsyncValidation
@OwnedBy(HarnessTeam.PIPELINE)
public abstract class CiAsyncExecutable implements AsyncExecutableWithRbac<StepElementParameters> {
  @Inject OpaServiceClient opaServiceClient;
  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    StepResponse stepResponse = handleAsyncResponseInternal(ambiance, stepParameters, responseDataMap);
    return postAsyncValidate(ambiance, stepParameters, stepResponse);
  }

  public abstract StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap);

  // TODO:  evaluating policies added in advanced section of the steps and updating status and failure info in the step
  // response
  public StepResponse postAsyncValidate(
      Ambiance ambiance, StepElementParameters stepParameters, StepResponse stepResponse) {
    if (Status.SUCCEEDED.equals(stepResponse.getStatus())) {
      return PolicyEvalUtils.evalPolicies(ambiance, stepParameters, stepResponse, opaServiceClient);
    }
    return stepResponse;
  }
}
