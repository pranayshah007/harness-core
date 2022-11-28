/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import static io.harness.cvng.beans.TimeSeriesMetricType.APDEX;

import io.harness.cvng.core.entities.TimeSeriesThreshold;
import io.harness.cvng.core.entities.TimeSeriesThreshold.TimeSeriesThresholdKeys;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.mongodb.morphia.query.UpdateResults;

@Slf4j
public class UpdateApdexMetricCriteria implements CVNGMigration {
  @Inject private HPersistence hPersistence;
  @Override
  public void migrate() {
    UpdateOperations<TimeSeriesThreshold> updateOperations =
        hPersistence.createUpdateOperations(TimeSeriesThreshold.class);
    updateOperations.set("criteria.thresholdType", "ACT_WHEN_LOWER");
    updateOperations.set("criteria.criteria", "< 0.2");

    Query<TimeSeriesThreshold> query = hPersistence.createQuery(TimeSeriesThreshold.class);
    query.filter(TimeSeriesThresholdKeys.metricType, APDEX);
    query.filter("criteria.type", "RATIO");

    UpdateResults updateResults = hPersistence.update(query, updateOperations);
    log.info("Update results count: " + updateResults.getUpdatedCount());

    updateOperations = hPersistence.createUpdateOperations(TimeSeriesThreshold.class);
    updateOperations.set("criteria.thresholdType", "ACT_WHEN_LOWER");
    updateOperations.set("criteria.criteria", "< 20");

    query = hPersistence.createQuery(TimeSeriesThreshold.class);
    query.filter(TimeSeriesThresholdKeys.metricType, APDEX);
    query.filter("criteria.type", "DELTA");

    updateResults = hPersistence.update(query, updateOperations);
    log.info("Update results count: " + updateResults.getUpdatedCount());
  }

  @Override
  public ChecklistItem whatHappensOnRollback() {
    return ChecklistItem.builder()
        .desc("Migration doesn't affect rollbacks, we still need updated values in the older versions")
        .build();
  }

  @Override
  public ChecklistItem whatHappensIfOldVersionIteratorPicksMigratedEntity() {
    return ChecklistItem.builder().desc("No iterator on this entity").build();
  }
}
