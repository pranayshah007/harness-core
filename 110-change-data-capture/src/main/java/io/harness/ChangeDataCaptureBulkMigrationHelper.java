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

import com.google.inject.Inject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

@OwnedBy(CE)
@Slf4j
public class ChangeDataCaptureBulkMigrationHelper {
  @Inject private ChangeTracker changeTracker;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ChangeEventProcessor changeEventProcessor;

  private <T extends PersistentEntity> void runBulkMigration(CDCEntity<T> cdcEntity) {
    Class<T> subscriptionEntity = cdcEntity.getSubscriptionEntity();

    MongoDatabase mongoDatabase =
        changeTracker.connectToMongoDatabase(changeTracker.getChangeDataCaptureDataStore(subscriptionEntity));

    MongoCollection<Document> collection =
        mongoDatabase.getCollection(changeTracker.getCollectionName(subscriptionEntity));

    try (MongoCursor<Document> cursor = collection.find().iterator()) {
      while (cursor.hasNext()) {
        final Document document = cursor.next();
        ChangeDataCapture[] dataCaptures = subscriptionEntity.getAnnotationsByType(ChangeDataCapture.class);
        for (ChangeDataCapture changeDataCapture : dataCaptures) {
          ChangeHandler changeHandler = cdcEntity.getChangeHandler(changeDataCapture.handler());
          if (changeHandler != null) {
            changeEventProcessor.processChangeEvent(CDCEntityBulkTaskConverter.convert(subscriptionEntity, document));
          } else {
            log.debug("ChangeHandler for {} is null", changeDataCapture.handler());
          }
        }
      }
    }
  }

  void doBulkSync(Set<CDCEntity<?>> entitiesToBulkSync) {
    changeEventProcessor.startProcessingChangeEvents();
    for (CDCEntity<?> cdcEntity : entitiesToBulkSync) {
      CDCStateEntity cdcStateEntityState =
          wingsPersistence.get(CDCStateEntity.class, cdcEntity.getSubscriptionEntity().getCanonicalName());
      if (null == cdcStateEntityState) {
        log.info("Migrating {} to Sink Change Data Capture", cdcEntity.getClass().getCanonicalName());
        runBulkMigration(cdcEntity);
        log.info("{} migrated to Sink Change Data Capture", cdcEntity.getClass().getCanonicalName());
      }
    }
    while (changeEventProcessor.isWorking()) {
      LockSupport.parkNanos(Duration.ofSeconds(1).toNanos());
    }
  }
}
