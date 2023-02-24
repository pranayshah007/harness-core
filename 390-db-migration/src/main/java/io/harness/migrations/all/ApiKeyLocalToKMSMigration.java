/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.threading.Morpheus.sleep;

import static java.util.Objects.isNull;

import io.harness.migrations.Migration;

import software.wings.beans.ApiKeyEntry;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.ApiKeyService;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApiKeyLocalToKMSMigration implements Migration {
  @Inject WingsPersistence wingsPersistence;
  @Inject ApiKeyService apiKeyService;
  private final int batchLimit = 1000;

  @Override
  public void migrate() {
    DBCollection collection = wingsPersistence.getCollection(ApiKeyEntry.class);
    DBCursor dataRecords = collection.find(new BasicDBObject()).limit(batchLimit);
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();

    int updated = 0;
    int batched = 0;

    try {
      while (dataRecords.hasNext()) {
        DBObject record = dataRecords.next();

        String uuid = (String) record.get("_id");
        ApiKeyEntry apiKeyEntry = ApiKeyEntry.builder()
                                      .uuid(uuid)
                                      .accountId((String) record.get("accountId"))
                                      .encryptedDataId((String) record.get("encryptedDataId"))
                                      .build();

        ApiKeyEntry migratedApiKeyEntry = apiKeyService.migrateToKMS(apiKeyEntry);

        if (isNull(migratedApiKeyEntry) || isNull(migratedApiKeyEntry.getEncryptedDataId())) {
          continue;
        }

        bulkWriteOperation.find(new BasicDBObject("_id", uuid))
            .updateOne(
                new BasicDBObject("$set", new BasicDBObject("encryptedDataId", apiKeyEntry.getEncryptedDataId())));
        updated++;
        batched++;

        if (updated != 0 && updated % 1000 == 0) {
          bulkWriteOperation.execute();
          sleep(Duration.ofMillis(200));
          bulkWriteOperation = collection.initializeUnorderedBulkOperation();
          dataRecords = collection.find(new BasicDBObject()).limit(batchLimit);
          batched = 0;
          log.info("Number of ApiKeys migrated is: {}", updated);
        }
      }

      if (batched != 0) {
        bulkWriteOperation.execute();
        log.info("Number of ApiKeys migrated is: {}", updated);
      }
    } catch (Exception ex) {
      log.error("Failure occurred in Api Key migration to KMS with exception", ex);
    } finally {
      dataRecords.close();
    }
  }
}
