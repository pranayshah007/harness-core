/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.executable;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.security.PmsSecurityContextEventGuard;

import lombok.SneakyThrows;

// Sync Executable With RBAC and postSyncValidation
@OwnedBy(HarnessTeam.PIPELINE)
public abstract class SyncExecutableWithCapabilities implements SyncExecutableWithRbac<StepBaseParameters> {
  @Override
  @SneakyThrows
  public StepResponse executeSync(Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage,
      PassThroughData passThroughData) {
    try (PmsSecurityContextEventGuard securityContextEventGuard = new PmsSecurityContextEventGuard(ambiance)) {
      validateResources(ambiance, stepParameters);
      StepResponse stepResponse = executeSyncAfterRbac(ambiance, stepParameters, inputPackage, passThroughData);
      return postSyncValidate(ambiance, stepParameters, stepResponse);
    }
  }
  public abstract StepResponse executeSyncAfterRbac(Ambiance ambiance, StepBaseParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData);

  // evaluating policies added in advanced section of the steps and updating status and failure info in the step
  // response
  public StepResponse postSyncValidate(
      Ambiance ambiance, StepBaseParameters stepParameters, StepResponse stepResponse) {
    return stepResponse;
  }
}
