/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;

import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.serverless.beans.ServerlessExecutionPassThroughData;
import io.harness.cdng.serverless.beans.ServerlessStepExecutorParams;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;

public interface ServerlessStepExecutor {
  TaskChainResponse executeServerlessTask(ManifestOutcome serverlessManifestOutcome, Ambiance ambiance,
      StepElementParameters stepParameters, ServerlessExecutionPassThroughData executionPassThroughData,
      UnitProgressData unitProgressData, ServerlessStepExecutorParams serverlessStepExecutorParams);

  TaskChainResponse executeServerlessPrepareRollbackTask(ManifestOutcome serverlessManifestOutcome, Ambiance ambiance,
      StepElementParameters stepParameters, ServerlessStepPassThroughData serverlessStepPassThroughData,
      UnitProgressData unitProgressData, ServerlessStepExecutorParams serverlessStepExecutorParams);
}
