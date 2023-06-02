/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.timescale;

import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.services.api.SLOTimeScaleService;
import io.harness.migration.NGMigration;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MigrateSLOtoTimeScaleDb implements NGMigration {
  @Inject TimeScaleDBService timeScaleDBService;

  @Inject private HPersistence hPersistence;

  @Inject SLOTimeScaleService sloTimeScaleService;

  @Override
  public void migrate() {
    if (timeScaleDBService.isValid()) {
      Query<AbstractServiceLevelObjective> serviceLevelObjectiveQuery =
          hPersistence.createQuery(AbstractServiceLevelObjective.class);
      try (HIterator<AbstractServiceLevelObjective> iterator = new HIterator<>(serviceLevelObjectiveQuery.fetch())) {
        while (iterator.hasNext()) {
          sloTimeScaleService.upsertServiceLevelObjective(iterator.next());
        }
      }
      Query<SLOHealthIndicator> sloHealthIndicator = hPersistence.createQuery(SLOHealthIndicator.class);
      try (HIterator<SLOHealthIndicator> iterator = new HIterator<>(sloHealthIndicator.fetch())) {
        while (iterator.hasNext()) {
          sloTimeScaleService.upsertSloHealthIndicator(iterator.next());
        }
      }
    }
  }
}
