/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.executable;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.security.PmsSecurityContextEventGuard;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

@OwnedBy(HarnessTeam.PIPELINE)
public abstract class TaskChainExecutableWithCapabilities extends TaskChainExecutableWithRollbackAndRbac {
  @Override
  public StepResponse finalizeExecution(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    try (PmsSecurityContextEventGuard securityContextEventGuard = new PmsSecurityContextEventGuard(ambiance)) {
      StepResponse stepResponse =
          finalizeExecutionWithSecurityContext(ambiance, stepParameters, passThroughData, responseDataSupplier);
      return postTaskValidate(ambiance, stepParameters, stepResponse);
    }
  }

  // evaluating policies added in advanced section of the steps and updating status and failure info in the step
  // response
  public StepResponse postTaskValidate(
      Ambiance ambiance, StepElementParameters stepParameters, StepResponse stepResponse) {
    return stepResponse;
  }
}
