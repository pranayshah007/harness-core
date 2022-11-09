/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aggregator.controllers;

import static io.harness.accesscontrol.aggregator.api.SecondarySyncStatus.SWITCH_TO_PRIMARY_REQUESTED;
import static io.harness.aggregator.AggregatorConfiguration.ACCESS_CONTROL_SERVICE;
import static io.harness.aggregator.models.MongoReconciliationOffset.PRIMARY_COLLECTION;
import static io.harness.aggregator.models.MongoReconciliationOffset.SECONDARY_COLLECTION;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.aggregator.models.AggregatorSecondarySyncState;
import io.harness.aggregator.repositories.AggregatorSecondarySyncStateRepository;
import io.harness.aggregator.repositories.MongoReconciliationOffsetRepository;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Slf4j
@Singleton
public class AggregatorController implements Runnable {
  private final AggregatorPrimarySyncController primarySyncController;
  private final AggregatorSecondarySyncController secondarySyncController;

  private final AggregatorSecondarySyncStateRepository aggregatorSecondarySyncStateRepository;
  private final ACLRepository secondaryACLRepository;
  private final MongoReconciliationOffsetRepository mongoReconciliationOffsetRepository;
  private final PersistentLocker persistentLocker;

  @Inject
  public AggregatorController(AggregatorSecondarySyncController secondarySyncController,
      AggregatorPrimarySyncController primarySyncJobController,
      AggregatorSecondarySyncStateRepository aggregatorSecondarySyncStateRepository,
      @Named(ACL.SECONDARY_COLLECTION) ACLRepository secondaryACLRepository,
      MongoReconciliationOffsetRepository mongoReconciliationOffsetRepository, PersistentLocker persistentLocker) {
    this.secondarySyncController = secondarySyncController;
    this.primarySyncController = primarySyncJobController;
    this.aggregatorSecondarySyncStateRepository = aggregatorSecondarySyncStateRepository;
    this.secondaryACLRepository = secondaryACLRepository;
    this.mongoReconciliationOffsetRepository = mongoReconciliationOffsetRepository;
    this.persistentLocker = persistentLocker;
  }

  @Override
  public void run() {
    ExecutorService primarySyncExecutorService = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat("aggregator-primary-sync-controller").build());
    ExecutorService secondarySyncExecutorService = Executors.newSingleThreadExecutor(
        new ThreadFactoryBuilder().setNameFormat("aggregator-secondary-sync-controller").build());
    Future<?> primarySyncFuture = null;
    Future<?> secondarySyncFuture = null;
    log.info("Aggregator Controller has started");
    try {
      while (true) {
        if (isSwitchToPrimaryRequested()) {
          stopChildControllers(primarySyncFuture, secondarySyncFuture);
          switchToPrimary();
        } else {
          if (!isControllerRunning(primarySyncFuture)) {
            log.info("Starting aggregator primary sync controller");
            primarySyncFuture = primarySyncExecutorService.submit(primarySyncController);
          }
          if (!isControllerRunning(secondarySyncFuture)) {
            log.info("Starting aggregator secondary sync controller");
            secondarySyncFuture = secondarySyncExecutorService.submit(secondarySyncController);
          }
        }
        TimeUnit.SECONDS.sleep(30);
      }
    } catch (InterruptedException e) {
      log.warn("The aggregator controller was interrupted. Exiting", e);
    } catch (Exception e) {
      log.error("Unexpected exception during aggregator controller. Exiting", e);
    } finally {
      primarySyncExecutorService.shutdownNow();
      secondarySyncExecutorService.shutdownNow();
      try {
        log.info("Waiting for Aggregator Primary Sync to shutdown gracefully.");
        primarySyncExecutorService.awaitTermination(30, TimeUnit.SECONDS);
        log.info("Waiting for Aggregator Secondary Sync to shutdown gracefully.");
        secondarySyncExecutorService.awaitTermination(30, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Aggregator primary and secondary controllers did not shutdown gracefully. Exiting", e);
      }
    }
  }

  private boolean isSwitchToPrimaryRequested() {
    Optional<AggregatorSecondarySyncState> optional =
        aggregatorSecondarySyncStateRepository.findByIdentifier(ACCESS_CONTROL_SERVICE);
    return optional.filter(state -> SWITCH_TO_PRIMARY_REQUESTED.equals(state.getSecondarySyncStatus())).isPresent();
  }

  private void switchToPrimary() {
    AcquiredLock<?> primaryControllerLock = null;
    AcquiredLock<?> secondaryControllerLock = null;
    try {
      log.info("Trying to acquire child controller locks to ensure child controllers have stopped on all instances");
      primaryControllerLock =
          persistentLocker.tryToAcquireLock(primarySyncController.getLockName(), Duration.ofSeconds(300));
      secondaryControllerLock =
          persistentLocker.tryToAcquireLock(secondarySyncController.getLockName(), Duration.ofSeconds(300));
      if (primaryControllerLock != null && secondaryControllerLock != null) {
        log.info("Both child controller locks have been acquired. Switching primary");
        secondaryACLRepository.renameCollection(ACL.PRIMARY_COLLECTION);
        mongoReconciliationOffsetRepository.renameCollectionAToCollectionB(SECONDARY_COLLECTION, PRIMARY_COLLECTION);
        aggregatorSecondarySyncStateRepository.removeByIdentifier(ACCESS_CONTROL_SERVICE);
      } else {
        log.info("Did not get both child controller locks. Will try switch to primary again after some time");
      }
    } catch (Exception e) {
      log.error("Switch to primary operation failed due to error", e);
    } finally {
      if (primaryControllerLock != null) {
        primaryControllerLock.release();
      }
      if (secondaryControllerLock != null) {
        secondaryControllerLock.release();
      }
    }
  }

  private void stopChildControllers(Future<?> primarySyncFuture, Future<?> secondarySyncFuture)
      throws InterruptedException {
    if (isControllerRunning(primarySyncFuture)) {
      stopController(primarySyncFuture);
    }
    if (isControllerRunning(secondarySyncFuture)) {
      stopController(secondarySyncFuture);
    }
  }

  private boolean isControllerRunning(Future<?> future) {
    return future != null && !future.isDone();
  }

  private void stopController(Future<?> future) throws InterruptedException {
    future.cancel(true);
    TimeUnit.SECONDS.sleep(30);
  }
}
