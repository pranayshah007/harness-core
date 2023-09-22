/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.githubrestraint.services;

import static io.harness.distribution.constraint.Consumer.State.ACTIVE;
import static io.harness.distribution.constraint.Consumer.State.BLOCKED;

import io.harness.distribution.constraint.Constraint;
import io.harness.distribution.constraint.ConstraintId;
import io.harness.distribution.constraint.ConstraintRegistry;
import io.harness.distribution.constraint.ConstraintUnit;
import io.harness.distribution.constraint.Consumer;
import io.harness.distribution.constraint.ConsumerId;
import io.harness.distribution.constraint.UnableToLoadConstraintException;
import io.harness.distribution.constraint.UnableToRegisterConsumerException;
import io.harness.distribution.constraint.UnableToSaveConstraintException;
import io.harness.gitopsprovider.entity.GithubRestraintInstance;
import io.harness.gitopsprovider.entity.GithubRestraintInstance.GithubRestraintInstanceKeys;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class GithubRestraintRegistry implements ConstraintRegistry {
  @Inject GithubRestraintInstanceService githubRestraintInstanceService;
  @Override
  public void save(ConstraintId id, Constraint.Spec spec) throws UnableToSaveConstraintException {}

  @Override
  public Constraint load(ConstraintId id) throws UnableToLoadConstraintException {
    return null;
  }

  @Override
  public List<Consumer> loadConsumers(ConstraintId id, ConstraintUnit unit, boolean hitSecondaryNode) {
    List<Consumer> consumers = new ArrayList<>();

    List<GithubRestraintInstance> instances = githubRestraintInstanceService.getAllByRestraintIdAndStates(
        id.getValue(), new ArrayList<>(Arrays.asList(ACTIVE, BLOCKED)));

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
  public boolean registerConsumer(ConstraintId id, ConstraintUnit unit, Consumer consumer, int currentlyRunning)
      throws UnableToRegisterConsumerException {
    return false;
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
    return false;
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
