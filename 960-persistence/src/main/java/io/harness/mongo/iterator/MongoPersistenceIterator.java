package io.harness.mongo.iterator;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.govern.Switch.unhandled;
import static io.harness.iterator.PersistenceIterator.ProcessMode.PUMP;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
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
import java.util.concurrent.Semaphore;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Builder
@Slf4j
public class MongoPersistenceIterator<T extends PersistentIterable, F extends FilterExpander>
    implements PersistenceIterator<T> {
  private static final Duration QUERY_TIME = ofMillis(200);

  @Inject private final QueueController queueController;

  public interface Handler<T> {
    void handle(T entity);
  }

  public enum SchedulingType { REGULAR, IRREGULAR, IRREGULAR_SKIP_MISSED }

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
  private ExecutorService executorService;
  private Semaphore semaphore;
  private boolean redistribute;
  private EntityProcessController<T> entityProcessController;
  @Getter private SchedulingType schedulingType;

  private long movingAvg(long current, long sample) {
    return (15 * current + sample) / 16;
  }

  private boolean shouldProcess() {
    return !MaintenanceController.getMaintenanceFlag() && queueController.isPrimary();
  }

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
              base, throttled, clazz, fieldName, schedulingType, targetInterval, filterExpander);
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
            executorService.submit(() -> processEntity(finalEntity));
            // it might take some time until the submitted task is actually triggered.
            // lets wait for awhile until for this to happen
            finalEntity.wait(10000);
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
        log.info("Working on entity was interrupted");
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
          if (delay < acceptableNoAlertDelay.toMillis()) {
            log.info("Working on entity");
          } else {
            log.error(
                "Working on entity but the delay is more than the acceptable {}", acceptableNoAlertDelay.toMillis());
          }
        }

        try {
          handler.handle(entity);
        } catch (RuntimeException exception) {
          log.error("Catch and handle all exceptions in the entity handler", exception);
        }
      } catch (Throwable exception) {
        log.error("Exception while processing entity", exception);
      } finally {
        semaphore.release();

        long processTime = currentTimeMillis() - startTime;
        try (ProcessTimeLogContext ignore2 = new ProcessTimeLogContext(processTime, OVERRIDE_ERROR)) {
          if (acceptableExecutionTime == null || processTime <= acceptableExecutionTime.toMillis()) {
            log.info("Done with entity");
          } else {
            log.error("Done with entity but took too long acceptable {}", acceptableExecutionTime.toMillis());
          }
        } catch (Throwable exception) {
          log.error("Exception while recording the processing of entity", exception);
        }
      }
    }
  }
}
