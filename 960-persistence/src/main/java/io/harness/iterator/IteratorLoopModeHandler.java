/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iterator;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class IteratorLoopModeHandler extends IteratorBaseHandler {
  protected ExecutorService executor;

  /**
   * This method returns true if both the executor services are terminated and vice versa.
   * If the child class extending this Base class doesn't initialize iterator,
   * then true is returned to avoid unexpected behaviours at the caller.
   * @return true / false
   */
  @Override
  public boolean isExecutorTerminated() {
    if (iterator == null || executor == null) {
      // If the iterator object is not yet created then return by default
      return true;
    }

    return iterator.getExecutorService().isTerminated() && executor.isTerminated();
  }

  /**
   * Overriding the base stopExecutor method since here this
   * has another executor that runs a different job.
   */
  @Override
  protected void stopExecutor() {
    if (executor == null) {
      // The executor is not yet initialized, so this iterator is
      // starting for the very first time, thus return.
      log.error("Executor not yet initialized for iterator {}", iteratorName);
      return;
    }

    log.info("Shutting down the executor for iterator {}", iteratorName);
    executor.shutdown();
    try {
      // Wait for 2s to allow all the current executing tasks to terminate.
      if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
    }

    ExecutorService executorService = iterator.getExecutorService();
    if (executorService == null) {
      // The executor service is not yet initialized, so this iterator is
      // starting for the very first time, thus return.
      log.error("Executor Service not yet initialized for iterator {}", iteratorName);
      return;
    }

    log.info("Shutting down the executorService for iterator {}", iteratorName);
    executorService.shutdown();
    try {
      // Wait for 2s to allow all the current executing tasks to terminate.
      if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
        executorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      executorService.shutdownNow();
    }
  }

  @Override protected abstract void registerIterator(IteratorExecutionHandler iteratorExecutionHandler);

  @Override
  protected abstract void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval);
}
