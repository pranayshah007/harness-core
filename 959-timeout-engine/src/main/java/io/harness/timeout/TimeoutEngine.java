/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.timeout;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.iterator.PersistenceIterator.ProcessMode.LOOP;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorLoopModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceRequiredProvider;
import io.harness.registries.timeout.TimeoutRegistry;
import io.harness.repositories.TimeoutInstanceRepository;
import io.harness.threading.ThreadPool;
import io.harness.timeout.TimeoutInstance.TimeoutInstanceKeys;
import io.harness.timeout.contracts.Dimension;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutParameters;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class TimeoutEngine extends IteratorLoopModeHandler implements Handler<TimeoutInstance> {
  private static final Duration MAX_CALLBACK_PROCESSING_TIME = Duration.ofMinutes(1);
  private static final Integer MAX_BATCH_SIZE = 500;

  @Inject private TimeoutInstanceRepository timeoutInstanceRepository;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private Injector injector;
  @Inject private TimeoutRegistry timeoutRegistry;

  public TimeoutInstance registerTimeout(@NotNull Dimension dimension, @NotNull TimeoutParameters timeoutParameters,
      @NotNull TimeoutCallback timeoutCallback) {
    TimeoutTrackerFactory timeoutTrackerFactory = timeoutRegistry.obtain(dimension);
    TimeoutTracker timeoutTracker = timeoutTrackerFactory.create(timeoutParameters);
    return registerTimeout(timeoutTracker, timeoutCallback);
  }

  public TimeoutInstance registerAbsoluteTimeout(Duration timeoutDuration, TimeoutCallback timeoutCallback) {
    return registerTimeout(AbsoluteTimeoutTrackerFactory.DIMENSION,
        AbsoluteTimeoutParameters.builder().timeoutMillis(timeoutDuration.toMillis()).build(), timeoutCallback);
  }

  @VisibleForTesting
  TimeoutInstance registerTimeout(@NotNull TimeoutTracker timeoutTracker, @NotNull TimeoutCallback timeoutCallback) {
    TimeoutInstance timeoutInstance =
        TimeoutInstance.builder().uuid(generateUuid()).tracker(timeoutTracker).callback(timeoutCallback).build();
    timeoutInstance.resetNextIteration();
    TimeoutInstance savedTimeoutInstance = timeoutInstanceRepository.save(timeoutInstance);
    log.info(format("Registered timeout with uuid: %s, currentTime: %d, expiryTime: %d, diff: %d",
        timeoutInstance.getUuid(), System.currentTimeMillis(), timeoutInstance.getNextIteration(),
        timeoutInstance.getNextIteration() - System.currentTimeMillis()));
    if (iterator != null) {
      iterator.wakeup();
    }
    return savedTimeoutInstance;
  }

  public void deleteTimeouts(List<String> timeoutInstanceIds) {
    if (EmptyPredicate.isEmpty(timeoutInstanceIds)) {
      return;
    }
    List<List<String>> partition = Lists.partition(timeoutInstanceIds, MAX_BATCH_SIZE);
    for (List<String> batchTimeInstanceIds : partition) {
      timeoutInstanceRepository.deleteByUuidIn(batchTimeInstanceIds);
    }
  }

  public void deleteTimeout(@NonNull String timeoutInstanceId) {
    // equal operator is better than in operator
    timeoutInstanceRepository.deleteById(timeoutInstanceId);
  }

  public void onEvent(List<String> timeoutInstanceIds, TimeoutEvent event) {
    if (EmptyPredicate.isEmpty(timeoutInstanceIds)) {
      return;
    }

    for (TimeoutInstance timeoutInstance : timeoutInstanceRepository.findAllById(timeoutInstanceIds)) {
      if (timeoutInstance.tracker.onEvent(event)) {
        timeoutInstance.resetNextIteration();
        timeoutInstanceRepository.save(timeoutInstance);
        if (iterator != null) {
          iterator.wakeup();
        }
      }
    }
  }

  @Override
  public void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<TimeoutInstance, SpringFilterExpander>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       TimeoutEngine.class,
                       MongoPersistenceIterator.<TimeoutInstance, SpringFilterExpander>builder()
                           .clazz(TimeoutInstance.class)
                           .fieldName(TimeoutInstanceKeys.nextIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ofSeconds(10))
                           .acceptableExecutionTime(ofSeconds(10))
                           .handler(this)
                           .persistenceProvider(new SpringPersistenceRequiredProvider<>(mongoTemplate)));
  }

  @Override
  public void createAndStartIterator(
      PersistenceIteratorFactory.PumpExecutorOptions executorOptions, Duration targetInterval) {
    executor =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("timeout-engine-iterator").build());
    ExecutorService executorService = ThreadPool.create(executorOptions.getPoolSize(), executorOptions.getPoolSize(),
        30, TimeUnit.SECONDS, new ThreadFactoryBuilder().setNameFormat("TimeoutEngineHandler-%d").build());
    iterator =
        (MongoPersistenceIterator<TimeoutInstance, SpringFilterExpander>) persistenceIteratorFactory.createIterator(
            TimeoutEngine.class,
            MongoPersistenceIterator.<TimeoutInstance, SpringFilterExpander>builder()
                .mode(LOOP)
                .iteratorName(iteratorName)
                .clazz(TimeoutInstance.class)
                .fieldName(TimeoutInstanceKeys.nextIteration)
                .targetInterval(targetInterval)
                .acceptableNoAlertDelay(ofSeconds(10))
                .acceptableExecutionTime(ofSeconds(10))
                .executorService(executorService)
                .semaphore(new Semaphore(executorOptions.getPoolSize()))
                .handler(this)
                .persistenceProvider(new SpringPersistenceRequiredProvider<>(mongoTemplate))
                .schedulingType(REGULAR));
    executor.submit(() -> iterator.process());
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "TimeoutEngine";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(TimeoutInstance timeoutInstance) {
    try (TimeoutInstanceLogContext ignore0 = new TimeoutInstanceLogContext(timeoutInstance.getUuid(), OVERRIDE_ERROR)) {
      final long now = System.currentTimeMillis();
      log.info("TimeoutInstance handle started");

      TimeoutCallback callback = timeoutInstance.getCallback();
      injector.injectMembers(callback);
      try {
        callback.onTimeout(timeoutInstance);
        log.info("TimeoutInstance callback finished");
      } catch (Exception ex) {
        // TODO(gpahal): What to do in case callback throws an exception. Should we retry?
        log.error("TimeoutInstance callback failed", ex);
      }

      try {
        timeoutInstanceRepository.deleteById(timeoutInstance.getUuid());
      } catch (Exception ex) {
        log.error("TimeoutInstance delete failed", ex);
      }

      final long passed = System.currentTimeMillis() - now;
      if (passed > MAX_CALLBACK_PROCESSING_TIME.toMillis()) {
        log.error(
            "TimeoutInstanceHandler: It took more than {} ms before we processed the callback. THIS IS VERY BAD!!!",
            MAX_CALLBACK_PROCESSING_TIME.toMillis());
      }
    }
  }
}
