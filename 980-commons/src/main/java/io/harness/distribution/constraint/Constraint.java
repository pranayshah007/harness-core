/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.distribution.constraint;
import static io.harness.distribution.constraint.Constraint.Strategy.ASAP;
import static io.harness.distribution.constraint.Constraint.Strategy.FIFO;
import static io.harness.distribution.constraint.Consumer.State.ACTIVE;
import static io.harness.distribution.constraint.Consumer.State.BLOCKED;
import static io.harness.distribution.constraint.Consumer.State.REJECTED;
import static io.harness.distribution.constraint.ConsumerContextUtils.getReleaseEntityId;
import static io.harness.distribution.constraint.ConsumerContextUtils.getReleaseEntityType;
import static io.harness.distribution.constraint.ConsumerContextUtils.hasContext;
import static io.harness.govern.Switch.unhandled;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.distribution.constraint.Consumer.State;
import io.harness.distribution.constraint.RunnableConsumers.RunnableConsumersBuilder;
import io.harness.threading.Morpheus;

import com.google.common.annotations.VisibleForTesting;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;

/*
 * Distributed constrain is designed to limit the access to arbitrary resource. It allows for configurable number of
 * permits. Every request requests particular number of permits. The constrain primitive will allow it if the expected
 * permits are available and will block it otherwise.
 *
 * Before we get in details lets put a small glossary:
 * * resource - an arbitrary system need that has some limits that can be represented in number
 * * limits - a number that represent the resource limitation
 * * consumer - a system that consumes at whole or partially the resource
 * * permits - a number that consume needs during it execution
 * * constrain - system that allows for multiple consumers to utilize a resource without permits of the active consumer
 * *             to exceed the limits of the resource
 * * constrain registry - a storage where the constrain can be persisted in a distributed way
 * * strategy - the way constrain allows for obtaining new permits
 * * as soon as possible strategy - In this strategy the constrain will allow for the next in line consumer that have
 *                                  the needed permits available to go.
 * * fifo or fair strategy - it will allow the consumers that are fist in to continue first
 *
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITOPS})
@Value
@Builder
public class Constraint {
  private static final int DELAY_FOR_OPTIMISTIC_RETRIES = 10;
  public static final int MAX_CONSUMERS_WAITING_FOR_RESOURCE = 20;

  private static final SecureRandom random = new SecureRandom();

  private ConstraintId id;

  public enum Strategy {
    ASAP,
    FIFO,
  }

  @Value
  @Builder
  public static class Spec {
    @Builder.Default private Strategy strategy = ASAP;
    int limits;
  }

  private Spec spec;

  public static Constraint create(ConstraintId id, Spec spec, ConstraintRegistry registry)
      throws UnableToSaveConstraintException {
    registry.save(id, spec);

    return builder().id(id).spec(spec).build();
  }

  public static Constraint load(ConstraintId id, ConstraintRegistry registry) throws UnableToLoadConstraintException {
    return registry.load(id);
  }

  public static int getUsedPermits(List<Consumer> consumers) {
    return consumers.stream().filter(item -> item.getState() == ACTIVE).mapToInt(Consumer::getPermits).sum();
  }

  private boolean enoughPermits(int permits, int usedPermits) {
    return getSpec().getLimits() - usedPermits >= permits;
  }

  private boolean nonBlocked(List<Consumer> consumers) {
    return consumers.stream().noneMatch(item -> item.getState() == BLOCKED);
  }

  private void checkForBadBlock(Consumer consumer, List<Consumer> consumers, ConstraintRegistry registry)
      throws PermanentlyBlockedConsumerException {
    int sameScopePermits = consumer.getPermits();
    for (Consumer blockedConsumer : consumers) {
      if (registry.overlappingScope(consumer, blockedConsumer)) {
        sameScopePermits += blockedConsumer.getPermits();
      }
    }

    if (sameScopePermits > getSpec().getLimits()) {
      throw new PermanentlyBlockedConsumerException(
          format("Consumer %s will be permanently blocked from other consumers in the same scope",
              consumer.getId().getValue()));
    }
  }

  private State calculateConsumerState(List<Consumer> consumers, int permits, int usedPermits) {
    State state = null;
    final Strategy strategy = getSpec().getStrategy();
    switch (strategy) {
      case FIFO:
        state = nonBlocked(consumers) && enoughPermits(permits, usedPermits) ? ACTIVE : BLOCKED;
        break;
      case ASAP:
        state = enoughPermits(permits, usedPermits) ? ACTIVE : BLOCKED;
        break;
      default:
        unhandled(strategy);
    }

    return state;
  }

  public State registerConsumer(
      ConstraintUnit unit, ConsumerId consumerId, int permits, Map<String, Object> context, ConstraintRegistry registry)
      throws InvalidPermitsException, UnableToRegisterConsumerException, PermanentlyBlockedConsumerException {
    if (permits <= 0) {
      throw new InvalidPermitsException("The permits should be positive number.");
    }
    if (permits > spec.getLimits()) {
      throw new InvalidPermitsException(format(
          "An amount of %d permits cannot be requested from constraint with limit %d.", permits, spec.getLimits()));
    }

    do {
      List<Consumer> consumers = registry.loadConsumers(id, unit, false);
      final int usedPermits = getUsedPermits(consumers);
      State state = calculateConsumerState(consumers, permits, usedPermits);

      if (context != null && Boolean.TRUE.equals(context.get("RESOURCE_CONSTRAINT_MAX_QUEUE"))) {
        long blockedConsumerCount = consumers.stream().filter(consumer -> consumer.getState().equals(BLOCKED)).count();
        if (blockedConsumerCount >= MAX_CONSUMERS_WAITING_FOR_RESOURCE) {
          state = REJECTED;
        }
      }

      final Consumer consumer =
          Consumer.builder().id(consumerId).permits(permits).state(state).context(context).build();

      if (BLOCKED == state) {
        checkForBadBlock(consumer, consumers, registry);
      }

      if (registry.registerConsumer(id, unit, consumer, usedPermits)) {
        return state;
      }

      Morpheus.quietSleep(Duration.ofMillis(random.nextInt(DELAY_FOR_OPTIMISTIC_RETRIES)));
    } while (registry.adjustRegisterConsumerContext(id, context));

    throw new UnableToRegisterConsumerException("Unable to register the constraint.");
  }

  public boolean consumerUnblocked(
      ConstraintUnit unit, ConsumerId consumerId, Map<String, Object> context, ConstraintRegistry registry) {
    return registry.consumerUnblocked(id, unit, consumerId, context);
  }

  boolean consumerFinished(ConstraintUnit unit, ConsumerId consumerId, ConstraintRegistry registry) {
    return registry.consumerFinished(id, unit, consumerId, null);
  }

  public RunnableConsumers runnableConsumers(
      ConstraintUnit unit, ConstraintRegistry registry, boolean hitSecondaryNode) {
    final List<Consumer> consumers = registry.loadConsumers(id, unit, hitSecondaryNode);
    int usedPermits = getUsedPermits(consumers);

    final RunnableConsumersBuilder builder = RunnableConsumers.builder().usedPermits(usedPermits);

    List<ConsumerId> consumerIds = new ArrayList<>();

    for (Consumer consumer : consumers) {
      if (consumer.getState() != BLOCKED) {
        continue;
      }
      if (consumerIds.contains(consumer.getId())) {
        continue;
      }

      if (!enoughPermits(consumer.getPermits(), usedPermits)) {
        final Strategy strategy = getSpec().getStrategy();
        if (strategy == FIFO) {
          break;
        } else if (strategy == ASAP) {
          continue;
        } else {
          unhandled(strategy);
        }
      }

      consumerIds.add(consumer.getId());
      usedPermits += consumer.getPermits();

      // WHEN ONE CONSUMER IS CHOOSE TO RUN, SEARCH FOR OTHERS CONSUMERS WITH THE SAME resourceUnit, releaseEntityType
      // AND releaseEntityId TO NOTIFY AND START THEM. IN THE NEXT ITERATIONS SKIP IF ALREADY ADDED TO FINAL RESULT.
      consumerIds.addAll(searchRelated(consumers, consumer));
    }

    return builder.consumerIds(consumerIds).build();
  }

  /**
   * Search for consumers with the same releaseEntityType and releaseEntityId and return them.
   */
  @VisibleForTesting
  List<ConsumerId> searchRelated(List<Consumer> consumers, Consumer consumer) {
    return consumers.stream()
        .filter(c
            -> isDifferentId(c, consumer) && hasContext(c) && hasContext(consumer)
                && Objects.equals(getReleaseEntityType(c), getReleaseEntityType(consumer))
                && Objects.equals(getReleaseEntityId(c), getReleaseEntityId(consumer)))
        .map(Consumer::getId)
        .collect(Collectors.toList());
  }

  private boolean isDifferentId(Consumer c1, Consumer c2) {
    return !c1.getId().equals(c2.getId());
  }
}
