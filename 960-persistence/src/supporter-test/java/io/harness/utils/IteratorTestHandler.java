/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import io.harness.iterator.IteratorBaseHandler;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IteratorTestHandler extends IteratorBaseHandler {
  private String testIteratorName;

  public IteratorTestHandler(String iteratorName) {
    this.testIteratorName = iteratorName;
  }

  @Override
  public boolean isExecutorTerminated() {
    if (iterator == null) {
      // If the iterator object is not yet created then return by default
      return true;
    }

    return iterator.getExecutorService().isTerminated();
  }

  @Override
  protected void stopExecutor() {
    if (iterator == null) {
      log.error("Iterator is not initialized");
      return;
    }

    ExecutorService executorService = iterator.getExecutorService();
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

  @Override
  protected void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = this.testIteratorName;
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  protected void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator = MongoPersistenceIterator.builder()
                   .executorService(new ScheduledThreadPoolExecutor(
                       executorOptions.getPoolSize(), new ThreadFactoryBuilder().setNameFormat(iteratorName).build()))
                   .build();
  }
}
