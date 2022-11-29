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
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class DeleteOrchestratorWithInvalidVerificationTaskId implements CVNGMigration {
  @Inject private HPersistence hPersistence;
  @Inject private VerificationTaskService verificationTaskService;

  @Override
  public void migrate() {
    log.info("Begin migration for deleting invalid orchestrators");
    Query<AnalysisOrchestrator> orchestratorQuery = hPersistence.createQuery(AnalysisOrchestrator.class);
    List<String> analysisOrchestratorsToDelete = new ArrayList<>();
    try (HIterator<AnalysisOrchestrator> iterator = new HIterator<>(orchestratorQuery.fetch())) {
      while (iterator.hasNext()) {
        AnalysisOrchestrator orchestrator = iterator.next();
        if (!verificationTaskService.maybeGet(orchestrator.getVerificationTaskId()).isPresent()) {
          log.info("Going to delete orchestrator: ", orchestrator.getVerificationTaskId());
          analysisOrchestratorsToDelete.add(orchestrator.getVerificationTaskId());
        }
      }
    }
    log.info("Deleting {} orchestrator's to running state from created state", analysisOrchestratorsToDelete.size());
    hPersistence.deleteOnServer(hPersistence.createQuery(AnalysisOrchestrator.class)
                                    .field(AnalysisOrchestratorKeys.verificationTaskId)
                                    .in(analysisOrchestratorsToDelete));
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
