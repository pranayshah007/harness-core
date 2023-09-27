/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.distribution.constraint.Consumer.State.ACTIVE;
import static io.harness.distribution.constraint.Consumer.State.REJECTED;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.distribution.constraint.Constraint;
import io.harness.distribution.constraint.ConstraintUnit;
import io.harness.distribution.constraint.Consumer;
import io.harness.distribution.constraint.ConsumerId;
import io.harness.distribution.constraint.InvalidPermitsException;
import io.harness.distribution.constraint.PermanentlyBlockedConsumerException;
import io.harness.distribution.constraint.UnableToRegisterConsumerException;
import io.harness.exception.GeneralException;
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
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.steps.resourcerestraint.beans.AcquireMode;
import io.harness.steps.resourcerestraint.beans.HoldingScope;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint.ResourceRestraintKeys;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance.ResourceRestraintInstanceKeys;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintOutcome;
import io.harness.steps.resourcerestraint.service.ResourceRestraintInstanceService;
import io.harness.steps.resourcerestraint.service.ResourceRestraintService;
import io.harness.steps.resourcerestraint.utils.ResourceRestraintUtils;
import io.harness.tasks.ResponseData;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = HarnessModuleComponent.CDS_PIPELINE)
public class ResourceRestraintStep implements AsyncExecutable<StepElementParameters> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.RESOURCE_CONSTRAINT_STEP_TYPE;

  @Inject private ResourceRestraintInstanceService resourceRestraintInstanceService;
  @Inject private ResourceRestraintService resourceRestraintService;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @SneakyThrows
  @Override
  public AsyncExecutableResponse executeAsync(Ambiance ambiance, StepElementParameters stepElementParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    IResourceRestraintSpecParameters specParameters =
        (IResourceRestraintSpecParameters) stepElementParameters.getSpec();

    ResourceRestraint resourceRestraint = Preconditions.checkNotNull(
        resourceRestraintService.getByNameAndAccountId(specParameters.getName(), AmbianceUtils.getAccountId(ambiance)));

    Constraint constraint = resourceRestraintInstanceService.createAbstraction(resourceRestraint);

    ConstraintUnit renderedResourceUnit = new ConstraintUnit(specParameters.getResourceUnit().getValue());
    HoldingScope holdingScope = specParameters.getHoldingScope();
    String releaseEntityId = ResourceRestraintUtils.getReleaseEntityId(ambiance, holdingScope);

    int permits = specParameters.getPermits();
    if (AcquireMode.ENSURE == specParameters.getAcquireMode()) {
      permits -= resourceRestraintInstanceService.getAllCurrentlyAcquiredPermits(
          holdingScope, releaseEntityId, renderedResourceUnit.getValue());
    }

    Map<String, Object> constraintContext =
        populateConstraintContext(resourceRestraint, specParameters, releaseEntityId);
    String consumerId = generateUuid();
    try {
      // This is invalid configuration just keeping it for now as validation might not be present on the spec
      if (permits <= 0) {
        return AsyncExecutableResponse.newBuilder().addAllLogKeys(getLogKeys(ambiance)).build();
      }

      Consumer.State state = constraint.registerConsumer(renderedResourceUnit, new ConsumerId(consumerId), permits,
          constraintContext, resourceRestraintInstanceService.getRegistry());

      if (state == ACTIVE) {
        // This is again sync mode
        return AsyncExecutableResponse.newBuilder().addAllLogKeys(getLogKeys(ambiance)).build();
      } else if (REJECTED == state) {
        throw new GeneralException("Found already running resourceConstrains, marking this execution as failed");
      }
    } catch (InvalidPermitsException | UnableToRegisterConsumerException | PermanentlyBlockedConsumerException e) {
      log.error("Exception on ResourceRestraintStep for id [{}]", AmbianceUtils.obtainCurrentRuntimeId(ambiance), e);
      throw e;
    }

    NGLogCallback logCallback = getLogCallback(ambiance, true);
    logCallback.saveExecutionLog(
        "Current execution is queued as another execution is running with given resource key.", LogLevel.INFO);
    return AsyncExecutableResponse.newBuilder().addCallbackIds(consumerId).addAllLogKeys(getLogKeys(ambiance)).build();
  }

  public List<String> getLogKeys(Ambiance ambiance) {
    return StepUtils.generateLogKeys(ambiance, new ArrayList<>());
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepElementParameters, Map<String, ResponseData> responseDataMap) {
    IResourceRestraintSpecParameters specParameters =
        (IResourceRestraintSpecParameters) stepElementParameters.getSpec();

    ResourceRestraint resourceRestraint = Preconditions.checkNotNull(
        resourceRestraintService.getByNameAndAccountId(specParameters.getName(), AmbianceUtils.getAccountId(ambiance)));

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
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepElementParameters, AsyncExecutableResponse executableResponse) {
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

  private Map<String, Object> populateConstraintContext(
      ResourceRestraint resourceRestraint, IResourceRestraintSpecParameters stepParameters, String releaseEntityId) {
    Map<String, Object> constraintContext = new HashMap<>();
    constraintContext.put(ResourceRestraintInstanceKeys.releaseEntityType, stepParameters.getHoldingScope().name());
    constraintContext.put(ResourceRestraintInstanceKeys.releaseEntityId, releaseEntityId);
    constraintContext.put(ResourceRestraintInstanceKeys.order,
        resourceRestraintInstanceService.getMaxOrder(resourceRestraint.getUuid()) + 1);
    constraintContext.put(ResourceRestraintKeys.capacity, resourceRestraint.getCapacity());
    constraintContext.put(ResourceRestraintKeys.name, resourceRestraint.getName());
    constraintContext.put(FeatureName.RESOURCE_CONSTRAINT_MAX_QUEUE.name(), true);

    return constraintContext;
  }
}
