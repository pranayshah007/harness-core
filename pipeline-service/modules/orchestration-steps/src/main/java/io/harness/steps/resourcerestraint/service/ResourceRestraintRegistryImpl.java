/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.distribution.constraint.Consumer.State.ACTIVE;
import static io.harness.distribution.constraint.Consumer.State.BLOCKED;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.distribution.constraint.Constraint;
import io.harness.distribution.constraint.ConstraintId;
import io.harness.distribution.constraint.ConstraintRegistry;
import io.harness.distribution.constraint.ConstraintUnit;
import io.harness.distribution.constraint.Consumer;
import io.harness.distribution.constraint.ConsumerId;
import io.harness.distribution.constraint.UnableToLoadConstraintException;
import io.harness.distribution.constraint.UnableToSaveConstraintException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.utils.PmsConstants;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance.ResourceRestraintInstanceBuilder;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance.ResourceRestraintInstanceKeys;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintResponseData;
import io.harness.tasks.ResponseData;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class ResourceRestraintRegistryImpl implements ResourceRestraintRegistry {
  @Inject private ResourceRestraintService resourceRestraintService;
  @Inject private ResourceRestraintInstanceService resourceRestraintInstanceService;
  @Inject private WaitNotifyEngine waitNotifyEngine;

  public ConstraintRegistry getRegistry() {
    return this;
  }

  @Override
  public void save(ConstraintId id, Constraint.Spec spec) throws UnableToSaveConstraintException {
    // to be implemented
  }

  @Override
  public Constraint load(ConstraintId id) throws UnableToLoadConstraintException {
    final ResourceRestraint resourceRestraint = resourceRestraintService.get(id.getValue());
    return resourceRestraintInstanceService.createAbstraction(resourceRestraint);
  }

  @Override
  public List<Consumer> loadConsumers(ConstraintId id, ConstraintUnit unit, boolean hitSecondaryNode) {
    List<Consumer> consumers = new ArrayList<>();

    List<ResourceRestraintInstance> instances =
        resourceRestraintInstanceService.getAllByRestraintIdAndResourceUnitAndStates(
            id.getValue(), unit.getValue(), new ArrayList<>(Arrays.asList(ACTIVE, BLOCKED)));

    instances.forEach(instance
        -> consumers.add(Consumer.builder()
                             .id(new ConsumerId(instance.getUuid()))
                             .state(instance.getState())
                             .permits(instance.getPermits())
                             .context(ImmutableMap.of(ResourceRestraintInstanceKeys.releaseEntityType,
                                 instance.getReleaseEntityType(), ResourceRestraintInstanceKeys.releaseEntityId,
                                 instance.getReleaseEntityId()))
                             .build()));
    return consumers;
  }

  @Override
  public boolean registerConsumer(ConstraintId id, ConstraintUnit unit, Consumer consumer, int currentlyRunning) {
    ResourceRestraint resourceRestraint = resourceRestraintService.get(id.getValue());
    if (resourceRestraint == null) {
      throw new InvalidRequestException(format("There is no resource constraint with id: %s", id.getValue()));
    }

    final ResourceRestraintInstanceBuilder builder =
        ResourceRestraintInstance.builder()
            .uuid(consumer.getId().getValue())
            .resourceRestraintId(id.getValue())
            .resourceUnit(unit.getValue())
            .releaseEntityType((String) consumer.getContext().get(ResourceRestraintInstanceKeys.releaseEntityType))
            .releaseEntityId((String) consumer.getContext().get(ResourceRestraintInstanceKeys.releaseEntityId))
            .permits(consumer.getPermits())
            .state(consumer.getState())
            .order((int) consumer.getContext().get(ResourceRestraintInstanceKeys.order));

    if (ACTIVE == consumer.getState()) {
      builder.acquireAt(System.currentTimeMillis());
    }

    try {
      resourceRestraintInstanceService.save(builder.build());
    } catch (DuplicateKeyException e) {
      log.error("Failed to add ResourceRestraintInstance", e);
      return false;
    }

    return true;
  }

  @Override
  public boolean adjustRegisterConsumerContext(ConstraintId id, Map<String, Object> context) {
    final int order = resourceRestraintInstanceService.getMaxOrder(id.getValue()) + 1;
    if (order == (int) context.get(ResourceRestraintInstanceKeys.order)) {
      return false;
    }
    context.put(ResourceRestraintInstanceKeys.order, order);
    return true;
  }

  @Override
  public boolean consumerUnblocked(
      ConstraintId id, ConstraintUnit unit, ConsumerId consumerId, Map<String, Object> context) {
    resourceRestraintInstanceService.activateBlockedInstance(consumerId.getValue(), unit.getValue());
    ResponseData responseData = ResourceRestraintResponseData.builder()
                                    .resourceRestraintId(id.getValue())
                                    .resourceUnit(unit.getValue())
                                    .build();
    waitNotifyEngine.doneWith(consumerId.getValue(), responseData);
    return true;
  }

  @Override
  public boolean consumerFinished(
      ConstraintId id, ConstraintUnit unit, ConsumerId consumerId, Map<String, Object> context) {
    try {
      resourceRestraintInstanceService.finishInstance(consumerId.getValue(), unit.getValue());
    } catch (InvalidRequestException e) {
      log.error("The attempt to finish Constraint with id {} failed for resource unit {} with Resource restraint id {}",
          id.getValue(), unit.getValue(), consumerId.getValue(), e);
      return false;
    }
    return true;
  }

  @Override
  public boolean overlappingScope(Consumer consumer, Consumer blockedConsumer) {
    String releaseScope = (String) consumer.getContext().get(ResourceRestraintInstanceKeys.releaseEntityType);
    String blockedReleaseScope =
        (String) blockedConsumer.getContext().get(ResourceRestraintInstanceKeys.releaseEntityType);

    if (!PmsConstants.RELEASE_ENTITY_TYPE_PLAN.equals(releaseScope)
        || !PmsConstants.RELEASE_ENTITY_TYPE_PLAN.equals(blockedReleaseScope)) {
      return false;
    }

    String planExecutionId = (String) consumer.getContext().get(ResourceRestraintInstanceKeys.releaseEntityId);
    String blockedPlanExecutionId =
        (String) blockedConsumer.getContext().get(ResourceRestraintInstanceKeys.releaseEntityId);

    return planExecutionId.equals(blockedPlanExecutionId);
  }
}
