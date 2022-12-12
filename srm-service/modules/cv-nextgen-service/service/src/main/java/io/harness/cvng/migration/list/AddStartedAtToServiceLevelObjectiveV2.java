/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class AddStartedAtToServiceLevelObjectiveV2 implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    log.info("Begin migration for adding startedAt to ServiceLevelObjectivesV2");
    Query<AbstractServiceLevelObjective> serviceLevelObjectiveQuery =
        hPersistence.createQuery(AbstractServiceLevelObjective.class)
            .field(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.startedAt)
            .doesNotExist();

    try (HIterator<AbstractServiceLevelObjective> iterator = new HIterator<>(serviceLevelObjectiveQuery.fetch())) {
      while (iterator.hasNext()) {
        try {
          AbstractServiceLevelObjective serviceLevelObjective = iterator.next();
          long startedAt = serviceLevelObjective.getCreatedAt();
          Query<AbstractServiceLevelObjective> query =
              hPersistence.createQuery(AbstractServiceLevelObjective.class)
                  .filter(
                      AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.uuid, serviceLevelObjective.getUuid());
          UpdateOperations<AbstractServiceLevelObjective> updateOperations =
              hPersistence.createUpdateOperations(AbstractServiceLevelObjective.class)
                  .set(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.startedAt, startedAt);
          hPersistence.update(query, updateOperations);
        } catch (Exception ex) {
          log.error("Exception occurred while adding startedAt to ServiceLevelObjectivesV2", ex);
        }
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
