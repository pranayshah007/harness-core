/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.Base.BaseKeys;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.TimeSeriesMLScores;
import software.wings.service.intfc.verification.CVConfigurationService;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class TimeSeriesMLScoresTTLMigration implements Migration {
  private static int BATCH_SIZE = 100;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private CVConfigurationService cvConfigurationService;

  @Override
  public void migrate() {
    Instant now = Instant.now();
    Instant deletionThreshold = now.minus(180, ChronoUnit.DAYS);
    log.info("Deleting records older than " + deletionThreshold.toString());
    deleteOldRecords(deletionThreshold);
    log.info("Deletion complete");
    log.info("Adding validUntil field to all records");
    addTTLIndexedField();
    log.info("Addition complete");
  }

  private void deleteOldRecords(Instant deletionThreshold) {
    try {
      Query<TimeSeriesMLScores> query = wingsPersistence.createQuery(TimeSeriesMLScores.class, excludeAuthority)
                                            .field(BaseKeys.lastUpdatedAt)
                                            .lessThanOrEq(deletionThreshold.toEpochMilli());
      long documentsToBeDeleted = query.count();
      log.info("Number of records to be deleted " + documentsToBeDeleted);
      long documentsRemaining = query.count();
      Query<TimeSeriesMLScores> limitedQuery = query.limit(BATCH_SIZE);
      while (documentsRemaining > 0) {
        wingsPersistence.deleteOnServer(limitedQuery);
        documentsRemaining = query.count();
        log.info("Number of records to be deleted " + documentsRemaining);
      }

    } catch (Exception e) {
      log.error("Exception while deleting records. ", e);
    }
  }

  private void addTTLIndexedField() {
    long totalRecords = wingsPersistence.createQuery(TimeSeriesMLScores.class, excludeAuthority).count();
    log.info("Number of records to be updated " + totalRecords);
    try (HIterator<TimeSeriesMLScores> iterator =
             new HIterator<>(wingsPersistence.createQuery(TimeSeriesMLScores.class).fetch())) {
      for (TimeSeriesMLScores dbRecord : iterator) {
        long lastUpdatedAt = dbRecord.getLastUpdatedAt();
        Date recordValidUntil = Date.from(Instant.ofEpochMilli(lastUpdatedAt).plus(180, ChronoUnit.DAYS));
        dbRecord.setValidUntil(recordValidUntil);
        log.info("Updating record " + dbRecord.getUuid());
        wingsPersistence.save(dbRecord);
      }
    } catch (Exception e) {
      log.error("Exception while deleting records. ", e);
    }
  }
}
