/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.invokers;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.execution.Status.SUSPENDED;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildChainExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.events.SpawnChildRequest;
import io.harness.pms.contracts.execution.events.SuspendChainRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.ChainDetails;
import io.harness.pms.sdk.core.execution.ExecuteStrategy;
import io.harness.pms.sdk.core.execution.InterruptPackage;
import io.harness.pms.sdk.core.execution.InvokerPackage;
import io.harness.pms.sdk.core.execution.ResumePackage;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.pms.sdk.core.registries.StepRegistry;
import io.harness.pms.sdk.core.steps.executables.ChildChainExecutable;
import io.harness.pms.sdk.core.steps.io.ResponseDataMapper;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponseMapper;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.tasks.ResponseData;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@SuppressWarnings({"rawtypes", "unchecked"})
@OwnedBy(PIPELINE)
public class ChildChainStrategy implements ExecuteStrategy {
  @Inject private SdkNodeExecutionService sdkNodeExecutionService;
  @Inject private StepRegistry stepRegistry;
  @Inject private ResponseDataMapper responseDataMapper;

  @Override
  public void start(InvokerPackage invokerPackage) {
    Ambiance ambiance = invokerPackage.getAmbiance();
    ChildChainExecutable childChainExecutable = extractStep(ambiance);
    ChildChainExecutableResponse childChainResponse = childChainExecutable.executeFirstChild(
        ambiance, invokerPackage.getStepParameters(), invokerPackage.getInputPackage());
    handleResponse(ambiance, childChainResponse);
  }

  @Override
  public void resume(ResumePackage resumePackage) {
    Ambiance ambiance = resumePackage.getAmbiance();
    ChildChainExecutable childChainExecutable = extractStep(ambiance);
    ChainDetails chainDetails = resumePackage.getChainDetails();
    Map<String, ResponseData> accumulatedResponse = resumePackage.getResponseDataMap();
    if (chainDetails.isShouldEnd()) {
      StepResponse stepResponse = childChainExecutable.finalizeExecution(
          ambiance, resumePackage.getStepParameters(), chainDetails.getPassThroughBytes(), accumulatedResponse);
      sdkNodeExecutionService.handleStepResponse(ambiance, StepResponseMapper.toStepResponseProto(stepResponse));
    } else {
      ChildChainExecutableResponse chainResponse =
          childChainExecutable.executeNextChild(ambiance, resumePackage.getStepParameters(),
              resumePackage.getStepInputPackage(), chainDetails.getPassThroughBytes(), accumulatedResponse);
      handleResponse(ambiance, chainResponse);
    }
  }

  @Override
  public void abort(InterruptPackage interruptPackage) {
    throw new UnsupportedOperationException("Abort not supported for Child Chain");
  }

  @Override
  public void expire(InterruptPackage interruptPackage) {
    throw new UnsupportedOperationException("Expire not supported for Child Chain");
  }

  @Override
  public void failure(InterruptPackage interruptPackage) {
    throw new UnsupportedOperationException("Expire not supported for Child Chain");
  }

  @Override
  public ChildChainExecutable extractStep(Ambiance ambiance) {
    return (ChildChainExecutable) stepRegistry.obtain(AmbianceUtils.getCurrentStepType(ambiance));
  }

  private void handleResponse(Ambiance ambiance, ChildChainExecutableResponse childChainResponse) {
    if (childChainResponse.getSuspend()) {
      suspendChain(ambiance, childChainResponse);
    } else {
      executeChild(ambiance, childChainResponse);
    }
  }

  private void executeChild(Ambiance ambiance, ChildChainExecutableResponse childChainResponse) {
    SpawnChildRequest spawnChildRequest = SpawnChildRequest.newBuilder().setChildChain(childChainResponse).build();
    sdkNodeExecutionService.spawnChild(ambiance, spawnChildRequest);
  }

  private void suspendChain(Ambiance ambiance, ChildChainExecutableResponse childChainResponse) {
    Level currentLevel = Preconditions.checkNotNull(AmbianceUtils.obtainCurrentLevel(ambiance));
    Map<String, ByteString> responseBytes =
        responseDataMapper.toResponseDataProto(Collections.singletonMap("ignore-" + currentLevel.getRuntimeId(),
            StepResponseNotifyData.builder()
                .nodeUuid(currentLevel.getSetupId())
                .nodeExecutionId(currentLevel.getRuntimeId())
                .identifier(currentLevel.getIdentifier())
                .status(SUSPENDED)
                .description("Ignoring Execution as next child found to be null")
                .build()));
    sdkNodeExecutionService.suspendChainExecution(ambiance,
        SuspendChainRequest.newBuilder()
            .setExecutableResponse(ExecutableResponse.newBuilder().setChildChain(childChainResponse).build())
            .setIsError(false)
            .putAllResponse(responseBytes)
            .build());
  }
}
