/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.pluginstep;

import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskExecutableWithRollback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.supplier.ThrowingSupplier;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PmsPluginStep extends TaskExecutableWithRollback<PmsPluginStepResponse> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.PLUGIN_STEP_TYPE;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  private NGLogCallback getNGLogCallback(LogStreamingStepClientFactory logStreamingStepClientFactory, Ambiance ambiance,
      String logFix, boolean openStream) {
    return new NGLogCallback(logStreamingStepClientFactory, ambiance, logFix, openStream);
  }

  @Override
  public TaskRequest obtainTask(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    // todo(abhinav): implement
    return null;
  }

  @Override
  public StepResponse handleTaskResult(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<PmsPluginStepResponse> responseSupplier) throws Exception {
    // todo(abhinav): implement
    return null;
  }
}
