/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.distribution.constraint;

import static io.harness.distribution.constraint.Consumer.State.ACTIVE;
import static io.harness.distribution.constraint.Consumer.State.BLOCKED;
import static io.harness.distribution.constraint.Consumer.State.FINISHED;

import static java.util.Collections.synchronizedMap;
import static org.apache.commons.lang3.StringUtils.defaultString;

import io.harness.distribution.constraint.Constraint.Spec;
import io.harness.distribution.constraint.Consumer.State;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import javax.validation.constraints.NotNull;

public class InprocConstraintRegistry implements ConstraintRegistry {
  private Map<ConstraintId, Spec> map = synchronizedMap(new HashMap<>());
  private Map<String, List<Consumer>> consumers = synchronizedMap(new HashMap<>());

  private String computeKey(@NotNull ConstraintId id, @NotNull ConstraintUnit unit) {
    return defaultString(id.getValue(), "null") + "/" + defaultString(unit.getValue(), "null");
  }

  @Override
  public void save(ConstraintId id, Spec spec) throws UnableToSaveConstraintException {
    if (map.putIfAbsent(id, spec) != null) {
      throw new UnableToSaveConstraintException("The constraint with this id already exists");
    }
  }

  @Override
  public Constraint load(ConstraintId id) throws UnableToLoadConstraintException {
    final Spec spec = map.get(id);
    if (spec == null) {
      return null;
    }
    return Constraint.builder().id(id).spec(spec).build();
  }

  @Override
  public List<Consumer> loadConsumers(ConstraintId id, ConstraintUnit unit, boolean hitSecondaryNode) {
    return this.consumers.computeIfAbsent(computeKey(id, unit), key -> new ArrayList<>());
  }

  @Override
  public boolean registerConsumer(ConstraintId id, ConstraintUnit unit, Consumer consumer, int currentlyRunning)
      throws UnableToRegisterConsumerException {
    synchronized (consumers) {
      List<Consumer> constraintConsumers = consumers.computeIfAbsent(computeKey(id, unit), key -> new ArrayList<>());
      if (Constraint.getUsedPermits(constraintConsumers) != currentlyRunning) {
        return false;
      }

      constraintConsumers.add(consumer);
    }
    return true;
  }

  @Override
  public boolean adjustRegisterConsumerContext(ConstraintId id, Map<String, Object> context) {
    return false;
  }

  @Override
  public boolean consumerFinished(
      ConstraintId id, ConstraintUnit unit, ConsumerId consumerId, Map<String, Object> context) {
    return consumerStateChange(id, unit, consumerId, FINISHED, ACTIVE);
  }

  @Override
  public boolean overlappingScope(Consumer consumer, Consumer blockedConsumer) {
    return false;
  }

  @Override
  public boolean consumerUnblocked(
      ConstraintId id, ConstraintUnit unit, ConsumerId consumerId, Map<String, Object> context) {
    return consumerStateChange(id, unit, consumerId, ACTIVE, BLOCKED);
  }

  private boolean consumerStateChange(
      ConstraintId id, ConstraintUnit unit, ConsumerId consumerId, State newState, State expected) {
    synchronized (consumers) {
      final List<Consumer> constraintConsumers = this.consumers.get(computeKey(id, unit));

      final ListIterator<Consumer> iterator = constraintConsumers.listIterator();
      while (iterator.hasNext()) {
        Consumer consumer = iterator.next();
        if (consumer.getId().equals(consumerId)) {
          if (consumer.getState() != expected) {
            return false;
          }
          iterator.set(Consumer.builder().state(newState).id(consumer.getId()).permits(consumer.getPermits()).build());
          return true;
        }
      }
    }

    return false;
  }
}
