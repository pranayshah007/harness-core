/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.distribution.constraint.Consumer.State.ACTIVE;
import static io.harness.distribution.constraint.Consumer.State.REJECTED;
import static io.harness.pms.sdk.core.execution.events.node.facilitate.FacilitatorResponse.FacilitatorResponseBuilder;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.distribution.constraint.Constraint;
import io.harness.distribution.constraint.ConstraintUnit;
import io.harness.distribution.constraint.Consumer;
import io.harness.distribution.constraint.ConsumerId;
import io.harness.distribution.constraint.InvalidPermitsException;
import io.harness.distribution.constraint.PermanentlyBlockedConsumerException;
import io.harness.distribution.constraint.UnableToRegisterConsumerException;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.eraro.ErrorCode;
import io.harness.exception.GeneralException;
import io.harness.exception.PersistentLockException;
import io.harness.exception.WingsException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.events.node.facilitate.Facilitator;
import io.harness.pms.sdk.core.execution.events.node.facilitate.FacilitatorResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.resourcerestraint.beans.AcquireMode;
import io.harness.steps.resourcerestraint.beans.HoldingScope;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint.ResourceRestraintKeys;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance.ResourceRestraintInstanceKeys;
import io.harness.steps.resourcerestraint.service.ResourceRestraintInstanceService;
import io.harness.steps.resourcerestraint.service.ResourceRestraintRegistry;
import io.harness.steps.resourcerestraint.service.ResourceRestraintService;
import io.harness.steps.resourcerestraint.utils.ResourceRestraintUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class ResourceRestraintFacilitator implements Facilitator {
  private static final String LOCK_PREFIX = "RR_LOCK_";

  public static final FacilitatorType FACILITATOR_TYPE =
      FacilitatorType.newBuilder().setType(StepSpecTypeConstants.RESOURCE_RESTRAINT_FACILITATOR_TYPE).build();

  @Inject private ResourceRestraintInstanceService resourceRestraintInstanceService;
  @Inject private ResourceRestraintService resourceRestraintService;
  @Inject private ResourceRestraintRegistry resourceRestraintRegistry;
  @Inject private PmsEngineExpressionService pmsEngineExpressionService;
  @Inject private PersistentLocker persistentLocker;

  @Override
  public FacilitatorResponse facilitate(
      Ambiance ambiance, StepParameters stepParameters, byte[] parameters, StepInputPackage inputPackage) {
    StepElementParameters stepElementParameters = (StepElementParameters) stepParameters;

    IResourceRestraintSpecParameters specParameters =
        (IResourceRestraintSpecParameters) stepElementParameters.getSpec();

    final ResourceRestraint resourceRestraint = Preconditions.checkNotNull(
        resourceRestraintService.getByNameAndAccountId(specParameters.getName(), AmbianceUtils.getAccountId(ambiance)));

    ConstraintUnit renderedResourceUnit = new ConstraintUnit(
        pmsEngineExpressionService.renderExpression(ambiance, specParameters.getResourceUnit().getValue()));

    final Constraint constraint = resourceRestraintInstanceService.createAbstraction(resourceRestraint);

    HoldingScope holdingScope = specParameters.getHoldingScope();
    String releaseEntityId = ResourceRestraintUtils.getReleaseEntityId(ambiance, holdingScope);

    try (AcquiredLock<?> lock = persistentLocker.waitToAcquireLockOptional(
             LOCK_PREFIX + resourceRestraint.getUuid(), Duration.ofSeconds(10), Duration.ofMinutes(1))) {
      if (lock == null) {
        throw new PersistentLockException("Cannot Acquire Lock for Resource Constraint",
            ErrorCode.FAILED_TO_ACQUIRE_PERSISTENT_LOCK, WingsException.USER);
      }

      int permits = specParameters.getPermits();
      if (AcquireMode.ENSURE == specParameters.getAcquireMode()) {
        permits -= resourceRestraintInstanceService.getAllCurrentlyAcquiredPermits(
            holdingScope, releaseEntityId, renderedResourceUnit.getValue());
      }

      FacilitatorResponseBuilder responseBuilder = FacilitatorResponse.builder();
      if (permits <= 0) {
        return responseBuilder.executionMode(ExecutionMode.SYNC)
            .passThroughData(buildPassThroughData(
                specParameters, resourceRestraint, null, releaseEntityId, renderedResourceUnit.getValue()))
            .build();
      }

      Map<String, Object> constraintContext =
          populateConstraintContext(resourceRestraint, specParameters, releaseEntityId);

      String consumerId = generateUuid();
      try {
        Consumer.State state = constraint.registerConsumer(
            renderedResourceUnit, new ConsumerId(consumerId), permits, constraintContext, resourceRestraintRegistry);

        if (ACTIVE == state) {
          return responseBuilder.executionMode(ExecutionMode.SYNC)
              .passThroughData(buildPassThroughData(
                  specParameters, resourceRestraint, consumerId, releaseEntityId, renderedResourceUnit.getValue()))
              .build();
        } else if (REJECTED == state) {
          throw new GeneralException("Found already running resourceConstrains, marking this execution as failed");
        }
      } catch (InvalidPermitsException | UnableToRegisterConsumerException | PermanentlyBlockedConsumerException e) {
        log.error("Exception on ResourceRestraintStep for id [{}]", AmbianceUtils.obtainCurrentRuntimeId(ambiance), e);
      }

      return responseBuilder.executionMode(ExecutionMode.CONSTRAINT)
          .passThroughData(buildPassThroughData(
              specParameters, resourceRestraint, consumerId, releaseEntityId, renderedResourceUnit.getValue()))
          .build();
    }
  }

  private ResourceRestraintPassThroughData buildPassThroughData(IResourceRestraintSpecParameters specParameters,
      ResourceRestraint resourceRestraint, String consumerId, String releaseEntityId, String unit) {
    return ResourceRestraintPassThroughData.builder()
        .consumerId(consumerId)
        .name(specParameters.getName())
        .resourceRestraintId(resourceRestraint.getUuid())
        .resourceUnit(unit)
        .capacity(resourceRestraint.getCapacity())
        .releaseEntityType(specParameters.getHoldingScope().name())
        .releaseEntityId(releaseEntityId)
        .build();
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
