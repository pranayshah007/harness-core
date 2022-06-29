/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.AsyncExecutable;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.StepSpecTypeConstants;
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
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
public class ResourceRestraintStep
    implements SyncExecutable<StepElementParameters>, AsyncExecutable<StepElementParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(StepSpecTypeConstants.RESOURCE_CONSTRAINT)
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private ResourceRestraintInstanceService resourceRestraintInstanceService;
  @Inject private ResourceRestraintService resourceRestraintService;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, StepElementParameters stepElementParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    ResourceRestraintSpecParameters specParameters = (ResourceRestraintSpecParameters) stepElementParameters.getSpec();
    ResourceRestraintPassThroughData data = (ResourceRestraintPassThroughData) passThroughData;

    return StepResponse.builder()
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(YAMLFieldNameConstants.OUTPUT)
                         .outcome(ResourceRestraintOutcome.builder()
                                      .name(specParameters.getName())
                                      .capacity(data.getCapacity())
                                      .resourceUnit(data.getResourceUnit())
                                      .usage(specParameters.getPermits())
                                      .alreadyAcquiredPermits(getAlreadyAcquiredPermits(
                                          specParameters.getHoldingScope(), data.getReleaseEntityId()))
                                      .build())
                         .build())
        .status(Status.SUCCEEDED)
        .build();
  }

  @Override
  public AsyncExecutableResponse executeAsync(Ambiance ambiance, StepElementParameters stepElementParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    ResourceRestraintPassThroughData restraintPassThroughData = (ResourceRestraintPassThroughData) passThroughData;

    return AsyncExecutableResponse.newBuilder().addCallbackIds(restraintPassThroughData.getConsumerId()).build();
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepElementParameters, Map<String, ResponseData> responseDataMap) {
    ResourceRestraintSpecParameters specParameters = (ResourceRestraintSpecParameters) stepElementParameters.getSpec();
    ResourceRestraintResponseData responseData =
        (ResourceRestraintResponseData) responseDataMap.values().iterator().next();
    final ResourceRestraint resourceRestraint = resourceRestraintService.get(responseData.getResourceRestraintId());
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
                                 ResourceRestraintUtils.getReleaseEntityId(ambiance, specParameters.getHoldingScope())))
                             .build())
                .build())
        .status(Status.SUCCEEDED)
        .build();
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepElementParameters, AsyncExecutableResponse executableResponse) {
    ResourceRestraintSpecParameters specParameters = (ResourceRestraintSpecParameters) stepElementParameters.getSpec();

    resourceRestraintInstanceService.finishInstance(
        Preconditions.checkNotNull(executableResponse.getCallbackIdsList().get(0),
            "CallbackId should not be null in handleAbort() for nodeExecution with id %s",
            AmbianceUtils.obtainCurrentRuntimeId(ambiance)),
        specParameters.getResourceUnit().getValue());
  }

  private int getAlreadyAcquiredPermits(HoldingScope holdingScope, String releaseEntityId) {
    return resourceRestraintInstanceService.getAllCurrentlyAcquiredPermits(holdingScope, releaseEntityId);
  }
}
