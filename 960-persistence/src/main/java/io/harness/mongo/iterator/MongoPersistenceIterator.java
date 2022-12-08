/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mongo.iterator;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.govern.Switch.unhandled;
import static io.harness.iterator.PersistenceIterator.ProcessMode.PUMP;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.metrics.impl.PersistenceMetricsServiceImpl.ITERATOR_DELAY;
import static io.harness.metrics.impl.PersistenceMetricsServiceImpl.ITERATOR_ERROR;
import static io.harness.metrics.impl.PersistenceMetricsServiceImpl.ITERATOR_PROCESSING_TIME;
import static io.harness.metrics.impl.PersistenceMetricsServiceImpl.ITERATOR_WORKING_ON_ENTITY;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.IRREGULAR_SKIP_MISSED;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ZERO;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistenceIterator;
import io.harness.iterator.PersistentIrregularIterable;
import io.harness.iterator.PersistentIterable;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.maintenance.MaintenanceController;
import io.harness.metrics.impl.PersistenceMetricsServiceImpl;
import io.harness.mongo.DelayLogContext;
import io.harness.mongo.EntityLogContext;
import io.harness.mongo.EntityProcessController;
import io.harness.mongo.ProcessTimeLogContext;
import io.harness.mongo.iterator.filter.FilterExpander;
import io.harness.mongo.iterator.provider.PersistenceProvider;
import io.harness.queue.QueueController;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.MorphiaIterator;

@OwnedBy(HarnessTeam.PL)
@Builder
@Slf4j
public class MongoPersistenceIterator<T extends PersistentIterable, F extends FilterExpander>
    implements PersistenceIterator<T> {
  private static final Duration QUERY_TIME = ofMillis(200);
  private static final int BATCH_SIZE_MULTIPLY_FACTOR = 2; // The factor by how much the batchSize should be increased
  private static final int WORKER_JOB_WAIT_DURATION_IN_MILLIS = 10000; // Wait duration when worker jobQ is full

  @Inject private final QueueController queueController;
  @Inject private PersistenceMetricsServiceImpl iteratorMetricsService;

  @Getter private final PersistenceProvider<T, F> persistenceProvider;
  private F filterExpander;
  private ProcessMode mode;
  private Class<T> clazz;
  private String fieldName;
  private Duration targetInterval;
  private Duration maximumDelayForCheck;
  private Duration acceptableNoAlertDelay;
  private Duration acceptableExecutionTime;
  private Duration throttleInterval;
  private Handler<T> handler;
  @Getter private ExecutorService executorService;
  @Getter private ScheduledThreadPoolExecutor threadPoolExecutor;
  private Semaphore semaphore;
  private boolean redistribute;
  private EntityProcessController<T> entityProcessController;
  @Getter private SchedulingType schedulingType;
  private String iteratorName;
  private boolean unsorted;
  private int replicaCount;
  private int shardId;

  public interface Handler<T> {
    void handle(T entity);
  }

  public enum SchedulingType { REGULAR, IRREGULAR, IRREGULAR_SKIP_MISSED }

  @Override
  public synchronized void wakeup() {
    switch (mode) {
      case PUMP:
        executorService.submit(this::process);
        break;
      case LOOP:
        notifyAll();
        break;
      default:
        unhandled(mode);
    }
  }

  @Override
  // The theory is that ERROR type exception are unrecoverable, that is not exactly true.
  @SuppressWarnings({"PMD", "squid:S1181"})
  public void process() {
    long movingAverage = 0;
    long previous = 0;
    while (true) {
      if (!shouldProcess()) {
        if (mode == PUMP) {
          return;
        }
        sleep(ofSeconds(1));
        continue;
      }
      try {
        // make sure we did not hit the limit
        semaphore.acquire();

        long base = currentTimeMillis();
        long throttled = base + (throttleInterval == null ? 0 : throttleInterval.toMillis());
        // redistribution make sense only for regular iteration
        if (redistribute && schedulingType == REGULAR && previous != 0) {
          base = movingAvg(previous + movingAverage, base);
          movingAverage = movingAvg(movingAverage, base - previous);
        }

        previous = base;

        T entity = null;
        try {
          entity = persistenceProvider.obtainNextInstance(
              base, throttled, clazz, fieldName, schedulingType, targetInterval, filterExpander, unsorted);
        } finally {
          semaphore.release();
        }

        if (entity != null) {
          // Make sure that if the object is updated we reset the scheduler for it
          if (schedulingType != REGULAR) {
            Long nextIteration = entity.obtainNextIteration(fieldName);

            List<Long> nextIterations =
                ((PersistentIrregularIterable) entity)
                    .recalculateNextIterations(fieldName, schedulingType == IRREGULAR_SKIP_MISSED, throttled);
            if (isNotEmpty(nextIterations)) {
              persistenceProvider.updateEntityField(entity, nextIterations, clazz, fieldName);
            }

            if (nextIteration == null) {
              continue;
            }
          }

          if (entityProcessController != null && !entityProcessController.shouldProcessEntity(entity)) {
            continue;
          }

          T finalEntity = entity;
          synchronized (finalEntity) {
            try {
              executorService.submit(() -> processEntity(finalEntity));
              // it might take some time until the submitted task is actually triggered.
              // lets wait for awhile until for this to happen
              finalEntity.wait(WORKER_JOB_WAIT_DURATION_IN_MILLIS);
            } catch (RejectedExecutionException e) {
              log.info("The executor service has been shutdown for entity {}", finalEntity);
            }
          }
          continue;
        }

        if (mode == PUMP) {
          break;
        }

        T next = persistenceProvider.findInstance(clazz, fieldName, filterExpander);

        long sleepMillis = calculateSleepDuration(next).toMillis();
        // Do not sleep with 0, it is actually infinite sleep
        if (sleepMillis > 0) {
          // set previous to 0 to reset base after notify() is called
          previous = 0;
          synchronized (this) {
            wait(sleepMillis);
          }
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        break;
      } catch (Throwable exception) {
        log.error("Exception occurred while processing iterator", exception);
        iteratorMetricsService.recordIteratorMetrics(iteratorName, ITERATOR_ERROR);
        sleep(ofSeconds(1));
      }
    }
  }

  public void recoverAfterPause() {
    persistenceProvider.recoverAfterPause(clazz, fieldName);
  }

  public Duration calculateSleepDuration(T next) {
    if (next == null) {
      return maximumDelayForCheck == null ? targetInterval : maximumDelayForCheck;
    }

    Long nextIteration = next.obtainNextIteration(fieldName);
    if (nextIteration == null) {
      return ZERO;
    }

    Duration nextEntity = ofMillis(nextIteration - currentTimeMillis());
    if (maximumDelayForCheck == null || nextEntity.compareTo(maximumDelayForCheck) < 0) {
      return nextEntity;
    }

    return maximumDelayForCheck;
  }

  /**
   * Process method for shard mode iterator.
   *
   * 1. Determine the total documents to be processed by this shard by fetching
   *    the total docs count for the collection and dividing by nos of shards.
   * 2. Find the start point (doc) for this shard by multiplying the total docs
   *    for each shard and the shardId.
   * 3. Initialize the currentEntity to point to the first doc that this shard
   *    should process by fetching the first doc from the collection.
   * 4. Get the remaining documents that are greater than the first doc in a
   *    single batch from Mongo.
   * 5. Submit each doc that was fetched to the worker Q and wait until the
   *    doc is picked from the Q by a worker.
   */
  public void shardProcess() {
    if (!shouldProcess()) {
      return;
    }

    try {
      // make sure we did not hit the limit
      semaphore.acquire();

      // Get the total count of documents this shard has to process.
      long totalTimeStart = currentTimeMillis();
      long startTime = currentTimeMillis();
      int shardTotalDocsToProcess =
          (int) Math.ceil((double) persistenceProvider.getDocumentsCount(clazz) / replicaCount);
      int totalDocs = shardTotalDocsToProcess;
      int start = shardTotalDocsToProcess * shardId;
      T currentEntity = persistenceProvider.getOneDocumentBySkip(clazz, filterExpander, start);
      long processTime = currentTimeMillis() - startTime;
      log.info("Shard Iterator Mode - shardTotalDocsToProcess {}, start {}", shardTotalDocsToProcess, start);
      log.info("The first entity being processed {} and time to fetch from Mongo {} ms", currentEntity.getUuid(),
          processTime);
      submitEntityForProcessing(currentEntity);
      shardTotalDocsToProcess--;

      /* After getting the start entity now just keep getting batch of entities
         where the batch is determined by the number of available threads in the
         executor and Q size.
       */

      while (true) {
        // If number of documents left to process is <= 0 then break
        // Note with this way of checking it would be possible that multiple
        // shards might process some overlapping docs but that should be fine.
        if (shardTotalDocsToProcess <= 0) {
          log.info("The last entity that was processed {} ", currentEntity.getUuid());
          break;
        }

        int limit =
            Math.min(BATCH_SIZE_MULTIPLY_FACTOR * (threadPoolExecutor.getCorePoolSize() - 1), shardTotalDocsToProcess);
        startTime = currentTimeMillis();
        MorphiaIterator<T, T> docItr =
            persistenceProvider.getDocumentsGreaterThanID(clazz, filterExpander, currentEntity.getUuid(), limit);
        processTime = currentTimeMillis() - startTime;
        log.info("Time to fetch {} docs from Mongo {} ms", limit, processTime);

        if (!docItr.hasNext()) {
          // There are no more documents to process so break
          log.info("The last entity that was processed {} ", currentEntity.getUuid());
          break;
        }

        while (docItr.hasNext()) {
          currentEntity = docItr.next();
          submitEntityForProcessing(currentEntity);
          shardTotalDocsToProcess--;
        }
      }

      log.info("Total time to process {} docs is {} ms", totalDocs, currentTimeMillis() - totalTimeStart);

    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    } catch (Throwable exception) {
      log.error("Exception occurred while processing iterator", exception);
      iteratorMetricsService.recordIteratorMetrics(iteratorName, ITERATOR_ERROR);
      sleep(ofSeconds(1));
    } finally {
      semaphore.release();
    }
  }

  // We are aware that the entity will be different object every time the method is
  // called. This is exactly what we want.
  // The theory is that ERROR type exception are unrecoverable, that is not exactly true.
  @SuppressWarnings({"squid:S2445", "PMD", "squid:S1181"})
  @VisibleForTesting
  public void processEntity(T entity) {
    try (EntityLogContext ignore = new EntityLogContext(entity, OVERRIDE_ERROR)) {
      try {
        semaphore.acquire();
      } catch (InterruptedException e) {
        log.error("Working on entity was interrupted", e);
        iteratorMetricsService.recordIteratorMetrics(iteratorName, ITERATOR_ERROR);
        Thread.currentThread().interrupt();
        return;
      }
      long startTime = currentTimeMillis();

      try {
        synchronized (entity) {
          entity.notify();
        }
        Long nextIteration = entity.obtainNextIteration(fieldName);
        if (schedulingType == REGULAR) {
          ((PersistentRegularIterable) entity).updateNextIteration(fieldName, 0L);
        }

        long delay = nextIteration == null || nextIteration == 0 ? 0 : startTime - nextIteration;
        try (DelayLogContext ignore2 = new DelayLogContext(delay, OVERRIDE_ERROR)) {
          log.debug("Working on entity");
          iteratorMetricsService.recordIteratorMetrics(iteratorName, ITERATOR_WORKING_ON_ENTITY);
          iteratorMetricsService.recordIteratorMetricsWithDuration(iteratorName, ofMillis(delay), ITERATOR_DELAY);

          if (delay >= acceptableNoAlertDelay.toMillis()) {
            log.debug(
                "Working on entity but the delay is more than the acceptable {}", acceptableNoAlertDelay.toMillis());
          }
        }

        try {
          handler.handle(entity);
        } catch (RuntimeException exception) {
          log.error("Catch and handle all exceptions in the entity handler", exception);
          iteratorMetricsService.recordIteratorMetrics(iteratorName, ITERATOR_ERROR);
        }
      } catch (Throwable exception) {
        log.error("Exception while processing entity", exception);
        iteratorMetricsService.recordIteratorMetrics(iteratorName, ITERATOR_ERROR);
      } finally {
        semaphore.release();

        long processTime = currentTimeMillis() - startTime;
        log.debug("Done with entity");
        iteratorMetricsService.recordIteratorMetricsWithDuration(
            iteratorName, ofMillis(processTime), ITERATOR_PROCESSING_TIME);

        try (ProcessTimeLogContext ignore2 = new ProcessTimeLogContext(processTime, OVERRIDE_ERROR)) {
          if (acceptableExecutionTime != null && processTime > acceptableExecutionTime.toMillis()) {
            log.debug("Done with entity but took too long acceptable {}", acceptableExecutionTime.toMillis());
          }
        } catch (Throwable exception) {
          log.error("Exception while recording the processing of entity", exception);
          iteratorMetricsService.recordIteratorMetrics(iteratorName, ITERATOR_ERROR);
        }
      }
    }
  }

  /**
   * Method to process an entity without updating any iteration fields.
   *
   * @param entity - Mongo doc that the worker thread should process.
   */
  private void processShardEntity(T entity) {
    try (EntityLogContext ignore = new EntityLogContext(entity, OVERRIDE_ERROR)) {
      try {
        semaphore.acquire();
      } catch (InterruptedException e) {
        log.error("Working on entity was interrupted", e);
        iteratorMetricsService.recordIteratorMetrics(iteratorName, ITERATOR_ERROR);
        Thread.currentThread().interrupt();
        return;
      }
      long startTime = currentTimeMillis();

      try {
        handler.handle(entity);
      } catch (RuntimeException exception) {
        log.error("Catch and handle all exceptions in the entity handler", exception);
        iteratorMetricsService.recordIteratorMetrics(iteratorName, ITERATOR_ERROR);
      } finally {
        semaphore.release();

        long processTime = currentTimeMillis() - startTime;
        log.debug("Done with entity");
        iteratorMetricsService.recordIteratorMetricsWithDuration(
            iteratorName, ofMillis(processTime), ITERATOR_PROCESSING_TIME);

        try (ProcessTimeLogContext ignore2 = new ProcessTimeLogContext(processTime, OVERRIDE_ERROR)) {
          if (acceptableExecutionTime != null && processTime > acceptableExecutionTime.toMillis()) {
            log.debug("Done with entity but took too long acceptable {}", acceptableExecutionTime.toMillis());
          }
        } catch (Throwable exception) {
          log.error("Exception while recording the processing of entity", exception);
          iteratorMetricsService.recordIteratorMetrics(iteratorName, ITERATOR_ERROR);
        }
      }
    }
  }

  /**
   * Method to submit a Mongo doc to the worker Q for processing.
   *
   * @param entity - Mongo doc that has to be processed.
   */
  private void submitEntityForProcessing(T entity) {
    if (entity == null) {
      return;
    }

    if (entityProcessController != null && !entityProcessController.shouldProcessEntity(entity)) {
      return;
    }

    try {
      threadPoolExecutor.submit(() -> processShardEntity(entity));
      // If the jobQ is full then wait for some-time.
      if (threadPoolExecutor.getQueue().size() >= (threadPoolExecutor.getCorePoolSize() * BATCH_SIZE_MULTIPLY_FACTOR)) {
        log.warn("The jobQ for the worker threads has exceeding - pausing for 10 secs");
        sleep(ofMillis(WORKER_JOB_WAIT_DURATION_IN_MILLIS));
      }
    } catch (RejectedExecutionException e) {
      log.info("The executor service has been shutdown for iterator {} - exp {}", iteratorName, e);
    }
  }

  private long movingAvg(long current, long sample) {
    return (15 * current + sample) / 16;
  }

  private boolean shouldProcess() {
    return !MaintenanceController.getMaintenanceFlag() && queueController.isPrimary();
  }
}
