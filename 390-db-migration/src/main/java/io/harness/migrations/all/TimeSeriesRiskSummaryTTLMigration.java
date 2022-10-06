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
import software.wings.service.impl.analysis.TimeSeriesRiskSummary;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

@Slf4j
public class TimeSeriesRiskSummaryTTLMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

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
      Query<TimeSeriesRiskSummary> countQuery =
          wingsPersistence.createQuery(TimeSeriesRiskSummary.class, excludeAuthority)
              .field(BaseKeys.lastUpdatedAt)
              .lessThanOrEq(deletionThreshold.toEpochMilli());
      long documentsToBeDeleted = countQuery.count();
      log.info("Number of records to be deleted " + documentsToBeDeleted);
      long documentsRemaining = countQuery.count();
      long fromTimestamp = wingsPersistence.createQuery(TimeSeriesRiskSummary.class, excludeAuthority)
                               .field(BaseKeys.lastUpdatedAt)
                               .lessThanOrEq(deletionThreshold.toEpochMilli())
                               .order(Sort.ascending(BaseKeys.lastUpdatedAt))
                               .limit(1)
                               .get()
                               .getLastUpdatedAt();
      Instant fromInstant = Instant.ofEpochMilli(fromTimestamp);
      Instant toInstant = fromInstant.plus(1, ChronoUnit.DAYS);

      while (documentsRemaining > 0) {
        toInstant = deletionThreshold.compareTo(toInstant) > 0 ? toInstant : deletionThreshold;
        log.info("Deleting records between " + fromInstant + " and " + toInstant);
        try {
          Query<TimeSeriesRiskSummary> findQuery =
              wingsPersistence.createQuery(TimeSeriesRiskSummary.class, excludeAuthority)
                  .field(BaseKeys.lastUpdatedAt)
                  .greaterThanOrEq(fromInstant.toEpochMilli())
                  .field(BaseKeys.lastUpdatedAt)
                  .lessThan(toInstant.toEpochMilli());

          wingsPersistence.deleteOnServer(findQuery);
        } catch (Exception e) {
          log.error("Exception while deleting records between " + fromInstant + " and " + toInstant, e);
        }
        documentsRemaining = countQuery.count();
        log.info("Number of records to be deleted " + documentsRemaining);
        fromInstant = fromInstant.plus(1, ChronoUnit.DAYS);
        toInstant = fromInstant.plus(1, ChronoUnit.DAYS);
      }

    } catch (Exception e) {
      log.error("Exception while deleting records. ", e);
    }
  }

  private void addTTLIndexedField() {
    long totalRecords = wingsPersistence.createQuery(TimeSeriesRiskSummary.class, excludeAuthority).count();
    log.info("Number of records to be updated " + totalRecords);
    try (HIterator<TimeSeriesRiskSummary> iterator =
             new HIterator<>(wingsPersistence.createQuery(TimeSeriesRiskSummary.class).fetch())) {
      for (TimeSeriesRiskSummary dbRecord : iterator) {
        long lastUpdatedAt = dbRecord.getLastUpdatedAt();
        Date recordValidUntil = Date.from(Instant.ofEpochMilli(lastUpdatedAt).plus(180, ChronoUnit.DAYS));
        dbRecord.setValidUntil(recordValidUntil);
        log.info("Updating record " + dbRecord.getUuid());
        try {
          wingsPersistence.save(dbRecord);
        } catch (Exception e) {
          log.error("Exception while updating record. " + dbRecord.getUuid(), e);
        }
      }
    } catch (Exception e) {
      log.error("Exception while updating records. ", e);
    }
  }
}
