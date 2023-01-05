/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofMillis;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;
import io.harness.time.Timestamp;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.MongoDataStoreServiceImpl;
import software.wings.service.impl.analysis.LogDataRecord;
import software.wings.service.impl.analysis.LogDataRecord.LogDataRecordKeys;
import software.wings.service.intfc.DataStoreService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MigrateLogDataRecordsToGoogle implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private DataStoreService dataStoreService;

  @Override
  public void migrate() {
    if (dataStoreService instanceof MongoDataStoreServiceImpl) {
      log.info("Datastore service is an instance of MongoDataStoreServiceImpl. Not migrating the records now.");
      return;
    }
    long startTime = Timestamp.currentMinuteBoundary() - TimeUnit.DAYS.toMillis(7);

    List<LogDataRecord> recordsFromMongo = new ArrayList<>();

    Query<LogDataRecord> logDataRecordQuery = wingsPersistence.createQuery(LogDataRecord.class)
                                                  .filter(LogDataRecordKeys.clusterLevel, "L2")
                                                  .field("createdAt")
                                                  .greaterThan(startTime);

    try (HIterator<LogDataRecord> records = new HIterator<>(logDataRecordQuery.fetch())) {
      while (records.hasNext()) {
        recordsFromMongo.add(records.next());
        if (recordsFromMongo.size() == 1000) {
          dataStoreService.save(LogDataRecord.class, recordsFromMongo, true);
          log.info("Copied 1000 L2 records from Mongo to GoogleDataStore");
          recordsFromMongo = new ArrayList<>();
          sleep(ofMillis(1500));
        }
      }
    }
    dataStoreService.save(LogDataRecord.class, recordsFromMongo, true);
    log.info("Copied {} L2 records from Mongo to GoogleDataStore", recordsFromMongo.size());
  }
}
