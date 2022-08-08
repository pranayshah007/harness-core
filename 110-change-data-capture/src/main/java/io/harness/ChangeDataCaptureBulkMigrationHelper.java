/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.ChangeDataCapture;
import io.harness.annotations.dev.OwnedBy;
import io.harness.changestreamsframework.ChangeTracker;
import io.harness.entities.CDCEntity;
import io.harness.persistence.PersistentEntity;

import software.wings.dl.WingsPersistence;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

@OwnedBy(CE)
@Slf4j
public class ChangeDataCaptureBulkMigrationHelper {
  @Inject private ChangeTracker changeTracker;
  @Inject private WingsPersistence wingsPersistence;
  private static final int NUMBER_OF_BULK_SYNC_THREADS = 5;
  private static final int BULK_SYNC_TASK_QUEUE_SIZE = 200;
  private static final String BULK_THREAD_SUFFIX = "-bulk-migration-%d";

  private boolean runBulkMigration(CDCEntity<?> cdcEntity) {
    return migrateDocuments(cdcEntity);
  }

  private <T extends PersistentEntity> boolean migrateDocuments(CDCEntity<T> cdcEntity) {
    String threadPoolNameFormat = cdcEntity.getClass().getSimpleName().concat(BULK_THREAD_SUFFIX);
    ExecutorService executorService = Executors.newFixedThreadPool(
        NUMBER_OF_BULK_SYNC_THREADS, new ThreadFactoryBuilder().setNameFormat(threadPoolNameFormat).build());
    BoundedExecutorService boundedExecutorService =
        new BoundedExecutorService(executorService, BULK_SYNC_TASK_QUEUE_SIZE);
    List<Future<Boolean>> taskFutures = new ArrayList<>();

    Class<? extends PersistentEntity> subscriptionEntity = cdcEntity.getSubscriptionEntity();

    MongoDatabase mongoDatabase =
        changeTracker.connectToMongoDatabase(changeTracker.getChangeDataCaptureDataStore(subscriptionEntity));

    MongoCollection<Document> collection =
        mongoDatabase.getCollection(changeTracker.getCollectionName(subscriptionEntity));

    try (MongoCursor<Document> cursor = collection.find().iterator()) {
      while (cursor.hasNext()) {
        final Document document = cursor.next();
        ChangeDataCapture[] dataCaptures =
            cdcEntity.getSubscriptionEntity().getAnnotationsByType(ChangeDataCapture.class);
        for (ChangeDataCapture changeDataCapture : dataCaptures) {
          ChangeHandler changeHandler = cdcEntity.getChangeHandler(changeDataCapture.handler());
          if (changeHandler != null) {
            CDCEntityBulkMigrationTask<T> cdcEntityBulkMigrationTask = new CDCEntityBulkMigrationTask<>(
                changeHandler, subscriptionEntity, document, changeDataCapture.table(), changeDataCapture.fields());
            taskFutures.add(boundedExecutorService.submit(cdcEntityBulkMigrationTask));
          } else {
            log.info("ChangeHandler for {} is null", changeDataCapture.handler());
          }
        }
      }
      for (Future<Boolean> taskFuture : taskFutures) {
        boolean result = taskFuture.get();
        if (!result) {
          return false;
        }
      }
      return true;
    } catch (InterruptedException e) {
      log.error("Bulk migration interrupted", e);
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      log.error("Bulk migration errored due to error", e.getCause());
    } finally {
      executorService.shutdownNow();
    }
    return false;
  }

  boolean doBulkSync(Set<CDCEntity<?>> entitiesToBulkSync) {
    boolean hasMigrationSucceeded = true;
    for (CDCEntity<?> cdcEntity : entitiesToBulkSync) {
      CDCStateEntity cdcStateEntityState =
          wingsPersistence.get(CDCStateEntity.class, cdcEntity.getSubscriptionEntity().getCanonicalName());
      if (null == cdcStateEntityState) {
        log.info("Migrating {} to Sink Change Data Capture", cdcEntity.getClass().getCanonicalName());
        hasMigrationSucceeded = runBulkMigration(cdcEntity);

        if (hasMigrationSucceeded) {
          log.info("{} migrated to Sink Change Data Capture", cdcEntity.getClass().getCanonicalName());
        } else {
          log.error(String.format(
              "Failed to migrate %s to Sink Change Data Capture", cdcEntity.getClass().getCanonicalName()));
          break;
        }
      }
    }
    return hasMigrationSucceeded;
  }
}
