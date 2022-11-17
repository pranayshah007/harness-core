/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.beans.SecretUsageLog;
import io.harness.beans.SecretUsageLog.SecretUsageLogKeys;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddValidUntilToSecretUsageLogs implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    DBCollection collection = wingsPersistence.getCollection(SecretUsageLog.class);
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();

    int i = 1;
    try (HIterator<SecretUsageLog> secretUsageLogs = new HIterator<>(wingsPersistence.createQuery(SecretUsageLog.class)
                                                                         .field("validUntil")
                                                                         .doesNotExist()
                                                                         .project(SecretUsageLogKeys.createdAt, true)
                                                                         .fetch())) {
      while (secretUsageLogs.hasNext()) {
        SecretUsageLog secretUsageLog = secretUsageLogs.next();
        ZonedDateTime zonedDateTime =
            Instant.ofEpochMilli(secretUsageLog.getCreatedAt()).atZone(ZoneOffset.UTC).plusMonths(6);

        if (i % 1000 == 0) {
          bulkWriteOperation.execute();
          bulkWriteOperation = collection.initializeUnorderedBulkOperation();
          log.info("SecretUsageLogs: {} updated", i);
        }
        ++i;

        bulkWriteOperation
            .find(wingsPersistence.createQuery(SecretUsageLog.class)
                      .filter(SecretUsageLogKeys.uuid, secretUsageLog.getUuid())
                      .getQueryObject())
            .updateOne(new BasicDBObject(
                "$set", new BasicDBObject("validUntil", java.util.Date.from(zonedDateTime.toInstant()))));
      }
    }
    if (i % 1000 != 1) {
      bulkWriteOperation.execute();
    }
  }
}
