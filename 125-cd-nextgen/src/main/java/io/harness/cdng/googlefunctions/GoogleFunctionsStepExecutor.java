/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.googlefunctions;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;

@OwnedBy(CDP)
public interface GoogleFunctionsStepExecutor {
  TaskChainResponse executeTask(Ambiance ambiance, StepBaseParameters stepParameters,
      GoogleFunctionsStepPassThroughData googleFunctionsStepPassThroughData, UnitProgressData unitProgressData);
}
