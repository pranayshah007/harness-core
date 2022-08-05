/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.entities.CDCEntity;

import software.wings.dl.WingsPersistence;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CE)
class ChangeEventProcessor {
  @Inject private Set<CDCEntity<?>> subscribedClasses;
  @Inject private WingsPersistence wingsPersistence;
  private final BlockingQueue<ChangeEvent<?>> changeEventQueue = new LinkedBlockingQueue<>();
  private final ExecutorService changeEventExecutorService =
      Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("primary-change-processor").build());
  private final ExecutorService changeEventProcessorWatcher =
      Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("change-processor-watcher").build());
  private Future<?> changeEventProcessorTaskFuture;
  private final AtomicLong total = new AtomicLong(0);
  private ChangeEventProcessorTask changeEventProcessorTask;

  void startProcessingChangeEvents() {
    changeEventProcessorTask = new ChangeEventProcessorTask(subscribedClasses, changeEventQueue, wingsPersistence);
    changeEventProcessorTaskFuture = changeEventExecutorService.submit(changeEventProcessorTask);
    changeEventProcessorWatcher.submit(this::watchChangeEventQueue);
  }

  boolean processChangeEvent(ChangeEvent<?> changeEvent) {
    try {
      log.trace("Adding change event in the queue, entity={}, type={}", changeEvent.getEntityType(),
          changeEvent.getChangeType());
      changeEventQueue.put(changeEvent);
      total.incrementAndGet();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Interrupted while waiting to add a change event in the queue", e.getCause());
      return false;
    }
    return true;
  }

  private void watchChangeEventQueue() {
    while (!changeEventProcessorWatcher.isShutdown()) {
      LockSupport.parkNanos(Duration.ofSeconds(30).toNanos());
      int waiting = changeEventQueue.size();
      int processing = changeEventProcessorTask.getActiveCount();
      log.info("ChangeEventProcessor stats, processing={}, waiting={}, completed={}, total={}", processing, waiting,
          changeEventProcessorTask.getCompletedTaskCount(), total.get());
      if ((processing + waiting) > 10) {
        changeEventQueue.stream()
            .collect(Collectors.groupingBy(ChangeEvent::getEntityType, Collectors.counting()))
            .forEach(
                (entity,
                    count) -> log.info("ChangeEventProcessor stats breakdown, entity={}, waiting={}", entity, count));
      }
    }
  }

  boolean isAlive() {
    return !changeEventProcessorTaskFuture.isDone();
  }

  void shutdown() {
    changeEventProcessorTaskFuture.cancel(true);
    changeEventProcessorWatcher.shutdownNow();
    changeEventExecutorService.shutdownNow();
  }
}
