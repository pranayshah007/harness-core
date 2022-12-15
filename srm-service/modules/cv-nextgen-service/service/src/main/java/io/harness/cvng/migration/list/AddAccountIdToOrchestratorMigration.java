/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator.AnalysisOrchestratorKeys;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class AddAccountIdToOrchestratorMigration implements CVNGMigration {
  @Inject private HPersistence hPersistence;
  @Inject private VerificationTaskService verificationTaskService;

  @Override
  public void migrate() {
    log.info("Begin migration for adding accountId to Orchestrator");
    Query<AnalysisOrchestrator> orchestratorQuery =
        hPersistence.createQuery(AnalysisOrchestrator.class).field(AnalysisOrchestratorKeys.accountId).doesNotExist();

    try (HIterator<AnalysisOrchestrator> iterator = new HIterator<>(orchestratorQuery.fetch())) {
      while (iterator.hasNext()) {
        try {
          AnalysisOrchestrator orchestrator = iterator.next();
          String accountId = verificationTaskService.get(orchestrator.getVerificationTaskId()).getAccountId();
          Query<AnalysisOrchestrator> query = hPersistence.createQuery(AnalysisOrchestrator.class)
                                                  .filter(AnalysisOrchestratorKeys.uuid, orchestrator.getUuid());
          UpdateOperations<AnalysisOrchestrator> updateOperations =
              hPersistence.createUpdateOperations(AnalysisOrchestrator.class)
                  .set(AnalysisOrchestratorKeys.accountId, accountId);
          hPersistence.update(query, updateOperations);
        } catch (Exception ex) {
          log.error("Exception occurred while adding accountId to orchestrator", ex);
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
