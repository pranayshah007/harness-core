/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.persistence.HPersistence.upsertReturnNewOptions;

import io.harness.CDCStateEntity.cdcStateEntityKeys;
import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.entities.CDCEntity;
import io.harness.persistence.PersistentEntity;

import software.wings.dl.WingsPersistence;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import jodd.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Strings;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class ChangeEventProcessorTask implements Runnable {
  private ExecutorService executorService;
  private final Set<CDCEntity<?>> cdcEntities;
  private final BlockingQueue<ChangeEvent<?>> changeEventQueue;
  private final WingsPersistence wingsPersistence;
  private final AtomicInteger processing = new AtomicInteger(0);
  private final AtomicLong completed = new AtomicLong(0);

  ChangeEventProcessorTask(Set<CDCEntity<?>> cdcEntities, BlockingQueue<ChangeEvent<?>> changeEventQueue,
      WingsPersistence wingsPersistence) {
    this.cdcEntities = cdcEntities;
    this.changeEventQueue = changeEventQueue;
    this.wingsPersistence = wingsPersistence;
  }

  @Override
  public void run() {
    executorService = Executors.newFixedThreadPool(
        cdcEntities.size(), ThreadFactoryBuilder.create().setNameFormat("change-processor-%d").get());
    Set<Future<?>> futures =
        cdcEntities.stream().map(cdcEntity -> executorService.submit(this::listenToQueue)).collect(Collectors.toSet());
    waitForTasks(futures);
  }

  private void waitForTasks(Set<Future<?>> futures) {
    for (Future<?> future : futures) {
      try {
        future.get();
      } catch (InterruptedException | ExecutionException e) {
        log.error("ChangeEvent processor task stopped", e);
      }
    }
  }

  public void listenToQueue() {
    try {
      while (!executorService.isShutdown()) {
        ChangeEvent<?> changeEvent = changeEventQueue.poll(Integer.MAX_VALUE, TimeUnit.MINUTES);
        if (changeEvent != null) {
          processing.incrementAndGet();
          processChangeSafely(changeEvent);
          processing.decrementAndGet();
          completed.incrementAndGet();
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("ChangeEvent processor interrupted");
    } finally {
      log.info("Shutting down search consumer service");
      executorService.shutdownNow();
    }
  }

  private void processChangeSafely(ChangeEvent<?> changeEvent) {
    try {
      processChange(changeEvent);
    } catch (Exception e) {
      log.error("An error occurred while processing change event, event={}", changeEvent, e);
    }
  }

  private void processChange(ChangeEvent<?> changeEvent) {
    Class<? extends PersistentEntity> clazz = changeEvent.getEntityType();
    ChangeDataCapture[] dataCaptures = clazz.getAnnotationsByType(ChangeDataCapture.class);

    for (CDCEntity<?> cdcEntity : cdcEntities) {
      if (cdcEntity.getSubscriptionEntity().equals(clazz)) {
        for (ChangeDataCapture changeDataCapture : dataCaptures) {
          ChangeHandler changeHandler = cdcEntity.getChangeHandler(changeDataCapture.handler());
          if (changeHandler != null) {
            changeHandler.handleChange(
                changeEvent, Strings.toLowerCase(changeDataCapture.table()), changeDataCapture.fields());
          } else {
            log.info("ChangeHandler for {} is null.", changeDataCapture.handler());
          }
        }
      }
    }

    saveCDCStateEntityToken(clazz, changeEvent);
  }

  private void saveCDCStateEntityToken(Class<? extends PersistentEntity> sourceClass, ChangeEvent<?> changeEvent) {
    if (changeEvent.getToken() != null) {
      String sourceClassName = sourceClass.getCanonicalName();

      Query<CDCStateEntity> query = wingsPersistence.createQuery(CDCStateEntity.class)
                                        .field(cdcStateEntityKeys.sourceEntityClass)
                                        .equal(sourceClassName);

      UpdateOperations<CDCStateEntity> updateOperations =
          wingsPersistence.createUpdateOperations(CDCStateEntity.class)
              .set(cdcStateEntityKeys.lastSyncedToken, changeEvent.getToken());

      CDCStateEntity cdcStateEntity = wingsPersistence.upsert(query, updateOperations, upsertReturnNewOptions);
      if (cdcStateEntity == null || !cdcStateEntity.getLastSyncedToken().equals(changeEvent.getToken())) {
        log.error(
            "Failed to save resume token for, entity={}, changeEvent={}", sourceClass.getCanonicalName(), changeEvent);
      }
    }
  }

  public int getActiveCount() {
    return processing.get();
  }

  public long getCompletedTaskCount() {
    return completed.get();
  }
}
