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

import io.harness.distribution.constraint.Consumer.State;
import io.harness.exception.WingsException;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.logging.ExceptionLogger;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResourceConstraintBackupHandler implements Handler<ResourceConstraintInstance> {
  private static final String handlerName = "ResourceConstraint-Backup";
  private static final int scheduleIntervalSeconds = 60;

  @Inject private AccountService accountService;
  @Inject private ResourceConstraintService resourceConstraintService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<ResourceConstraintInstance> persistenceProvider;

  public void registerIterators(int threadPoolSize) {
    PumpExecutorOptions executorOptions = PumpExecutorOptions.builder()
                                              .name(handlerName)
                                              .poolSize(threadPoolSize)
                                              .interval(ofSeconds(scheduleIntervalSeconds))
                                              .build();
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
        ResourceConstraintBackupHandler.class,
        MongoPersistenceIterator
            .<ResourceConstraintInstance, MorphiaFilterExpander<ResourceConstraintInstance>>builder()
            .clazz(ResourceConstraintInstance.class)
            .fieldName(ResourceConstraintInstanceKeys.nextIteration)
            .filterExpander(q -> q.field(ResourceConstraintInstanceKeys.state).in(NOT_FINISHED_STATES))
            .targetInterval(ofSeconds(30))
            .acceptableNoAlertDelay(ofSeconds(30))
            .acceptableExecutionTime(ofSeconds(30))
            .handler(this)
            .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  @Override
  public void handle(ResourceConstraintInstance instance) {
    String constraintId = instance.getResourceConstraintId();
    boolean toUnblock = false;
    try {
      if (State.BLOCKED.name().equals(instance.getState())) {
        log.debug("This is a completely blocked constraint: {}", constraintId);
        toUnblock = true;
      } else if (State.ACTIVE.name().equals(instance.getState())) {
        if (resourceConstraintService.updateActiveConstraintForInstance(instance)) {
          log.info("The following resource constrained need to be unblocked: {}", constraintId);
          toUnblock = true;
        }
      }
      if (toUnblock) {
        // Unblock the constraints that can be unblocked
        resourceConstraintService.updateBlockedConstraints(Sets.newHashSet(constraintId));
      }
    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (RuntimeException e) {
      log.error(String.format("An error occurred when running resource constraint monitor [instance=%s]", instance), e);
    }
  }
}
