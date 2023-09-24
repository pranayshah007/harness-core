/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.githubrestraint.services;

import static io.harness.distribution.constraint.Consumer.State.ACTIVE;

import io.harness.distribution.constraint.Constraint;
import io.harness.distribution.constraint.ConstraintId;
import io.harness.distribution.constraint.ConstraintRegistry;
import io.harness.distribution.constraint.ConstraintUnit;
import io.harness.distribution.constraint.Consumer;
import io.harness.distribution.constraint.ConsumerId;
import io.harness.distribution.constraint.UnableToLoadConstraintException;
import io.harness.distribution.constraint.UnableToSaveConstraintException;
import io.harness.gitopsprovider.entity.GithubRestraintInstance;
import io.harness.gitopsprovider.entity.GithubRestraintInstance.GithubRestraintInstanceBuilder;
import io.harness.gitopsprovider.entity.GithubRestraintInstance.GithubRestraintInstanceKeys;
import io.harness.gitopsprovider.entity.GithubRestraintInstanceResponseData;
import io.harness.tasks.ResponseData;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;

@Slf4j
public class GithubRestraintRegistry implements ConstraintRegistry {
  @Inject GithubRestraintInstanceService githubRestraintInstanceService;
  @Inject private WaitNotifyEngine waitNotifyEngine;

  @Override
  public void save(ConstraintId id, Constraint.Spec spec) throws UnableToSaveConstraintException {}

  @Override
  public Constraint load(ConstraintId id) throws UnableToLoadConstraintException {
    return null;
  }

  @Override
  public List<Consumer> loadConsumers(ConstraintId id, ConstraintUnit unit, boolean hitSecondaryNode) {
    List<Consumer> consumers = new ArrayList<>();

    List<GithubRestraintInstance> instances =
        githubRestraintInstanceService.getAllActiveAndBlockedByResourceUnit(unit.getValue());

    instances.forEach(instance
        -> consumers.add(
            Consumer.builder()
                .id(new ConsumerId(instance.getUuid()))
                .state(instance.getState())
                .permits(instance.getPermits())
                .context(ImmutableMap.of(GithubRestraintInstanceKeys.releaseEntityId, instance.getReleaseEntityId()))
                .build()));
    return consumers;
  }

  @Override
  public boolean registerConsumer(ConstraintId id, ConstraintUnit unit, Consumer consumer, int currentlyRunning) {
    final GithubRestraintInstanceBuilder builder =
        GithubRestraintInstance.builder()
            .uuid(consumer.getId().getValue())
            .resourceUnit(unit.getValue())
            .releaseEntityId((String) consumer.getContext().get(GithubRestraintInstanceKeys.releaseEntityId))
            .permits(consumer.getPermits())
            .state(consumer.getState())
            .order((int) consumer.getContext().get(GithubRestraintInstanceKeys.order));

    if (ACTIVE == consumer.getState()) {
      builder.acquireAt(System.currentTimeMillis());
    }

    try {
      githubRestraintInstanceService.save(builder.build());
    } catch (DuplicateKeyException e) {
      log.error("Failed to add GithubRestraintInstance", e);
      return false;
    }

    return true;
  }

  @Override
  public boolean adjustRegisterConsumerContext(ConstraintId id, Map<String, Object> context) {
    final int order = githubRestraintInstanceService.getMaxOrder(id.getValue()) + 1;
    if (order == (int) context.get(GithubRestraintInstanceKeys.order)) {
      return false;
    }
    context.put(GithubRestraintInstanceKeys.order, order);
    return true;
  }

  @Override
  public boolean consumerUnblocked(
      ConstraintId id, ConstraintUnit unit, ConsumerId consumerId, Map<String, Object> context) {
    githubRestraintInstanceService.activateBlockedInstance(consumerId.getValue(), unit.getValue());
    ResponseData responseData = GithubRestraintInstanceResponseData.builder().resourceUnit(unit.getValue()).build();
    waitNotifyEngine.doneWith(consumerId.getValue(), responseData);
    return true;
  }

  @Override
  public boolean consumerFinished(
      ConstraintId id, ConstraintUnit unit, ConsumerId consumerId, Map<String, Object> context) {
    return false;
  }

  @Override
  public boolean overlappingScope(Consumer consumer, Consumer blockedConsumer) {
    return false;
  }
}
