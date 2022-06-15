/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator.ServiceLevelIndicatorKeys;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective.ServiceLevelObjectiveKeys;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateResults;

@Slf4j
public class AddEnabledFlagToSLISLOMigration implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    log.info("Begin migration for updating SLI with enabled flag");
    Query<ServiceLevelIndicator> serviceLevelIndicatorQuery = hPersistence.createQuery(ServiceLevelIndicator.class);
    try (HIterator<ServiceLevelIndicator> sliIterator = new HIterator<>(serviceLevelIndicatorQuery.fetch())) {
      while (sliIterator.hasNext()) {
        ServiceLevelIndicator serviceLevelIndicator = sliIterator.next();
        UpdateResults updateResults = hPersistence.update(serviceLevelIndicator,
            hPersistence.createUpdateOperations(ServiceLevelIndicator.class)
                .set(ServiceLevelIndicatorKeys.enabled, true));
        log.info("Updated for SLI {}, {}, Update Result {}", serviceLevelIndicator.getProjectIdentifier(),
            serviceLevelIndicator.getIdentifier(), updateResults);
      }
    }

    log.info("Begin migration for updating SL0 with enabled flag");
    Query<ServiceLevelObjective> serviceLevelObjectiveQuery = hPersistence.createQuery(ServiceLevelObjective.class);
    try (HIterator<ServiceLevelObjective> sloIterator = new HIterator<>(serviceLevelObjectiveQuery.fetch())) {
      while (sloIterator.hasNext()) {
        ServiceLevelObjective serviceLevelObjective = sloIterator.next();
        UpdateResults updateResults = hPersistence.update(serviceLevelObjective,
            hPersistence.createUpdateOperations(ServiceLevelObjective.class)
                .set(ServiceLevelObjectiveKeys.enabled, true));
        log.info("Updated for SLO {}, {}, Update Result {}", serviceLevelObjective.getProjectIdentifier(),
            serviceLevelObjective.getIdentifier(), updateResults);
      }
    }
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
