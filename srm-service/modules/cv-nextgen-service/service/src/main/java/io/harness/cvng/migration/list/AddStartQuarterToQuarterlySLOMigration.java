/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.servicelevelobjective.beans.QuarterStart;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.QuarterlyCalenderTarget;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.persistence.HQuery;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddStartQuarterToQuarterlySLOMigration implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;

  @Override
  public void migrate() {
    log.info("Beginning migration for adding startQuarter field To Quarterly SLOs");
    Query<AbstractServiceLevelObjective> abstractServiceLevelObjectiveQuery =
        hPersistence.createQuery(AbstractServiceLevelObjective.class, HQuery.excludeAuthority)
            .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.target,
                QuarterlyCalenderTarget.builder().build());
    try (HIterator<AbstractServiceLevelObjective> iterator =
             new HIterator<>(abstractServiceLevelObjectiveQuery.fetch())) {
      while (iterator.hasNext()) {
        AbstractServiceLevelObjective serviceLevelObjective = iterator.next();
        UpdateOperations<AbstractServiceLevelObjective> updateOperations =
            hPersistence.createUpdateOperations(AbstractServiceLevelObjective.class);
        try {
          updateOperations.set(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.target,
              QuarterlyCalenderTarget.builder().quarterStart(QuarterStart.JAN_APR_JUL_OCT).build());
          hPersistence.update(serviceLevelObjective, updateOperations);
        } catch (Exception e) {
          log.error("Couldn't add startQuarter field for SLO with sloId {}. Exception Caused: {}",
              serviceLevelObjective.getUuid(), e.getMessage());
        }
      }
    }
    log.info("Updated Quarterly SLOs with startQuarter field");
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
