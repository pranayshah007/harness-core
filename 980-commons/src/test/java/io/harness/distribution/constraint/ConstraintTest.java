/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.distribution.constraint;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.distribution.constraint.Consumer.State.ACTIVE;
import static io.harness.distribution.constraint.Consumer.State.BLOCKED;
import static io.harness.distribution.constraint.Consumer.State.FINISHED;
import static io.harness.distribution.constraint.Consumer.State.REJECTED;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.INDER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.distribution.constraint.Constraint.Spec;
import io.harness.distribution.constraint.Constraint.Strategy;
import io.harness.rule.Owner;
import io.harness.threading.Concurrent;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ConstraintTest extends CategoryTest {
  private static final SecureRandom random = new SecureRandom();

  ConstraintId id = new ConstraintId("foo");

  ConstraintUnit unit1 = new ConstraintUnit("unit1");
  ConstraintUnit unit2 = new ConstraintUnit("unit2");

  ConsumerId consumer1 = new ConsumerId("consumer1");
  ConsumerId consumer2 = new ConsumerId("consumer2");
  ConsumerId consumer3 = new ConsumerId("consumer3");
  ConsumerId consumer4 = new ConsumerId("consumer4");

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testCreateConstraint() throws UnableToSaveConstraintException {
    ConstraintRegistry registry = new InprocConstraintRegistry();

    Constraint constraint = Constraint.create(id, Spec.builder().strategy(Strategy.FIFO).limits(10).build(), registry);
    assertThat(constraint).isNotNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testLoadConstraint() throws UnableToSaveConstraintException, UnableToLoadConstraintException {
    ConstraintRegistry registry = new InprocConstraintRegistry();

    Constraint constraint = Constraint.load(id, registry);
    assertThat(constraint).isNull();

    Constraint newConstraint =
        Constraint.create(id, Spec.builder().strategy(Strategy.FIFO).limits(10).build(), registry);

    constraint = Constraint.load(id, registry);

    assertThat(constraint).isEqualTo(newConstraint);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testRegisterConsumer()
      throws UnableToSaveConstraintException, InvalidPermitsException, UnableToRegisterConsumerException {
    ConstraintRegistry registry = new InprocConstraintRegistry();

    Constraint constraint = Constraint.create(id, Spec.builder().strategy(Strategy.FIFO).limits(10).build(), registry);

    assertThatExceptionOfType(InvalidPermitsException.class)
        .isThrownBy(() -> constraint.registerConsumer(unit1, consumer1, -5, null, registry));
    assertThatExceptionOfType(InvalidPermitsException.class)
        .isThrownBy(() -> constraint.registerConsumer(unit1, consumer1, 0, null, registry));
    assertThatExceptionOfType(InvalidPermitsException.class)
        .isThrownBy(() -> constraint.registerConsumer(unit1, consumer1, 11, null, registry));
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testRegisterConsumerFIFO() throws UnableToSaveConstraintException, InvalidPermitsException,
                                                UnableToRegisterConsumerException, PermanentlyBlockedConsumerException {
    ConstraintRegistry registry = new InprocConstraintRegistry();

    Constraint constraint = Constraint.create(id, Spec.builder().strategy(Strategy.FIFO).limits(10).build(), registry);
    assertThat(constraint.registerConsumer(unit1, consumer1, 1, null, registry)).isEqualTo(ACTIVE);
    assertThat(constraint.registerConsumer(unit1, consumer2, 10, null, registry)).isEqualTo(BLOCKED);
    assertThat(constraint.registerConsumer(unit1, consumer3, 1, null, registry)).isEqualTo(BLOCKED);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testRegisterConsumerASAP() throws UnableToSaveConstraintException, InvalidPermitsException,
                                                UnableToRegisterConsumerException, PermanentlyBlockedConsumerException {
    ConstraintRegistry registry = new InprocConstraintRegistry();

    Constraint constraint = Constraint.create(id, Spec.builder().strategy(Strategy.ASAP).limits(10).build(), registry);
    assertThat(constraint.registerConsumer(unit1, consumer1, 1, null, registry)).isEqualTo(ACTIVE);
    assertThat(constraint.registerConsumer(unit1, consumer2, 10, null, registry)).isEqualTo(BLOCKED);
    assertThat(constraint.registerConsumer(unit1, consumer3, 1, null, registry)).isEqualTo(ACTIVE);
    assertThat(constraint.registerConsumer(unit1, consumer4, 9, null, registry)).isEqualTo(BLOCKED);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testRegisterConsumerUnblocked()
      throws UnableToSaveConstraintException, InvalidPermitsException, UnableToRegisterConsumerException,
             PermanentlyBlockedConsumerException {
    ConstraintRegistry registry = new InprocConstraintRegistry();

    Constraint constraint = Constraint.create(id, Spec.builder().strategy(Strategy.ASAP).limits(10).build(), registry);
    assertThat(constraint.registerConsumer(unit1, consumer1, 10, null, registry)).isEqualTo(ACTIVE);
    assertThat(constraint.registerConsumer(unit1, consumer2, 3, null, registry)).isEqualTo(BLOCKED);
    assertThat(constraint.registerConsumer(unit1, consumer3, 5, null, registry)).isEqualTo(BLOCKED);
    assertThat(constraint.registerConsumer(unit1, consumer4, 5, null, registry)).isEqualTo(BLOCKED);

    assertThat(constraint.consumerFinished(unit1, consumer1, registry)).isTrue();

    // Unblock already finished
    assertThat(constraint.consumerUnblocked(unit1, consumer1, null, registry)).isFalse();

    assertThat(constraint.consumerUnblocked(unit1, consumer2, null, registry)).isTrue();

    // Unblock already running
    assertThat(constraint.consumerUnblocked(unit1, consumer2, null, registry)).isFalse();

    assertThat(constraint.consumerUnblocked(unit1, consumer3, null, registry)).isTrue();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testRegisterConsumerFinished()
      throws UnableToSaveConstraintException, InvalidPermitsException, UnableToRegisterConsumerException,
             PermanentlyBlockedConsumerException {
    ConstraintRegistry registry = new InprocConstraintRegistry();

    Constraint constraint = Constraint.create(id, Spec.builder().strategy(Strategy.ASAP).limits(10).build(), registry);
    assertThat(constraint.registerConsumer(unit1, consumer1, 10, null, registry)).isEqualTo(ACTIVE);
    assertThat(constraint.registerConsumer(unit1, consumer2, 3, null, registry)).isEqualTo(BLOCKED);

    // finish blocked
    assertThat(constraint.consumerFinished(unit1, consumer2, registry)).isFalse();

    assertThat(constraint.consumerFinished(unit1, consumer1, registry)).isTrue();

    // finish blocked again with no running
    assertThat(constraint.consumerFinished(unit1, consumer2, registry)).isFalse();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testRunnableConsumersASAP()
      throws UnableToSaveConstraintException, InvalidPermitsException, UnableToRegisterConsumerException,
             PermanentlyBlockedConsumerException {
    ConstraintRegistry registry = new InprocConstraintRegistry();

    Constraint constraint = Constraint.create(id, Spec.builder().strategy(Strategy.ASAP).limits(10).build(), registry);
    assertThat(constraint.registerConsumer(unit1, consumer1, 10, null, registry)).isEqualTo(ACTIVE);
    assertThat(constraint.registerConsumer(unit1, consumer2, 3, null, registry)).isEqualTo(BLOCKED);
    assertThat(constraint.registerConsumer(unit1, consumer3, 8, null, registry)).isEqualTo(BLOCKED);
    assertThat(constraint.registerConsumer(unit1, consumer4, 3, null, registry)).isEqualTo(BLOCKED);

    assertThat(constraint.runnableConsumers(unit1, registry, false).getConsumerIds()).isEmpty();

    assertThat(constraint.consumerFinished(unit1, consumer1, registry)).isTrue();

    assertThat(constraint.runnableConsumers(unit1, registry, false).getConsumerIds()).contains(consumer2, consumer4);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testUnits() throws UnableToSaveConstraintException, InvalidPermitsException,
                                 UnableToRegisterConsumerException, PermanentlyBlockedConsumerException {
    ConstraintRegistry registry = new InprocConstraintRegistry();

    Constraint constraint1 = Constraint.create(id, Spec.builder().strategy(Strategy.ASAP).limits(10).build(), registry);
    assertThat(constraint1.registerConsumer(unit1, consumer1, 10, null, registry)).isEqualTo(ACTIVE);
    assertThat(constraint1.registerConsumer(unit2, consumer2, 10, null, registry)).isEqualTo(ACTIVE);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testRunnableConsumersFIFO()
      throws UnableToSaveConstraintException, InvalidPermitsException, UnableToRegisterConsumerException,
             PermanentlyBlockedConsumerException {
    ConstraintRegistry registry = new InprocConstraintRegistry();

    Constraint constraint = Constraint.create(id, Spec.builder().strategy(Strategy.FIFO).limits(10).build(), registry);
    assertThat(constraint.registerConsumer(unit1, consumer1, 10, null, registry)).isEqualTo(ACTIVE);
    assertThat(constraint.registerConsumer(unit1, consumer2, 3, null, registry)).isEqualTo(BLOCKED);
    assertThat(constraint.registerConsumer(unit1, consumer3, 8, null, registry)).isEqualTo(BLOCKED);
    assertThat(constraint.registerConsumer(unit1, consumer4, 3, null, registry)).isEqualTo(BLOCKED);

    assertThat(constraint.runnableConsumers(unit1, registry, false).getConsumerIds()).isEmpty();

    assertThat(constraint.consumerFinished(unit1, consumer1, registry)).isTrue();

    assertThat(constraint.runnableConsumers(unit1, registry, false).getConsumerIds()).contains(consumer2);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testSimulation() throws UnableToSaveConstraintException, InvalidPermitsException,
                                      UnableToRegisterConsumerException, PermanentlyBlockedConsumerException {
    ConstraintRegistry registry = new InprocConstraintRegistry();

    Constraint constraint = Constraint.create(id, Spec.builder().strategy(Strategy.FIFO).limits(10).build(), registry);
    assertThat(constraint.registerConsumer(unit1, consumer1, 10, null, registry)).isEqualTo(ACTIVE);

    for (int i = 0; i < 100; ++i) {
      ConsumerId consumerId = new ConsumerId("c" + i);
      assertThat(constraint.registerConsumer(unit1, consumerId, random.nextInt(10) + 1, null, registry))
          .isEqualTo(BLOCKED);
    }

    Concurrent.test(2, i -> {
      while (true) {
        if (i == 0) {
          final List<Consumer> consumers = registry.loadConsumers(constraint.getId(), unit1, false);
          if (consumers.stream().noneMatch(consumer -> consumer.getState() != FINISHED)) {
            break;
          }
          final List<Consumer> list =
              consumers.stream().filter(consumer -> consumer.getState() == ACTIVE).collect(Collectors.toList());
          if (isEmpty(list)) {
            continue;
          }
          final Consumer consumer = list.get(random.nextInt(list.size()));
          registry.consumerFinished(constraint.getId(), unit1, consumer.getId(), null);
        } else {
          final RunnableConsumers runnableConsumers = constraint.runnableConsumers(unit1, registry, false);
          if (runnableConsumers.getUsedPermits() == 0 && isEmpty(runnableConsumers.getConsumerIds())) {
            break;
          }

          for (ConsumerId consumerId : runnableConsumers.getConsumerIds()) {
            if (!constraint.consumerUnblocked(unit1, consumerId, null, registry)) {
              break;
            }
          }
        }
      }
    });

    final List<Consumer> consumers = registry.loadConsumers(constraint.getId(), unit1, false);
    assertThat(consumers.stream().anyMatch(consumer -> consumer.getState() != FINISHED)).isFalse();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testRegisterConsumerWithBlockedConsumersGreaterThan20()
      throws UnableToSaveConstraintException, InvalidPermitsException, UnableToRegisterConsumerException,
             PermanentlyBlockedConsumerException {
    ConstraintRegistry registry = new InprocConstraintRegistry();
    Map<String, Object> constraintContext = new HashMap<>();
    constraintContext.put("RESOURCE_CONSTRAINT_MAX_QUEUE", true);

    Constraint constraint = Constraint.create(id, Spec.builder().strategy(Strategy.FIFO).limits(10).build(), registry);
    assertThat(constraint.registerConsumer(unit1, consumer1, 1, constraintContext, registry)).isEqualTo(ACTIVE);
    for (int i = 0; i < 20; i++) {
      assertThat(constraint.registerConsumer(unit1, consumer2, 10, constraintContext, registry)).isEqualTo(BLOCKED);
    }
    assertThat(constraint.registerConsumer(unit1, consumer3, 1, constraintContext, registry)).isEqualTo(REJECTED);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSearchRelatedConsumers() throws UnableToSaveConstraintException {
    List<Consumer> consumers = new ArrayList<>();
    consumers.add(createConsumer("A", "entityTypeA", "entityIdA"));
    consumers.add(createConsumer("B", "entityTypeB", "entityIdB"));
    consumers.add(createConsumer("C", "entityTypeB", "entityIdB"));
    consumers.add(createConsumer("D", "entityTypeB", "entityIdB"));

    ConstraintRegistry registry = new InprocConstraintRegistry();
    Constraint constraint = Constraint.create(id, Spec.builder().strategy(Strategy.FIFO).limits(10).build(), registry);

    List<ConsumerId> consumerIds = constraint.searchRelated(consumers, createConsumer("C", "entityTypeB", "entityIdB"));
    assertThat(consumerIds).isNotNull();
    assertThat(consumerIds).containsOnly(new ConsumerId("B"), new ConsumerId("D"));
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSearchRelatedConsumersNotFound() throws UnableToSaveConstraintException {
    List<Consumer> consumers = new ArrayList<>();
    consumers.add(createConsumer("A", "entityTypeA", "entityIdA"));
    consumers.add(createConsumer("B", "entityTypeB", "entityIdB"));
    consumers.add(createConsumer("C", "entityTypeC", "entityIdC"));
    consumers.add(createConsumer("D", "entityTypeD", "entityIdD"));

    ConstraintRegistry registry = new InprocConstraintRegistry();
    Constraint constraint = Constraint.create(id, Spec.builder().strategy(Strategy.FIFO).limits(10).build(), registry);

    List<ConsumerId> consumerIds = constraint.searchRelated(consumers, createConsumer("C", "entityTypeC", "entityIdC"));
    assertThat(consumerIds).isNotNull();
    assertThat(consumerIds).isEmpty();
  }

  private Consumer createConsumer(String id, String releaseEntityType, String releaseEntityId) {
    return Consumer.builder().id(new ConsumerId(id)).context(createContext(releaseEntityType, releaseEntityId)).build();
  }

  private Map<String, Object> createContext(String releaseEntityType, String releaseEntityId) {
    HashMap<String, Object> context = new HashMap<>();
    context.put("releaseEntityType", releaseEntityType);
    context.put("releaseEntityId", releaseEntityId);
    return context;
  }
}
