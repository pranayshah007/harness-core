/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.threading;

import static io.harness.threading.Morpheus.quietSleep;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.manage.ManagedExecutorService;
import io.harness.manage.ManagedScheduledExecutorService;

import com.codahale.metrics.InstrumentedExecutorService;
import com.codahale.metrics.InstrumentedScheduledExecutorService;
import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;

/**
 * This is a common threadpool for the entire application.
 */
@OwnedBy(HarnessTeam.PL)
@UtilityClass
public class ThreadPool {
  private static final int CORE_POOL_SIZE = 20;
  private static final int MAX_POOL_SIZE = 1000;
  private static final long IDLE_TIME = 500L;
  private static final ThreadPoolExecutor commonPool =
      create(CORE_POOL_SIZE, MAX_POOL_SIZE, IDLE_TIME, TimeUnit.MILLISECONDS);

  /**
   * Creates the thread pool executor.
   *
   * @param corePoolSize the core pool size
   * @param maxPoolSize  the max pool size
   * @param idleTime     the idle time
   * @param unit         the unit
   * @return the thread pool executor
   */
  public static ThreadPoolExecutor create(int corePoolSize, int maxPoolSize, long idleTime, TimeUnit unit) {
    return create(corePoolSize, maxPoolSize, idleTime, unit, Executors.defaultThreadFactory());
  }

  public static ThreadPoolExecutor create(ThreadPoolConfig poolConfig, ThreadFactory threadFactory) {
    return create(poolConfig.getCorePoolSize(), poolConfig.getMaxPoolSize(), poolConfig.getIdleTime(),
        poolConfig.getTimeUnit(), threadFactory, -1, new ForceQueuePolicy());
  }

  public static ThreadPoolExecutor create(ThreadPoolConfig poolConfig, int queueSize, ThreadFactory threadFactory,
      RejectedExecutionHandler rejectedExecutionHandler) {
    return create(poolConfig.getCorePoolSize(), poolConfig.getMaxPoolSize(), poolConfig.getIdleTime(),
        poolConfig.getTimeUnit(), threadFactory, queueSize, rejectedExecutionHandler);
  }

  public static ThreadPoolExecutor create(
      int corePoolSize, int maxPoolSize, long idleTime, TimeUnit unit, ThreadFactory threadFactory) {
    return create(corePoolSize, maxPoolSize, idleTime, unit, threadFactory, -1, new ForceQueuePolicy());
  }

  public static ThreadPoolExecutor create(int corePoolSize, int maxPoolSize, long idleTime, TimeUnit unit,
      ThreadFactory threadFactory, int queueSize, RejectedExecutionHandler rejectedExecutionHandler) {
    ScalingQueue<Runnable> queue = queueSize < 0 ? new ScalingQueue<>() : new ScalingQueue<>(queueSize);
    ThreadPoolExecutor executor =
        new ScalingThreadPoolExecutor(corePoolSize, maxPoolSize, idleTime, unit, queue, threadFactory);
    executor.setRejectedExecutionHandler(rejectedExecutionHandler);
    queue.setThreadPoolExecutor(executor);
    return executor;
  }

  public static ExecutorService getInstrumentedExecutorService(int corePoolSize, int maxPoolSize, long idleTime,
      TimeUnit unit, String threadName, MetricRegistry metricRegistry) {
    return new InstrumentedExecutorService(create(corePoolSize, maxPoolSize, idleTime, unit,
                                               new ThreadFactoryBuilder().setNameFormat(threadName + "-%d").build()),
        metricRegistry, threadName);
  }

  public static ExecutorService getInstrumentedExecutorService(
      ThreadPoolConfig poolConfig, String threadName, MetricRegistry metricRegistry) {
    return new InstrumentedExecutorService(
        create(poolConfig, new ThreadFactoryBuilder().setNameFormat(threadName + "-%d").build()), metricRegistry,
        threadName);
  }

  public static ExecutorService getInstrumentedManagedExecutorService(
      ThreadPoolConfig poolConfig, String threadName, MetricRegistry metricRegistry) {
    return new InstrumentedExecutorService(
        new ManagedExecutorService(
            create(poolConfig.getCorePoolSize(), poolConfig.getMaxPoolSize(), poolConfig.getIdleTime(),
                poolConfig.getTimeUnit(), new ThreadFactoryBuilder().setNameFormat(threadName + "-%d").build())),
        metricRegistry, threadName);
  }

  public static ExecutorService getInstrumentedManagedExecutorService(ThreadPoolConfig poolConfig, int queueSize,
      RejectedExecutionHandler rejectedExecutionHandler, String threadName, MetricRegistry metricRegistry) {
    return new InstrumentedExecutorService(
        new ManagedExecutorService(create(poolConfig, queueSize,
            new ThreadFactoryBuilder().setNameFormat(threadName + "-%d").build(), rejectedExecutionHandler)),
        metricRegistry, threadName);
  }

  public static ScheduledExecutorService getInstrumentedManagedScheduledExecutorService(
      String threadName, MetricRegistry metricRegistry) {
    return new InstrumentedScheduledExecutorService(
        new ManagedScheduledExecutorService(threadName + "-%d"), metricRegistry, threadName);
  }

  public static ScheduledExecutorService getInstrumentedScheduledExecutorService(
      String threadName, MetricRegistry metricRegistry) {
    return new InstrumentedScheduledExecutorService(
        new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat(threadName + "-%d").build()),
        metricRegistry, threadName);
  }

  public static ScheduledExecutorService getInstrumentedScheduledExecutorService(
      String threadName, int corePoolSize, MetricRegistry metricRegistry) {
    return new InstrumentedScheduledExecutorService(
        new ScheduledThreadPoolExecutor(
            corePoolSize, new ThreadFactoryBuilder().setNameFormat(threadName + "-%d").build()),
        metricRegistry, threadName);
  }

  /**
   * Execute.
   *
   * @param task  the task
   * @param delay the delay
   */
  public static void execute(Runnable task, int delay) {
    execute(new Delayed(task, delay));
  }

  /**
   * Execute.
   *
   * @param task the task
   */
  public static void execute(Runnable task) {
    commonPool.execute(task);
  }

  /**
   * Submit.
   *
   * @param <T>  the generic type
   * @param task the task
   * @return the future
   */
  public static <T> Future<T> submit(Callable<T> task) {
    return commonPool.submit(task);
  }

  /**
   * Shutdown.
   */
  public static void shutdown() {
    commonPool.shutdown();
  }

  /**
   * Shutdown now.
   */
  public static void shutdownNow() {
    commonPool.shutdownNow();
  }

  /**
   * Is idle boolean.
   *
   * @return the boolean
   */
  public static boolean isIdle() {
    return commonPool.getActiveCount() == 0;
  }

  private static class Delayed implements Runnable {
    private Runnable runnable;
    private int delay;

    /**
     * Instantiates a new Delayed.
     *
     * @param runnable the runnable
     * @param delay    the delay
     */
    Delayed(Runnable runnable, int delay) {
      this.runnable = runnable;
      this.delay = delay;
    }

    @Override
    public void run() {
      quietSleep(Duration.ofMillis(delay));
      runnable.run();
    }
  }
}
