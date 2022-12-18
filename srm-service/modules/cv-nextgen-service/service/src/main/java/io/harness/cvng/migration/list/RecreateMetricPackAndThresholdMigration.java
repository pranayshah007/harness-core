/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.entities.TimeSeriesThreshold;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Entity;

@Slf4j
public class RecreateMetricPackAndThresholdMigration implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    log.info("dropping collection {}", MetricPack.class.getAnnotation(Entity.class).value());
    hPersistence.getCollection(MetricPack.class).drop();
    log.info("dropping collection {}", TimeSeriesThreshold.class.getAnnotation(Entity.class).value());
    hPersistence.getCollection(TimeSeriesThreshold.class).drop();
    log.info("migration done");
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
