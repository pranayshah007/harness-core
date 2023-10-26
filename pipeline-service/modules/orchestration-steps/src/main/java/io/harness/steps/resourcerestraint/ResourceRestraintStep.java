/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.AsyncExecutable;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.resourcerestraint.beans.HoldingScope;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintOutcome;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintResponseData;
import io.harness.steps.resourcerestraint.service.ResourceRestraintInstanceService;
import io.harness.steps.resourcerestraint.service.ResourceRestraintService;
import io.harness.steps.resourcerestraint.utils.ResourceRestraintUtils;
import io.harness.tasks.ResponseData;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
public class ResourceRestraintStep
    implements SyncExecutable<StepElementParameters>, AsyncExecutable<StepElementParameters> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.RESOURCE_CONSTRAINT_STEP_TYPE;

  @Inject private ResourceRestraintInstanceService resourceRestraintInstanceService;
  @Inject private ResourceRestraintService resourceRestraintService;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, StepElementParameters stepElementParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    IResourceRestraintSpecParameters specParameters =
        (IResourceRestraintSpecParameters) stepElementParameters.getSpec();
    ResourceRestraintPassThroughData data = (ResourceRestraintPassThroughData) passThroughData;
    NGLogCallback logCallback = getLogCallback(ambiance, true);
    logCallback.saveExecutionLog(
        "No executions are running with given resource key.", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    return StepResponse.builder()
        .stepOutcome(
            StepResponse.StepOutcome.builder()
                .name(YAMLFieldNameConstants.OUTPUT)
                .outcome(ResourceRestraintOutcome.builder()
                             .name(specParameters.getName())
                             .capacity(data.getCapacity())
                             .resourceUnit(data.getResourceUnit())
                             .usage(specParameters.getPermits())
                             .alreadyAcquiredPermits(getAlreadyAcquiredPermits(
                                 specParameters.getHoldingScope(), data.getReleaseEntityId(), data.getResourceUnit()))
                             .build())
                .build())
        .status(Status.SUCCEEDED)
        .build();
  }

  @Override
  public AsyncExecutableResponse executeAsync(Ambiance ambiance, StepElementParameters stepElementParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    ResourceRestraintPassThroughData restraintPassThroughData = (ResourceRestraintPassThroughData) passThroughData;
    NGLogCallback logCallback = getLogCallback(ambiance, true);
    logCallback.saveExecutionLog(
        "Current execution is queued as another execution is running with given resource key.", LogLevel.INFO);
    return AsyncExecutableResponse.newBuilder()
        .addCallbackIds(restraintPassThroughData.getConsumerId())
        .addAllLogKeys(getLogKeys(ambiance))
        .build();
  }

  @Override
  public List<String> getLogKeys(Ambiance ambiance) {
    return StepUtils.generateLogKeys(ambiance, new ArrayList<>());
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepElementParameters, Map<String, ResponseData> responseDataMap) {
    IResourceRestraintSpecParameters specParameters =
        (IResourceRestraintSpecParameters) stepElementParameters.getSpec();
    ResourceRestraintResponseData responseData =
        (ResourceRestraintResponseData) responseDataMap.values().iterator().next();
    final ResourceRestraint resourceRestraint = resourceRestraintService.get(responseData.getResourceRestraintId());
    NGLogCallback logCallback = getLogCallback(ambiance, false);
    logCallback.saveExecutionLog("Resuming current execution...", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    return StepResponse.builder()
        .stepOutcome(
            StepResponse.StepOutcome.builder()
                .name(YAMLFieldNameConstants.OUTPUT)
                .outcome(ResourceRestraintOutcome.builder()
                             .name(resourceRestraint.getName())
                             .capacity(resourceRestraint.getCapacity())
                             .resourceUnit(specParameters.getResourceUnit().getValue())
                             .usage(specParameters.getPermits())
                             .alreadyAcquiredPermits(getAlreadyAcquiredPermits(specParameters.getHoldingScope(),
                                 ResourceRestraintUtils.getReleaseEntityId(ambiance, specParameters.getHoldingScope()),
                                 specParameters.getResourceUnit().getValue()))
                             .build())
                .build())
        .status(Status.SUCCEEDED)
        .build();
  }

  @Override
  public void handleAbort(Ambiance ambiance, StepElementParameters stepElementParameters,
      AsyncExecutableResponse executableResponse, boolean userMarked) {
    IResourceRestraintSpecParameters specParameters =
        (IResourceRestraintSpecParameters) stepElementParameters.getSpec();
    NGLogCallback logCallback = getLogCallback(ambiance, false);
    logCallback.saveExecutionLog("Resource Restraint Step was aborted.", LogLevel.INFO, CommandExecutionStatus.FAILURE);
    resourceRestraintInstanceService.finishInstance(
        Preconditions.checkNotNull(executableResponse.getCallbackIdsList().get(0),
            "CallbackId should not be null in handleAbort() for nodeExecution with id %s",
            AmbianceUtils.obtainCurrentRuntimeId(ambiance)),
        specParameters.getResourceUnit().getValue());
  }

  private int getAlreadyAcquiredPermits(HoldingScope holdingScope, String releaseEntityId, String resourceUnit) {
    return resourceRestraintInstanceService.getAllCurrentlyAcquiredPermits(holdingScope, releaseEntityId, resourceUnit);
  }

  public NGLogCallback getLogCallback(Ambiance ambiance, boolean shouldOpenStream) {
    return new NGLogCallback(logStreamingStepClientFactory, ambiance, null, shouldOpenStream);
  }
}
