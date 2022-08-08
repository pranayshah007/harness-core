/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HPersistence.DEFAULT_STORE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migrations.Migration;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class AlertCheckJobPollIntervalMigration implements Migration {
  private static final String DEBUG_LINE = "AlertCheckJobPollInterval Migration: ";
  private static final String COLLECTION_NAME = "quartz_triggers";
  private static final String KEY_GROUP = "ALERT_CHECK_CRON_GROUP";

  // current poll interval is 5 mins
  private static final long CURRENT_REPEAT_INTERVAL = 300000L;

  // new poll interval time should be 2 mins
  private static final long NEW_REPEAT_INTERVAL = 120000L;

  @Inject private HPersistence persistence;

  @Override
  public void migrate() {
    log.info(DEBUG_LINE + "Starting the migration to reduce the alert check cron job poll interval.");
    try {
      runMigrationInBatch();
    } catch (Exception e) {
      log.error(
          DEBUG_LINE + "Exception occurred while running migration to reduce the alert check cron job poll interval. ",
          e);
    } finally {
      log.info(DEBUG_LINE + "Migration complete to reduce the alert check cron job poll interval.");
    }
  }

  private void runMigrationInBatch() {
    DBCollection collection = persistence.getCollection(DEFAULT_STORE, COLLECTION_NAME);
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();

    BasicDBObject objectsToBeUpdated =
        new BasicDBObject("keyGroup", KEY_GROUP).append("repeatInterval", CURRENT_REPEAT_INTERVAL);
    BasicDBObject projection = new BasicDBObject("_id", true);

    DBCursor dataRecords = collection.find(objectsToBeUpdated, projection).limit(1000);

    int updated = 0;
    try {
      while (dataRecords.hasNext()) {
        DBObject dataRecord = dataRecords.next();

        ObjectId uuId = (ObjectId) dataRecord.get("_id");
        bulkWriteOperation.find(new BasicDBObject("_id", uuId))
            .updateOne(new BasicDBObject("$set", new BasicDBObject("repeatInterval", NEW_REPEAT_INTERVAL)));
        updated++;

        if (updated != 0 && updated % 1000 == 0) {
          bulkWriteOperation.execute();
          bulkWriteOperation = collection.initializeUnorderedBulkOperation();
          dataRecords = collection.find(objectsToBeUpdated, projection).limit(1000);
          log.info(DEBUG_LINE + "Number of records updated for {} is {}", COLLECTION_NAME, updated);
        }
      }

      if (updated % 1000 != 0) {
        bulkWriteOperation.execute();
        log.info(DEBUG_LINE + "Number of records updated for {} is {}", COLLECTION_NAME, updated);
      }
    } catch (Exception e) {
      log.error(
          DEBUG_LINE + "Exception occurred while running migration to reduce the alert check job poll interval. ", e);
    } finally {
      dataRecords.close();
    }
  }
}
