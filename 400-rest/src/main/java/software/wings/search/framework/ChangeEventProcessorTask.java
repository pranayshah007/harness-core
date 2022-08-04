/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.framework;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.persistence.HPersistence.upsertReturnNewOptions;

import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.changestreams.ChangeEvent;
import io.harness.mongo.changestreams.ChangeType;
import io.harness.persistence.PersistentEntity;

import software.wings.dl.WingsPersistence;
import software.wings.search.framework.SearchSourceEntitySyncState.SearchSourceEntitySyncStateKeys;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@OwnedBy(PL)
@Slf4j
public class ChangeEventProcessorTask implements Runnable {
  private ExecutorService executorService;
  private Set<SearchEntity<?>> searchEntities;
  private Set<TimeScaleEntity<?>> timeScaleEntities;
  private WingsPersistence wingsPersistence;
  private ChangeEventMetricsTracker changeEventMetricsTracker;
  private BlockingQueue<ChangeEvent<?>> changeEventQueue;
  private Set<String> accountIdsToSyncToTimescale;
  private long logMetricsCounter;
  private boolean closeTimeScaleSyncProcessingOnFailure;

  ChangeEventProcessorTask(Set<SearchEntity<?>> searchEntities, Set<TimeScaleEntity<?>> timeScaleEntities,
      WingsPersistence wingsPersistence, ChangeEventMetricsTracker changeEventMetricsTracker,
      BlockingQueue<ChangeEvent<?>> changeEventQueue, Set<String> accountIdsToSyncToTimescale,
      boolean closeTimeScaleSyncProcessingOnFailure) {
    this.searchEntities = searchEntities;
    this.timeScaleEntities = timeScaleEntities;
    this.wingsPersistence = wingsPersistence;
    this.changeEventMetricsTracker = changeEventMetricsTracker;
    this.changeEventQueue = changeEventQueue;
    this.logMetricsCounter = 0;
    this.accountIdsToSyncToTimescale = accountIdsToSyncToTimescale;
    this.closeTimeScaleSyncProcessingOnFailure = closeTimeScaleSyncProcessingOnFailure;
  }

  public void run() {
    executorService = Executors.newFixedThreadPool(
        searchEntities.size(), new ThreadFactoryBuilder().setNameFormat("change-processor-%d").build());
    try {
      boolean isRunningSuccessfully = true;
      boolean isTimeScaleRunningSuccessfully = true;

      while (isRunningSuccessfully || isTimeScaleRunningSuccessfully) {
        ChangeEvent<?> changeEvent = changeEventQueue.poll(Integer.MAX_VALUE, TimeUnit.MINUTES);
        if (isRunningSuccessfully) {
          if (changeEvent != null) {
            isRunningSuccessfully = processChange(changeEvent);
          }
        }
        if (!closeTimeScaleSyncProcessingOnFailure || isTimeScaleRunningSuccessfully) {
          if (changeEvent != null) {
            isTimeScaleRunningSuccessfully = processTimeScaleChange(changeEvent);
          }
        }
      }

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("ChangeEvent processor interrupted", e);
    } finally {
      log.info("Shutting down search consumer service");
      executorService.shutdownNow();
    }
  }

  private boolean saveSearchSourceEntitySyncStateToken(Class<? extends PersistentEntity> sourceClass, String token) {
    String sourceClassName = sourceClass.getCanonicalName();

    Query<SearchSourceEntitySyncState> query = wingsPersistence.createQuery(SearchSourceEntitySyncState.class)
                                                   .field(SearchSourceEntitySyncStateKeys.sourceEntityClass)
                                                   .equal(sourceClassName);

    UpdateOperations<SearchSourceEntitySyncState> updateOperations =
        wingsPersistence.createUpdateOperations(SearchSourceEntitySyncState.class)
            .set(SearchSourceEntitySyncStateKeys.lastSyncedToken, token);

    SearchSourceEntitySyncState searchSourceEntitySyncState =
        wingsPersistence.upsert(query, updateOperations, upsertReturnNewOptions);
    if (searchSourceEntitySyncState == null || !searchSourceEntitySyncState.getLastSyncedToken().equals(token)) {
      log.error(String.format("Search Entity %s token %s could not be updated", sourceClass.getCanonicalName(), token));
      return false;
    }
    return true;
  }

  private Callable<Boolean> getProcessChangeEventTask(ChangeHandler changeHandler, ChangeEvent changeEvent) {
    return () -> changeHandler.handleChange(changeEvent);
  }

  private boolean processChange(ChangeEvent<?> changeEvent) {
    Instant start = Instant.now();
    Class<? extends PersistentEntity> sourceClass = changeEvent.getEntityType();
    List<Future<Boolean>> processChangeEventTaskFutures = new ArrayList<>();
    for (SearchEntity<?> searchEntity : searchEntities) {
      if (searchEntity.getSubscriptionEntities().contains(sourceClass)) {
        ChangeHandler changeHandler = searchEntity.getChangeHandler();
        Callable<Boolean> processChangeEventTask = getProcessChangeEventTask(changeHandler, changeEvent);
        processChangeEventTaskFutures.add(executorService.submit(processChangeEventTask));
      }
    }

    for (Future<Boolean> processChangeEventFuture : processChangeEventTaskFutures) {
      boolean isChangeHandled = false;
      try {
        isChangeHandled = processChangeEventFuture.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.error("Change Event thread interrupted", e);
      } catch (ExecutionException e) {
        log.error("Change event thread interrupted due to exception", e);
      }
      if (!isChangeHandled) {
        log.error("Could not process changeEvent {}", changeEvent.toString());
        return false;
      }
    }

    boolean isSaved = saveSearchSourceEntitySyncStateToken(sourceClass, changeEvent.getToken());
    if (!isSaved) {
      log.error("Could not save token. ChangeEvent {} could not be processed for entity {}", changeEvent.toString(),
          sourceClass.getCanonicalName());
    }

    double timeTaken = Duration.between(start, Instant.now()).toMillis();
    changeEventMetricsTracker.updateAverage(changeEvent.getEntityType().toString(), timeTaken);
    logMetrics(changeEvent, timeTaken);
    return isSaved;
  }

  private boolean processTimeScaleChange(ChangeEvent<?> changeEvent) {
    Instant start = Instant.now();
    Class<? extends PersistentEntity> sourceClass = changeEvent.getEntityType();
    List<Future<Boolean>> processChangeEventTaskFutures = new ArrayList<>();

    for (TimeScaleEntity<?> timeScaleEntity : timeScaleEntities) {
      if (timeScaleEntity.getSourceEntityClass().equals(sourceClass)) {
        ChangeHandler changeHandler = timeScaleEntity.getChangeHandler();
        if ((ChangeType.DELETE.equals(changeEvent.getChangeType())
                || timeScaleEntity.toProcessChangeEvent(accountIdsToSyncToTimescale, changeEvent.getFullDocument()))
            && changeHandler != null) {
          Callable<Boolean> processChangeEventTask = getProcessChangeEventTask(changeHandler, changeEvent);
          processChangeEventTaskFutures.add(executorService.submit(processChangeEventTask));
        }
      }
    }

    boolean isSaved = saveSearchSourceEntitySyncStateToken(sourceClass, changeEvent.getToken());
    for (Future<Boolean> processChangeEventFuture : processChangeEventTaskFutures) {
      boolean isChangeHandled = false;
      try {
        isChangeHandled = processChangeEventFuture.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.error("ChangeStream: TimeScale Change Event thread interrupted", e);
      } catch (ExecutionException e) {
        log.error("ChangeStream: TimeScale Change event thread interrupted due to exception", e);
      }
      if (!isChangeHandled) {
        log.error("ChangeStream: Could not process TimeScale changeEvent {}", changeEvent.toString());
        return false;
      }
    }

    if (!isSaved) {
      log.error("Could not save token. ChangeEvent {} could not be processed for entity {}", changeEvent.toString(),
          sourceClass.getCanonicalName());
    }

    double timeTaken = Duration.between(start, Instant.now()).toMillis();
    changeEventMetricsTracker.updateAverage(changeEvent.getEntityType().toString(), timeTaken);
    logMetrics(changeEvent, timeTaken);
    return isSaved;
  }

  private void logMetrics(ChangeEvent<?> changeEvent, double timeTaken) {
    logMetricsCounter++;
    boolean shouldLogMetrics = (logMetricsCounter % 5000) == 0;
    if (shouldLogMetrics) {
      log.info("Search change event blocking queue size {}", changeEventQueue.size());
      log.info("Time taken for changeEvent {}:{} is {}", changeEvent.getEntityType(), changeEvent.getChangeType(),
          timeTaken);
      log.info("Running average: {}", changeEventMetricsTracker.getRunningAverageTime());
      log.info("No. of change Events processed: {}", changeEventMetricsTracker.getNumChangeEvents());
    }
  }
}
