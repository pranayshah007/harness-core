/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.invokers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.events.SpawnChildrenRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.ExecuteStrategy;
import io.harness.pms.sdk.core.execution.InvokerPackage;
import io.harness.pms.sdk.core.execution.ResumePackage;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.pms.sdk.core.registries.StepRegistry;
import io.harness.pms.sdk.core.steps.executables.ChildrenExecutable;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseMapper;

import com.google.inject.Inject;

@SuppressWarnings({"rawtypes", "unchecked"})
@OwnedBy(PIPELINE)
public class ChildrenStrategy implements ExecuteStrategy {
  @Inject private SdkNodeExecutionService sdkNodeExecutionService;
  @Inject private StepRegistry stepRegistry;

  @Override
  public void start(InvokerPackage invokerPackage) {
    Ambiance ambiance = invokerPackage.getAmbiance();
    ChildrenExecutable childrenExecutable = extractStep(ambiance);
    ChildrenExecutableResponse response = childrenExecutable.obtainChildren(
        ambiance, invokerPackage.getStepParameters(), invokerPackage.getInputPackage());
    handleResponse(ambiance, response);
  }

  @Override
  public void resume(ResumePackage resumePackage) {
    Ambiance ambiance = resumePackage.getAmbiance();
    ChildrenExecutable childrenExecutable = extractStep(ambiance);
    StepResponse stepResponse = childrenExecutable.handleChildrenResponse(
        ambiance, resumePackage.getStepParameters(), resumePackage.getResponseDataMap());
    sdkNodeExecutionService.handleStepResponse(ambiance, StepResponseMapper.toStepResponseProto(stepResponse));
  }

  @Override
  public ChildrenExecutable extractStep(Ambiance ambiance) {
    return (ChildrenExecutable) stepRegistry.obtain(AmbianceUtils.getCurrentStepType(ambiance));
  }

  private void handleResponse(Ambiance ambiance, ChildrenExecutableResponse response) {
    SpawnChildrenRequest spawnChildrenRequest = SpawnChildrenRequest.newBuilder().setChildren(response).build();
    sdkNodeExecutionService.spawnChildren(ambiance, spawnChildrenRequest);
  }
}
