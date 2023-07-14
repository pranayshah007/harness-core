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
import static io.harness.metrics.impl.PersistenceMetricsServiceImpl.ITERATOR_REDIS_LOCK_ACQUIRE_FAIL;
import static io.harness.metrics.impl.PersistenceMetricsServiceImpl.ITERATOR_WORKING_ON_ENTITY;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.IRREGULAR_SKIP_MISSED;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;
import static io.harness.threading.Morpheus.sleep;

import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iterator.PersistenceIterator;
import io.harness.iterator.PersistentIrregularIterable;
import io.harness.iterator.PersistentIterable;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.logging.DelayLogContext;
import io.harness.logging.ProcessTimeLogContext;
import io.harness.maintenance.MaintenanceController;
import io.harness.metrics.impl.PersistenceMetricsServiceImpl;
import io.harness.mongo.EntityLogContext;
import io.harness.mongo.EntityProcessController;
import io.harness.mongo.iterator.filter.FilterExpander;
import io.harness.mongo.iterator.provider.PersistenceProvider;
import io.harness.queue.QueueController;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.client.RedisException;

@OwnedBy(HarnessTeam.PL)
@Builder
@Slf4j
public class MongoPersistenceIterator<T extends PersistentIterable, F extends FilterExpander>
    implements PersistenceIterator<T> {
  private static final Duration QUERY_TIME = ofMillis(200);
  private static final int SIMPLE_MOVING_AVG_MULTIPLIER = 15; // The multiplier to be used for SMA
  private static final int SIMPLE_MOVING_AVG_DIVISOR = 16; // The divisor to be used for SMA
  private static final String SEMAPHORE_ACQUIRE_ERROR = "Working on entity was interrupted";
  private static final int LOCK_WAIT_TIMEOUT_SECONDS =
      5; // The lockWaitTimeout is the duration to wait to acquire a lock
  private static final int BATCH_SIZE_MULTIPLY_FACTOR = 2; // The factor by how much the batchSize should be increased
  private static final int REDIS_BATCH_PAUSE_DURATION = 5; // The duration by which to pause if worker JobQ is full

  @Inject private final QueueController queueController;
  @Inject private PersistenceMetricsServiceImpl iteratorMetricsService;

  public enum SchedulingType { REGULAR, IRREGULAR, IRREGULAR_SKIP_MISSED }

  @Getter private final PersistenceProvider<T, F> persistenceProvider;
  private F filterExpander;
  @Getter private ProcessMode mode;
  private Class<T> clazz;
  private String fieldName;
  private Duration targetInterval;
  private Duration maximumDelayForCheck;
  private Duration acceptableNoAlertDelay;
  private Duration acceptableExecutionTime;
  private Duration threadPoolIntervalInSeconds;
  private Duration throttleInterval;
  private int redisModeBatchSize;
  private int redisLockTimeout;
  private Handler<T> handler;
  @Getter private ExecutorService executorService;
  @Getter private ScheduledThreadPoolExecutor workerThreadPoolExecutor;
  private Semaphore semaphore;
  private boolean redistribute;
  private EntityProcessController<T> entityProcessController;
  @Getter private SchedulingType schedulingType;
  private String iteratorName;
  private boolean unsorted;

  private boolean isDelegateTaskMigrationEnabled;
  private PersistentLocker persistentLocker;

  public interface Handler<T> {
    void handle(T entity);
  }

  @Override
  public synchronized void wakeup() {
    switch (mode) {
      case PUMP:
        executorService.submit(this::process);
        break;
      case LOOP:
      case REDIS_BATCH:
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
          entity = persistenceProvider.obtainNextInstance(base, throttled, clazz, fieldName, schedulingType,
              targetInterval, filterExpander, unsorted, isDelegateTaskMigrationEnabled);
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
              finalEntity.wait(10000);
            } catch (RejectedExecutionException e) {
              log.info("The executor service has been shutdown - received exception {} ", e);
            }
          }
          continue;
        }

        if (mode == PUMP) {
          break;
        }

        T next = persistenceProvider.findInstance(clazz, fieldName, filterExpander, isDelegateTaskMigrationEnabled);

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
      return maximumDelayForCheck == null ? targetInterval : maximumDelayForCheck;
    }

    Duration nextEntity = ofMillis(nextIteration - currentTimeMillis());
    if (maximumDelayForCheck == null || nextEntity.compareTo(maximumDelayForCheck) < 0) {
      return nextEntity;
    }

    return maximumDelayForCheck;
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
        log.error(SEMAPHORE_ACQUIRE_ERROR, e);
        iteratorMetricsService.recordIteratorMetrics(iteratorName, ITERATOR_ERROR);
        Thread.currentThread().interrupt();
        return;
      }
      long startTime = currentTimeMillis();

      try {
        synchronized (entity) {
          entity.notify();
        }

        // Update the iterator metrics for ITERATOR_WORKING_ON_ENTITY and ITERATOR_DELAY
        updateIteratorMetricsNosOfEntityAndDelay(entity);
        if (schedulingType == REGULAR) {
          ((PersistentRegularIterable) entity).updateNextIteration(fieldName, 0L);
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

        // Update the iterator metric for ITERATOR_PROCESSING_TIME
        updateIteratorMetricProcessingTime(processTime);
      }
    }
  }

  /**
   * Process method for Redis Batch mode iterator.
   *
   *  1. Update the batch-size by finding a limit which will take into account the number
   *     of docs still not processed in the jobQ which ensures that the Q doesn't overflow.
   *  2. If the batch-size limit is not positive then pause for a while. This allows the
   *     workers to process the remaining docs in the jobQ and doesn't cause Q overflow.
   *  3. Try to acquire a Redis distributed lock so that this process gets an exclusive
   *     access to a batch of Mongo docs that it can update and work on.
   *  4. Once the Redis lock is acquired fetch a batch of documents. The number of docs
   *     to fetch will be the batch-size limit value that was computed earlier.
   *  5. Iterator over the fetched docs and submit it to the workers jobQ without waiting
   *     to ensure that the Redis lock is released at the earliest.
   *  6. Collect the doc Ids of each doc that was submitted to the worker jobQ and update
   *     the nextIteration fields of these docs in bulk using Mongo's bulkWrite operation.
   *  7. Release the Redis distributed lock so that the other processes can work on another
   *     batch of Mongo docs.
   */
  public void redisBatchProcess() {
    long movingAverage = 0;
    long previous = 0;

    while (true) {
      // Check if iterators should run or not.
      if (!shouldProcess()) {
        sleep(ofSeconds(1));
        continue;
      }

      long base = currentTimeMillis();
      if (previous != 0) {
        base = movingAvg(previous + movingAverage, base);
        movingAverage = movingAvg(movingAverage, base - previous);
      }
      previous = base;

      // Compute a limit value that takes into account the number of unprocessed
      // docs in the jobQ to ensure that the Q doesn't overflow.
      int limit = Math.min(redisModeBatchSize, redisModeBatchSize - workerThreadPoolExecutor.getQueue().size());

      if (limit <= 0) {
        // The Queue is full, so try after sometime
        log.warn("The worker Q for {} iterator is full, pausing for 5 seconds", iteratorName);
        sleep(ofSeconds(REDIS_BATCH_PAUSE_DURATION));
        continue;
      }

      long totalTimeStart = currentTimeMillis();
      long startTime = currentTimeMillis();

      AcquiredLock acquiredLock = null;
      long processTime = 0;
      List<String> docIds = new ArrayList<>();
      try {
        // Acquire the distributed lock
        acquiredLock = acquireLock();

        processTime = currentTimeMillis() - startTime;
        log.debug("Redis Batch Iterator Mode - time to acquire Redis lock {}", processTime);

        startTime = currentTimeMillis();
        Iterator<T> docItr = persistenceProvider.obtainNextInstances(clazz, fieldName, filterExpander, limit);
        processTime = currentTimeMillis() - startTime;
        log.debug("Redis Batch Iterator Mode - time to acquire {} docs is {}", limit, processTime);

        // Iterate over the fetched documents - submit it to workers and prepare bulkWrite operations
        while (docItr.hasNext()) {
          T entity = docItr.next();
          submitEntityForProcessingWithoutWait(entity);
          docIds.add(entity.getUuid());
        }

        // Update the documents next iteration field
        updateDocumentNextIteration(docIds, base);

      } catch (Exception ex) {
        log.error("Received an exception in redisBatchProcess {} ", ex);
      } finally {
        // Release the distributed lock - acquiredLock cannot be null
        releaseLock(acquiredLock);

        processTime = currentTimeMillis() - totalTimeStart;
        log.debug("Redis Batch Iterator Mode - time to carryout the entire processing is {}", processTime);
      }

      // If there were no docs available then sleep for
      // the configured threadPool interval duration.
      if (docIds.isEmpty()) {
        synchronized (this) {
          try {
            wait(threadPoolIntervalInSeconds.toMillis());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      }
    }
  }

  /**
   * Method to submit an entity to the Worker JobQ.
   * @param entity - Mongo document that worker thread should process
   */
  public void submitEntityForProcessingWithoutWait(T entity) {
    if (entity == null) {
      return;
    }

    if (entityProcessController != null && !entityProcessController.shouldProcessEntity(entity)) {
      return;
    }

    T finalEntity = entity;
    synchronized (finalEntity) {
      try {
        // We won't wait for the threads to pick up the task.
        // The caller should be mindful of the threadPoolExecutor jobQ overflow.
        workerThreadPoolExecutor.submit(() -> processEntityWithoutWaitNotify(finalEntity));
      } catch (RejectedExecutionException e) {
        log.info("The executor service has been shutdown - received exception {} ", e);
      }
    }
  }

  private long movingAvg(long current, long sample) {
    return (SIMPLE_MOVING_AVG_MULTIPLIER * current + sample) / SIMPLE_MOVING_AVG_DIVISOR;
  }

  private boolean shouldProcess() {
    return !MaintenanceController.getMaintenanceFlag() && queueController.isPrimary();
  }

  /**
   * Method to process entity without synchronized wait notification.
   * @param entity - Mongo document that worker thread should process
   */
  private void processEntityWithoutWaitNotify(T entity) {
    try (EntityLogContext ignore = new EntityLogContext(entity, OVERRIDE_ERROR)) {
      try {
        semaphore.acquire();
      } catch (InterruptedException e) {
        log.error(SEMAPHORE_ACQUIRE_ERROR, e);
        iteratorMetricsService.recordIteratorMetrics(iteratorName, ITERATOR_ERROR);
        Thread.currentThread().interrupt();
        return;
      }

      // Update the iterator metrics for ITERATOR_WORKING_ON_ENTITY and ITERATOR_DELAY
      updateIteratorMetricsNosOfEntityAndDelay(entity);
      long startTime = currentTimeMillis();

      try {
        // Work on this entity.
        handler.handle(entity);
      } catch (Exception exception) {
        log.error("Catch and handle all exceptions in the entity handler", exception);
        iteratorMetricsService.recordIteratorMetrics(iteratorName, ITERATOR_ERROR);
      } finally {
        semaphore.release();

        long processTime = currentTimeMillis() - startTime;
        log.debug("Done with entity");

        // Update the iterator metric for ITERATOR_PROCESSING_TIME
        updateIteratorMetricProcessingTime(processTime);
      }
    }
  }

  /**
   * Method to acquire the lock and return it.
   * @return AcquiredLock
   */
  private AcquiredLock acquireLock() {
    String lockName = MongoPersistenceIterator.class.getName() + "-" + iteratorName;
    while (true) {
      // Hardcoding the lockTimeout and waitTimeout for the lock to 5 secs.
      // Note - The Redis distributed lock framework is supposed to return Null
      // if it was unable to acquire a lock and not throw an exception.
      try (AcquiredLock acquiredLock = persistentLocker.waitToAcquireLockOptional(
               lockName, ofSeconds(redisLockTimeout), ofSeconds(LOCK_WAIT_TIMEOUT_SECONDS))) {
        if (acquiredLock != null) {
          // Got the lock, proceed further.
          return acquiredLock;
        } else {
          log.debug("Failed to acquire distributed lock - attempting to reacquire after 5 secs");
          iteratorMetricsService.recordIteratorMetrics(iteratorName, ITERATOR_REDIS_LOCK_ACQUIRE_FAIL);
          sleep(ofSeconds(REDIS_BATCH_PAUSE_DURATION));
        }
      }
    }
  }

  /**
   * Method to release the lock.
   */
  private void releaseLock(AcquiredLock acquiredLock) {
    // Note - We might have to try acquiring the lock again before releasing it.
    // If the lock got acquired by some other process then it might cause undefined behaviour.
    // Will try handling it if the issue is observed since now the lock timeout is at 5 secs
    // which is a lot and trying to acquire the lock again will cause additional delays.
    try {
      acquiredLock.release();
    } catch (RedisException ex) {
      log.debug(" Redis Batch Iterator Mode - Received a RedisException while releasing the lock {}, stack trace {} ",
          ex, ex.getStackTrace());
    } catch (RuntimeException ex) {
      log.debug(" Redis Batch Iterator Mode - Received a RuntimeException while releasing the lock {}, stack trace {} ",
          ex, ex.getStackTrace());
    }
  }

  /**
   * Method to carryout bulk find and update operation.
   * @param docIds List of document Ids
   */
  private void updateDocumentNextIteration(List<String> docIds, long base) {
    // If the docIds list is empty then return
    if (docIds.isEmpty()) {
      return;
    }

    int size = docIds.size();
    try {
      long startTime = currentTimeMillis();
      BulkWriteOpsResults writeResults =
          persistenceProvider.bulkWriteDocumentsMatchingIds(clazz, docIds, fieldName, base, targetInterval);
      long processTime = currentTimeMillis() - startTime;
      log.debug(
          "Redis Batch Iterator Mode - time to carryout bulk write for {} docs is {}", docIds.size(), processTime);

      // Do not do any further time-consuming processing here because
      // the distributed lock has to be released in the finally block for safety.

      // Check if all documents were found
      if (size != writeResults.getMatchedCount()) {
        log.warn("All documents not found - exp {} found {} ", size, writeResults.getMatchedCount());
      }
      // Check if all the writes went through
      if (size != writeResults.getModifiedCount()) {
        log.warn("All updates for the field {} did not go through - exp {} modified {} ", fieldName, size,
            writeResults.getModifiedCount());
      }
    } catch (RuntimeException ex) {
      log.error("Failed to find and update documents due to exception {} ", ex);
    }
  }

  /**
   * Helper method to update the iterator metrics for -
   * 1. ITERATOR_WORKING_ON_ENTITY
   * 2. ITERATOR_DELAY
   *
   * @param entity the mongo doc or entity currently being procesed
   */
  private void updateIteratorMetricsNosOfEntityAndDelay(T entity) {
    long startTime = currentTimeMillis();
    Long nextIteration = entity.obtainNextIteration(fieldName);
    long delay = (nextIteration == null || nextIteration == 0) ? 0 : (startTime - nextIteration);

    try (DelayLogContext ignore2 = new DelayLogContext(delay, OVERRIDE_ERROR)) {
      iteratorMetricsService.recordIteratorMetrics(iteratorName, ITERATOR_WORKING_ON_ENTITY);
      iteratorMetricsService.recordIteratorMetricsWithDuration(iteratorName, ofMillis(delay), ITERATOR_DELAY);

      if (delay >= acceptableNoAlertDelay.toMillis()) {
        log.debug("Working on entity but the delay is more than the acceptable {}", acceptableNoAlertDelay.toMillis());
      }
    }
  }

  /**
   * Helper method to update the iterator metric for ITERATOR_PROCESSING_TIME
   *
   * @param processTime the time it took to process the mongo doc or entity
   */
  private void updateIteratorMetricProcessingTime(long processTime) {
    iteratorMetricsService.recordIteratorMetricsWithDuration(
        iteratorName, ofMillis(processTime), ITERATOR_PROCESSING_TIME);

    try (ProcessTimeLogContext ignore2 = new ProcessTimeLogContext(processTime, OVERRIDE_ERROR)) {
      if (acceptableExecutionTime != null && processTime > acceptableExecutionTime.toMillis()) {
        log.debug("Done with entity but took too long acceptable {}", acceptableExecutionTime.toMillis());
      }
    } catch (Exception exception) {
      log.error("Exception while recording the processing of entity", exception);
      iteratorMetricsService.recordIteratorMetrics(iteratorName, ITERATOR_ERROR);
    }
  }
}
