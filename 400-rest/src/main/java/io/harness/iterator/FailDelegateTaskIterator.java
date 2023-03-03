/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iterator;

import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import software.wings.core.managerConfiguration.ConfigurationController;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Singleton
@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._870_ORCHESTRATION)
public class FailDelegateTaskIterator
    extends IteratorPumpAndRedisModeHandler implements MongoPersistenceIterator.Handler<DelegateTask> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<DelegateTask> persistenceProvider;
  @Inject private ConfigurationController configurationController;
  @Inject private FailDelegateTaskIteratorHelper failDelegateTaskIteratorHelper;

  private static final Duration ACCEPTABLE_NO_ALERT_DELAY = ofSeconds(45);
  private static final Duration ACCEPTABLE_EXECUTION_TIME = ofSeconds(30);

  @Override
  public void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<DelegateTask, MorphiaFilterExpander<DelegateTask>>)
                   persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                       FailDelegateTaskIterator.class,
                       MongoPersistenceIterator.<DelegateTask, MorphiaFilterExpander<DelegateTask>>builder()
                           .clazz(DelegateTask.class)
                           .fieldName(DelegateTaskKeys.delegateTaskFailIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
                           .filterExpander(query
                               -> query.criteria(DelegateTaskKeys.createdAt)
                                      .lessThan(currentTimeMillis() - TimeUnit.MINUTES.toMillis(1)))
                           .handler(this)
                           .schedulingType(MongoPersistenceIterator.SchedulingType.REGULAR)
                           .persistenceProvider(persistenceProvider)
                           .unsorted(true)
                           .redistribute(true));
  }

  @Override
  public void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<DelegateTask, MorphiaFilterExpander<DelegateTask>>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       FailDelegateTaskIterator.class,
                       MongoPersistenceIterator.<DelegateTask, MorphiaFilterExpander<DelegateTask>>builder()
                           .clazz(DelegateTask.class)
                           .fieldName(DelegateTaskKeys.delegateTaskFailIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
                           .filterExpander(query
                               -> query.criteria(DelegateTaskKeys.createdAt)
                                      .lessThan(currentTimeMillis() - TimeUnit.MINUTES.toMillis(1)))
                           .handler(this)
                           .persistenceProvider(persistenceProvider));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "DelegateTaskFail";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(DelegateTask delegateTask) {
    if (configurationController.isPrimary()) {
      failDelegateTaskIteratorHelper.markTimedOutTasksAsFailed(delegateTask, false);
      failDelegateTaskIteratorHelper.markNotAcquiredAfterMultipleBroadcastAsFailed(delegateTask, true);
      failDelegateTaskIteratorHelper.markLongQueuedTasksAsFailed(delegateTask, false);
      failDelegateTaskIteratorHelper.failValidationCompletedQueuedTask(delegateTask, false);
    }
  }
}
