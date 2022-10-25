/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.threading;

import io.harness.logging.logcontext.AutoLogRemoveAllContext;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CurrentThreadExecutor extends AbstractExecutorService {
  private AtomicBoolean terminated = new AtomicBoolean(false);

  @Override
  public void execute(Runnable command) {
    try (AutoLogRemoveAllContext ignore = new AutoLogRemoveAllContext()) {
      command.run();
    }
  }

  @Override
  public void shutdown() {
    terminated.compareAndSet(false, true);
  }

  @Override
  public List<Runnable> shutdownNow() {
    return Collections.emptyList();
  }

  @Override
  public boolean isShutdown() {
    return terminated.get();
  }

  @Override
  public boolean isTerminated() {
    return terminated.get();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    shutdown();
    return terminated.get();
  }
}
