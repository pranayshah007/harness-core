/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

@OwnedBy(PL)
class BoundedExecutorService {
  //private final ExecutorService executorService;
  private final ExecutorService executorService;
  private final Semaphore semaphore;

  BoundedExecutorService(ExecutorService executorService, int bound) {
    this.executorService = executorService;
    this.semaphore = new Semaphore(bound);
  }

  <T> Future<T> submit(final Callable<T> task) throws InterruptedException {
    semaphore.acquire();
    try {
      return executorService.submit(() -> {
        try {
          return task.call();
        } finally {
          semaphore.release();
        }
      });
    } catch (RejectedExecutionException e) {
      semaphore.release();
      throw e;
    }
  }
}
