/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.transformer.SLOTargetTransformerOldAndNew;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MigrateSLOTargetInSLOV2 implements CVNGMigration {
  @Inject HPersistence hPersistence;

  @Override
  public void migrate() {
    log.info("Begin migration for reading from SLOTarget in SLOV2 entity and write to target field in the same entity");
    List<AbstractServiceLevelObjective> serviceLevelObjectives =
        hPersistence.createQuery(AbstractServiceLevelObjective.class, excludeAuthority)
            .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.target, null)
            .asList();
    for (AbstractServiceLevelObjective serviceLevelObjective : serviceLevelObjectives) {
      UpdateOperations<AbstractServiceLevelObjective> updateOperations =
          hPersistence.createUpdateOperations(AbstractServiceLevelObjective.class)
              .set(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.target,
                  SLOTargetTransformerOldAndNew.getNewSLOtargetFromOldSLOtarget(serviceLevelObjective.getSloTarget()));
      hPersistence.update(serviceLevelObjective, updateOperations);
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
