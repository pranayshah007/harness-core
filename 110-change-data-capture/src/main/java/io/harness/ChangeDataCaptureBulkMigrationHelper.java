/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.changestreamsframework.ChangeTracker;
import io.harness.changestreamsframework.ChangeType;
import io.harness.entities.CDCEntity;
import io.harness.persistence.PersistentEntity;

import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;

@OwnedBy(CE)
@Slf4j
public class ChangeDataCaptureBulkMigrationHelper {
  @Inject private ChangeTracker changeTracker;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ChangeEventProcessor changeEventProcessor;

  private <T extends PersistentEntity> int runBulkMigration(
      CDCEntity<T> cdcEntity, Bson filter, String changeHandler, ChangeType changeType) {
    Class<T> subscriptionEntity = cdcEntity.getSubscriptionEntity();
    int counter = 0;

    MongoDatabase mongoDatabase =
        changeTracker.connectToMongoDatabase(changeTracker.getChangeDataCaptureDataStore(subscriptionEntity));

    MongoCollection<Document> collection =
        mongoDatabase.getCollection(changeTracker.getCollectionName(subscriptionEntity));

    FindIterable<Document> documents;
    if (filter == null) {
      documents = collection.find();
    } else {
      documents = collection.find(filter);
    }

    try (MongoCursor<Document> cursor = documents.iterator()) {
      while (cursor.hasNext()) {
        runSyncForEntity(subscriptionEntity, cursor, changeHandler, changeType);
        counter++;
      }
    }
    return counter;
  }

  private <T extends PersistentEntity> void runSyncForEntity(
      Class<T> subscriptionEntity, MongoCursor<Document> cursor, String handler, ChangeType changeType) {
    final Document document = cursor.next();
    changeEventProcessor.processChangeEvent(
        CDCEntityBulkTaskConverter.convert(subscriptionEntity, document, handler, changeType));
  }

  public void doBulkSync(Iterable<CDCEntity<?>> entitiesToBulkSync) {
    bulkSync(entitiesToBulkSync, null, null, ChangeType.INSERT);
  }

  public int doPartialSync(
      Iterable<CDCEntity<?>> entitiesToBulkSync, Bson filter, String changeHandler, ChangeType changeType) {
    return bulkSync(entitiesToBulkSync, filter, changeHandler, changeType);
  }

  public int bulkSync(
      Iterable<CDCEntity<?>> entitiesToBulkSync, Bson filter, String changeHandler, ChangeType changeType) {
    int counter = 0;
    changeEventProcessor.startProcessingChangeEvents();
    for (CDCEntity<?> cdcEntity : entitiesToBulkSync) {
      if (isPartialSync(filter) || isFirstSync(cdcEntity)) {
        log.info("Migrating {} to Sink Change Data Capture", cdcEntity.getClass().getCanonicalName());
        counter += runBulkMigration(cdcEntity, filter, changeHandler, changeType);
        log.info("{} migrated to Sink Change Data Capture", cdcEntity.getClass().getCanonicalName());
      }
    }
    while (changeEventProcessor.isWorking()) {
      LockSupport.parkNanos(Duration.ofSeconds(1).toNanos());
    }
    return counter;
  }

  private boolean isPartialSync(Bson filter) {
    return filter != null;
  }

  private boolean isFirstSync(CDCEntity<?> cdcEntity) {
    CDCStateEntity cdcStateEntityState =
        wingsPersistence.get(CDCStateEntity.class, cdcEntity.getSubscriptionEntity().getCanonicalName());
    return null == cdcStateEntityState;
  }
}
