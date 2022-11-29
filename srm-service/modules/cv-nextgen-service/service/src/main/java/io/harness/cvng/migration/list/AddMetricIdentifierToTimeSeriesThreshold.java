/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cvng.core.entities.TimeSeriesThreshold;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class AddMetricIdentifierToTimeSeriesThreshold implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    log.info("Adding identifiers for Time Series Thresholds");
    Query<TimeSeriesThreshold> timeSeriesThresholdQuery = hPersistence.createQuery(TimeSeriesThreshold.class);
    try (HIterator<TimeSeriesThreshold> iterator = new HIterator<>(timeSeriesThresholdQuery.fetch())) {
      while (iterator.hasNext()) {
        TimeSeriesThreshold timeSeriesThreshold = iterator.next();

        if (isEmpty(timeSeriesThreshold.getMetricIdentifier())
            || containsUpperCaseLetter(timeSeriesThreshold.getMetricIdentifier())) {
          String identifier = timeSeriesThreshold.getMetricName().replaceAll(" ", "_").toLowerCase();
          identifier = identifier.replaceAll("\\(", "");
          identifier = identifier.replaceAll("\\)", "");
          timeSeriesThreshold.setMetricIdentifier(identifier);
        }
        hPersistence.save(timeSeriesThreshold);

        log.info("Identifier updation for time series thresholds {}, {}", timeSeriesThreshold.getProjectIdentifier(),
            timeSeriesThreshold.getUuid());
      }
    }
  }
  public boolean containsUpperCaseLetter(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (Character.isUpperCase(s.charAt(i))) {
        return true;
      }
    }
    return false;
  }
  @Override
  public ChecklistItem whatHappensOnRollback() {
    return ChecklistItem.NA;
  }

  @Override
  public ChecklistItem whatHappensIfOldVersionIteratorPicksMigratedEntity() {
    return ChecklistItem.NA;
  }
}
