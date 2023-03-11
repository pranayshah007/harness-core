/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iterator;

import static java.lang.System.currentTimeMillis;

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
// TODO: ARPIT remove this iterator once delegate task migration has been done
public class FailDelegateTaskIteratorOnDMS
    extends IteratorPumpAndRedisModeHandler implements MongoPersistenceIterator.Handler<DelegateTask> {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MorphiaPersistenceProvider<DelegateTask> persistenceProvider;
  @Inject private ConfigurationController configurationController;
  @Inject private FailDelegateTaskIteratorHelper failDelegateTaskIteratorHelper;

  @Override
  public void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<DelegateTask, MorphiaFilterExpander<DelegateTask>>)
                   persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                       FailDelegateTaskIteratorOnDMS.class,
                       MongoPersistenceIterator.<DelegateTask, MorphiaFilterExpander<DelegateTask>>builder()
                           .clazz(DelegateTask.class)
                           .fieldName(DelegateTaskKeys.delegateTaskFailIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(Duration.ofSeconds(45))
                           .acceptableExecutionTime(Duration.ofSeconds(30))
                           .filterExpander(query
                               -> query.criteria(DelegateTaskKeys.createdAt)
                                      .lessThan(currentTimeMillis() - TimeUnit.MINUTES.toMillis(1)))
                           .handler(this)
                           .schedulingType(MongoPersistenceIterator.SchedulingType.REGULAR)
                           .persistenceProvider(persistenceProvider)
                           .unsorted(true)
                           .isDelegateTaskMigrationEnabled(true)
                           .redistribute(true));
  }

  @Override
  public void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<DelegateTask, MorphiaFilterExpander<DelegateTask>>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       FailDelegateTaskIteratorOnDMS.class,
                       MongoPersistenceIterator.<DelegateTask, MorphiaFilterExpander<DelegateTask>>builder()
                           .clazz(DelegateTask.class)
                           .fieldName(DelegateTaskKeys.delegateTaskFailIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(Duration.ofSeconds(45))
                           .acceptableExecutionTime(Duration.ofSeconds(30))
                           .filterExpander(query
                               -> query.criteria(DelegateTaskKeys.createdAt)
                                      .lessThan(currentTimeMillis() - TimeUnit.MINUTES.toMillis(1)))
                           .handler(this)
                           .persistenceProvider(persistenceProvider)
                           .isDelegateTaskMigrationEnabled(true));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "DelegateTaskFailOnDMS";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(DelegateTask delegateTask) {
    if (configurationController.isPrimary()) {
      failDelegateTaskIteratorHelper.markTimedOutTasksAsFailed(delegateTask, true);
      failDelegateTaskIteratorHelper.markNotAcquiredAfterMultipleBroadcastAsFailed(delegateTask, true);
      failDelegateTaskIteratorHelper.markLongQueuedTasksAsFailed(delegateTask, true);
      failDelegateTaskIteratorHelper.failValidationCompletedQueuedTask(delegateTask, true);
    }
  }
}
