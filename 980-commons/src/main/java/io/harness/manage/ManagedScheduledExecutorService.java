/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.manage;

import static io.harness.manage.GlobalContextManager.generateExecutorTask;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ManagedScheduledExecutorService extends ManagedExecutorService implements ScheduledExecutorService {
  private static ScheduledThreadPoolExecutor createScheduledThreadPoolExecutor(String name) {
    ScheduledThreadPoolExecutor scheduledThreadPoolExecutor =
        new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat(name).build());
    scheduledThreadPoolExecutor.setRemoveOnCancelPolicy(true);
    return scheduledThreadPoolExecutor;
  }

  /**
   * Instantiates a new managed scheduled executor service.
   *
   * @param name the name
   */
  public ManagedScheduledExecutorService(String name) {
    super(createScheduledThreadPoolExecutor(name));
  }

  /* (non-Javadoc)
   * @see java.util.concurrent.ScheduledExecutorService#schedule(java.lang.Runnable, long,
   * java.util.concurrent.TimeUnit)
   */
  @Override
  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    return ((ScheduledExecutorService) getExecutorService()).schedule(generateExecutorTask(command), delay, unit);
  }

  /* (non-Javadoc)
   * @see java.util.concurrent.ScheduledExecutorService#schedule(java.util.concurrent.Callable, long,
   * java.util.concurrent.TimeUnit)
   */
  @Override
  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    return ((ScheduledExecutorService) getExecutorService()).schedule(generateExecutorTask(callable), delay, unit);
  }

  /* (non-Javadoc)
   * @see java.util.concurrent.ScheduledExecutorService#scheduleAtFixedRate(java.lang.Runnable, long, long,
   * java.util.concurrent.TimeUnit)
   */
  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
    return ((ScheduledExecutorService) getExecutorService())
        .scheduleAtFixedRate(generateExecutorTask(command), initialDelay, period, unit);
  }

  /* (non-Javadoc)
   * @see java.util.concurrent.ScheduledExecutorService#scheduleWithFixedDelay(java.lang.Runnable, long, long,
   * java.util.concurrent.TimeUnit)
   */
  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
    return ((ScheduledExecutorService) getExecutorService())
        .scheduleWithFixedDelay(generateExecutorTask(command), initialDelay, delay, unit);
  }

  /* (non-Javadoc)
   * @see software.wings.utils.ManagedExecutorService#stop()
   */
  @Override
  public void stop() throws Exception {
    shutdown();
    awaitTermination(10000, TimeUnit.MILLISECONDS);
  }
}
