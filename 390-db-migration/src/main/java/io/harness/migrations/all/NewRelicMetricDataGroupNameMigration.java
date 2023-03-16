/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static software.wings.beans.Base.ID_KEY2;
import static software.wings.beans.dto.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by rsingh on 3/26/18.
 */
@Slf4j
public class NewRelicMetricDataGroupNameMigration implements Migration {
  @Inject WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    DBCollection collection = wingsPersistence.getCollection(NewRelicMetricDataRecord.class);
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();
    int updated = 0;
    int batched = 0;
    try (HIterator<NewRelicMetricDataRecord> dataRecords = new HIterator<>(
             wingsPersistence.createQuery(NewRelicMetricDataRecord.class).field("groupName").doesNotExist().fetch())) {
      while (dataRecords.hasNext()) {
        final NewRelicMetricDataRecord metricDataRecord = dataRecords.next();

        bulkWriteOperation
            .find(wingsPersistence.createQuery(NewRelicMetricDataRecord.class)
                      .filter(ID_KEY2, metricDataRecord.getUuid())
                      .getQueryObject())
            .updateOne(new BasicDBObject("$set", new BasicDBObject("groupName", DEFAULT_GROUP_NAME)));
        updated++;
        batched++;

        if (updated != 0 && updated % 1000 == 0) {
          bulkWriteOperation.execute();
          bulkWriteOperation = collection.initializeUnorderedBulkOperation();
          batched = 0;
          log.info("updated: " + updated);
        }
      }

      if (batched != 0) {
        bulkWriteOperation.execute();
        log.info("updated: " + updated);
      }
    }

    log.info("Complete. Updated " + updated + " records.");
  }
}
