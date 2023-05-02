/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.core.entities.SideKick;
import io.harness.cvng.core.entities.SideKick.SideKickData;
import io.harness.cvng.core.entities.SideKick.SidekickKeys;
import io.harness.cvng.core.entities.SideKick.Status;
import io.harness.cvng.core.services.api.SideKickExecutor;
import io.harness.cvng.core.services.api.SideKickExecutor.RetryData;
import io.harness.cvng.core.services.api.SideKickService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

@Singleton
@Slf4j
public class SideKickServiceImpl implements SideKickService {
  @Inject private HPersistence hPersistence;
  @Inject private Map<SideKick.Type, SideKickExecutor> typeSideKickExecutorMap;
  @Inject private Clock clock;
  @Override
  public void schedule(SideKickData sideKickData, Instant runAfter) {
    hPersistence.save(
        SideKick.builder().status(Status.QUEUED).runAfter(runAfter).retryCount(0).sideKickData(sideKickData).build());
    log.info("Saved sidekick: {} run after: {}", sideKickData, runAfter);
  }

  @Override
  public void processNext() {
    while (true) {
      boolean processed = processNextTask();
      if (!processed) {
        break;
      }
    }
  }

  private boolean processNextTask() {
    Query<SideKick> query = hPersistence.createQuery(SideKick.class)
                                .filter(SidekickKeys.status, Status.QUEUED)
                                .field(SidekickKeys.runAfter)
                                .lessThan(clock.instant())
                                .order(Sort.ascending(SidekickKeys.lastUpdatedAt));
    SideKick nextTask = query.get();
    if (Objects.nonNull(nextTask)) {
      log.info("Checking if can execute task: {}", nextTask);
      SideKickExecutor executor = typeSideKickExecutorMap.get(nextTask.getSideKickData().getType());
      if (executor.canExecute(nextTask.getSideKickData())) {
        log.info("Processing task: {}", nextTask);
        UpdateOperations<SideKick> updateOperations = hPersistence.createUpdateOperations(SideKick.class)
                                                          .set(SidekickKeys.status, Status.RUNNING)
                                                          .set(SidekickKeys.lastUpdatedAt, clock.millis());
        hPersistence.update(nextTask, updateOperations);
        executeTask(executor, nextTask);
      } else {
        log.info("Rescheduling task: {}", nextTask);
        reschedule(executor, nextTask);
      }
      return true;
    } else {
      return false;
    }
  }

  private void executeTask(SideKickExecutor executor, SideKick task) {
    Status status = null;
    String exceptionStr = null;
    String stacktrace = null;
    try {
      executor.execute(task.getSideKickData());
      status = Status.SUCCESS;
    } catch (Exception exception) {
      status = Status.FAILED;
      exceptionStr = exception.getMessage();
      stacktrace = ExceptionUtils.getStackTrace(exception);
    } finally {
      UpdateOperations<SideKick> statusUpdateOperations =
          hPersistence.createUpdateOperations(SideKick.class).set(SidekickKeys.status, status);
      if (exceptionStr != null) {
        statusUpdateOperations.set(SidekickKeys.exception, exceptionStr);
      }
      if (stacktrace != null) {
        statusUpdateOperations.set(SidekickKeys.stacktrace, stacktrace);
      }
      hPersistence.update(task, statusUpdateOperations);

      if (status == Status.FAILED) {
        RetryData retryData =
            typeSideKickExecutorMap.get(task.getSideKickData().getType()).shouldRetry(task.getRetryCount());
        if (retryData.isShouldRetry()) {
          shouldRetry(task, retryData.getNextRetryTime());
        }
      }
    }
  }

  private void shouldRetry(SideKick sideKick, Instant nextRunTime) {
    UpdateOperations<SideKick> statusUpdateOperations = hPersistence.createUpdateOperations(SideKick.class)
                                                            .set(SidekickKeys.status, Status.QUEUED)
                                                            .inc(SidekickKeys.retryCount)
                                                            .set(SidekickKeys.runAfter, nextRunTime);

    hPersistence.update(sideKick, statusUpdateOperations);
  }

  private void reschedule(SideKickExecutor executor, SideKick task) {
    long currentTime = clock.millis();
    UpdateOperations<SideKick> updateOperations =
        hPersistence.createUpdateOperations(SideKick.class)
            .set(SidekickKeys.lastUpdatedAt, currentTime)
            .set(SidekickKeys.runAfter, Instant.ofEpochMilli(currentTime).plus(executor.delayExecutionBy()));
    hPersistence.update(task, updateOperations);
  }
}
