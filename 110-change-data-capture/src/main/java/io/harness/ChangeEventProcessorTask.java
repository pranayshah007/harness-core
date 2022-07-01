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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
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
import org.bouncycastle.util.Strings;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class ChangeEventProcessorTask implements Runnable {
  private ExecutorService executorService;
  private Set<CDCEntity<?>> cdcEntities;
  private BlockingQueue<ChangeEvent<?>> changeEventQueue;
  private WingsPersistence wingsPersistence;

  ChangeEventProcessorTask(Set<CDCEntity<?>> cdcEntities, BlockingQueue<ChangeEvent<?>> changeEventQueue,
      WingsPersistence wingsPersistence) {
    this.cdcEntities = cdcEntities;
    this.changeEventQueue = changeEventQueue;
    this.wingsPersistence = wingsPersistence;
  }

  @Override
  public void run() {
    executorService =
        Executors.newFixedThreadPool(8, new ThreadFactoryBuilder().setNameFormat("change-processor-%d").build());
    try {
      boolean isRunningSuccessfully = true;
      while (isRunningSuccessfully) {
        ChangeEvent<?> changeEvent = changeEventQueue.poll(Integer.MAX_VALUE, TimeUnit.MINUTES);
        if (changeEvent != null) {
          isRunningSuccessfully = processChange(changeEvent);
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

  private Callable<Boolean> getProcessChangeEventTask(
      ChangeHandler changeHandler, ChangeEvent changeEvent, ChangeDataCapture changeDataCapture) {
    return ()
               -> changeHandler.handleChange(
                   changeEvent, Strings.toLowerCase(changeDataCapture.table()), changeDataCapture.fields());
  }

  private boolean processChange(ChangeEvent<?> changeEvent) {
    List<Future<Boolean>> processChangeEventTaskFutures = new ArrayList<>();
    Class<? extends PersistentEntity> clazz = changeEvent.getEntityType();
    ChangeDataCapture[] dataCaptures = clazz.getAnnotationsByType(ChangeDataCapture.class);

    for (CDCEntity<?> cdcEntity : cdcEntities) {
      if (cdcEntity.getSubscriptionEntity().equals(clazz)) {
        for (ChangeDataCapture changeDataCapture : dataCaptures) {
          ChangeHandler changeHandler = cdcEntity.getChangeHandler(changeDataCapture.handler());
          if (changeHandler != null) {
            Callable<Boolean> processChangeEventTask =
                getProcessChangeEventTask(changeHandler, changeEvent, changeDataCapture);
            processChangeEventTaskFutures.add(executorService.submit(processChangeEventTask));
          } else {
            log.info("ChangeHandler for {} is null.", changeDataCapture.handler());
          }
        }
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
        log.error("Change event thread interrupted due to exception", e.getCause());
      }
      if (!isChangeHandled) {
        log.error("Could not process changeEvent {}", changeEvent.toString());
        return false;
      }
    }

    boolean isSaved = saveCDCStateEntityToken(clazz, changeEvent.getToken());
    if (!isSaved) {
      log.error("Could not save token. ChangeEvent {} could not be processed for entity {}", changeEvent.toString(),
          clazz.getCanonicalName());
    }
    return isSaved;
  }

  private boolean saveCDCStateEntityToken(Class<? extends PersistentEntity> sourceClass, String token) {
    String sourceClassName = sourceClass.getCanonicalName();

    Query<CDCStateEntity> query = wingsPersistence.createQuery(CDCStateEntity.class)
                                      .field(cdcStateEntityKeys.sourceEntityClass)
                                      .equal(sourceClassName);

    UpdateOperations<CDCStateEntity> updateOperations =
        wingsPersistence.createUpdateOperations(CDCStateEntity.class).set(cdcStateEntityKeys.lastSyncedToken, token);

    CDCStateEntity cdcStateEntity = wingsPersistence.upsert(query, updateOperations, upsertReturnNewOptions);
    if (cdcStateEntity == null || !cdcStateEntity.getLastSyncedToken().equals(token)) {
      log.error(String.format("Search Entity %s token %s could not be updated", sourceClass.getCanonicalName(), token));
      return false;
    }
    return true;
  }
}
