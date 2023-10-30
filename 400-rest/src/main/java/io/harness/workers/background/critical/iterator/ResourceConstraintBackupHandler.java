/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.workers.background.critical.iterator;

import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static software.wings.beans.ResourceConstraintInstance.NOT_FINISHED_STATES;

import static java.time.Duration.ofSeconds;

import io.harness.beans.FeatureName;
import io.harness.distribution.constraint.Consumer.State;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpAndRedisModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;

import software.wings.beans.ResourceConstraintInstance;
import software.wings.beans.ResourceConstraintInstance.ResourceConstraintInstanceKeys;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.ResourceConstraintService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResourceConstraintBackupHandler
    extends IteratorPumpAndRedisModeHandler implements Handler<ResourceConstraintInstance> {
  private static final String handlerName = "ResourceConstraint-Backup";
  private static final Duration ACCEPTABLE_NO_ALERT_DELAY = ofSeconds(30);
  private static final Duration ACCEPTABLE_EXECUTION_TIME = ofSeconds(30);

  @Inject private AccountService accountService;
  @Inject private ResourceConstraintService resourceConstraintService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<ResourceConstraintInstance> persistenceProvider;
  @Inject private FeatureFlagService featureFlagService;

  @Override
  protected void createAndStartIterator(PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<ResourceConstraintInstance, MorphiaFilterExpander<ResourceConstraintInstance>>)
                   persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                       ResourceConstraintBackupHandler.class,
                       MongoPersistenceIterator
                           .<ResourceConstraintInstance, MorphiaFilterExpander<ResourceConstraintInstance>>builder()
                           .clazz(ResourceConstraintInstance.class)
                           .fieldName(ResourceConstraintInstanceKeys.nextIteration)
                           .filterExpander(q -> q.field(ResourceConstraintInstanceKeys.state).in(NOT_FINISHED_STATES))
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
                           .handler(this)
                           .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
                           .schedulingType(REGULAR)
                           .persistenceProvider(persistenceProvider)
                           .redistribute(true));
  }

  @Override
  protected void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<ResourceConstraintInstance, MorphiaFilterExpander<ResourceConstraintInstance>>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       ResourceConstraintBackupHandler.class,
                       MongoPersistenceIterator
                           .<ResourceConstraintInstance, MorphiaFilterExpander<ResourceConstraintInstance>>builder()
                           .clazz(ResourceConstraintInstance.class)
                           .fieldName(ResourceConstraintInstanceKeys.nextIteration)
                           .filterExpander(q -> q.field(ResourceConstraintInstanceKeys.state).in(NOT_FINISHED_STATES))
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
                           .handler(this)
                           .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
                           .persistenceProvider(persistenceProvider));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = handlerName;

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(ResourceConstraintInstance instance) {
    String constraintId = instance.getResourceConstraintId();
    boolean toUnblock = false;
    boolean hitSecondaryNode = false;
    try {
      if (State.BLOCKED.name().equals(instance.getState())) {
        log.debug("This is a completely blocked constraint: {}", constraintId);
        toUnblock = true;
        hitSecondaryNode = true;
      } else if (State.ACTIVE.name().equals(instance.getState())) {
        if (resourceConstraintService.updateActiveConstraintForInstance(instance)) {
          log.info("The following resource constrained need to be unblocked: {}", constraintId);
          toUnblock = true;
        }
      }
      if (toUnblock) {
        // Unblock the constraints that can be unblocked
        if (featureFlagService.isEnabled(
                FeatureName.CDS_RESOURCE_CONSTRAINT_INSTANCE_OPTIMIZATION, instance.getAccountId())) {
          resourceConstraintService.updateBlockedConstraintsV2(Sets.newHashSet(constraintId), hitSecondaryNode);
        } else {
          resourceConstraintService.updateBlockedConstraints(Sets.newHashSet(constraintId));
        }
      }
    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (RuntimeException e) {
      log.error(String.format("An error occurred when running resource constraint monitor [instance=%s]", instance), e);
    }
  }
}
