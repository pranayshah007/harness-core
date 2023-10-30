/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.distribution.constraint;

import io.harness.distribution.constraint.Constraint.Spec;

import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;

public interface ConstraintRegistry {
  void save(ConstraintId id, Spec spec) throws UnableToSaveConstraintException;
  Constraint load(ConstraintId id) throws UnableToLoadConstraintException;
  List<Consumer> loadConsumers(@NotNull ConstraintId id, @NotNull ConstraintUnit unit, boolean hitSecondaryNode);

  // When a new consumer is registered it goes into either blocked or running state.
  boolean registerConsumer(ConstraintId id, ConstraintUnit unit, Consumer consumer, int currentlyRunning)
      throws UnableToRegisterConsumerException;

  boolean adjustRegisterConsumerContext(ConstraintId id, Map<String, Object> context);

  boolean consumerUnblocked(ConstraintId id, ConstraintUnit unit, ConsumerId consumerId, Map<String, Object> context);
  boolean consumerFinished(ConstraintId id, ConstraintUnit unit, ConsumerId consumerId, Map<String, Object> context);

  boolean overlappingScope(Consumer consumer, Consumer blockedConsumer);
}
