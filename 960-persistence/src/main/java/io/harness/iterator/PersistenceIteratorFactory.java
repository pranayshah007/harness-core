/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.iterator;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.iterator.PersistenceIterator.ProcessMode.LOOP;
import static io.harness.iterator.PersistenceIterator.ProcessMode.PUMP;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.IRREGULAR;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.IRREGULAR_SKIP_MISSED;

import io.harness.annotations.dev.OwnedBy;
import io.harness.config.WorkersConfiguration;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.MongoPersistenceIteratorBuilder;
import io.harness.mongo.iterator.filter.FilterExpander;

import com.codahale.metrics.InstrumentedExecutorService;
import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@Slf4j
public final class PersistenceIteratorFactory {
  private static final SecureRandom random = new SecureRandom();

  @Inject Injector injector;

  @Inject WorkersConfiguration workersConfiguration;
  @Inject HarnessMetricRegistry harnessMetricRegistry;

  public <T extends PersistentIterable, F extends FilterExpander> PersistenceIterator createIterator(
      Class<?> cls, MongoPersistenceIteratorBuilder<T, F> builder) {
    if (!workersConfiguration.confirmWorkerIsActive(cls)) {
      log.info("Worker {} is disabled in this setup", cls.getName());
      return null;
    }

    log.info("Worker {} is enabled in this setup", cls.getName());
    MongoPersistenceIterator<T, F> iterator = builder.build();
    injector.injectMembers(iterator);
    return iterator;
  }

  @Value
  @Builder
  public static class PumpExecutorOptions {
    private String name;
    private int poolSize;
    private Duration interval;
  }

  private <T extends PersistentIterable, F extends FilterExpander> PersistenceIterator<T>
  createIteratorWithDedicatedThreadPool(PersistenceIterator.ProcessMode processMode, PumpExecutorOptions options,
      Class<?> cls, MongoPersistenceIteratorBuilder<T, F> builder) {
    if (!workersConfiguration.confirmWorkerIsActive(cls)) {
      log.info("Worker {} is disabled in this setup", cls.getName());
      return null;
    }

    String iteratorName = "Iterator-" + options.getName();
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
        options.getPoolSize(), new ThreadFactoryBuilder().setNameFormat(iteratorName).build());
    log.info("Worker {} is enabled in this setup", cls.getName());

    MetricRegistry metricRegistry = harnessMetricRegistry.getThreadPoolMetricRegistry();
    InstrumentedExecutorService instrumentedExecutorService =
        new InstrumentedExecutorService(executor, metricRegistry, iteratorName);

    MongoPersistenceIterator<T, F> iterator = builder.mode(processMode)
                                                  .executorService(instrumentedExecutorService)
                                                  .semaphore(new Semaphore(options.getPoolSize()))
                                                  .iteratorName(options.getName())
                                                  .build();
    injector.injectMembers(iterator);
    long millis = options.interval.toMillis();
    executor.scheduleAtFixedRate(iterator::process, random.nextInt((int) millis), millis, TimeUnit.MILLISECONDS);

    if (iterator.getSchedulingType() == IRREGULAR || iterator.getSchedulingType() == IRREGULAR_SKIP_MISSED) {
      executor.schedule(iterator::recoverAfterPause, random.nextInt((int) millis), TimeUnit.MILLISECONDS);
    }

    return iterator;
  }

  // TODO (prashant) : this method looks wrong for loop iterators, scheduled at fixed rate do not make sense
  // Investigate more when time permits
  public <T extends PersistentIterable, F extends FilterExpander> PersistenceIterator<T>
  createLoopIteratorWithDedicatedThreadPool(
      PumpExecutorOptions options, Class<?> cls, MongoPersistenceIteratorBuilder<T, F> builder) {
    return createIteratorWithDedicatedThreadPool(LOOP, options, cls, builder);
  }

  public <T extends PersistentIterable, F extends FilterExpander> PersistenceIterator<T>
  createPumpIteratorWithDedicatedThreadPool(
      PumpExecutorOptions options, Class<?> cls, MongoPersistenceIteratorBuilder<T, F> builder) {
    return createIteratorWithDedicatedThreadPool(PUMP, options, cls, builder);
  }
}
