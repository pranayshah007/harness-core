/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.statemachine.beans.AnalysisOrchestratorStatus;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator.AnalysisOrchestratorKeys;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;

@Slf4j
public class DeleteInvalidOrchestratorsMigration implements CVNGMigration {
  @Inject private HPersistence hPersistence;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private OrchestrationService orchestrationService;

  @Override
  public void migrate() {
    Query<AnalysisOrchestrator> orchestratorQuery =
        hPersistence.createQuery(AnalysisOrchestrator.class)
            .filter(AnalysisOrchestratorKeys.status, AnalysisOrchestratorStatus.RUNNING);
    Set<String> deletedVerificationTasks = new HashSet<>();
    try (HIterator<AnalysisOrchestrator> iterator = new HIterator<>(orchestratorQuery.fetch())) {
      while (iterator.hasNext()) {
        AnalysisOrchestrator orchestrator = iterator.next();
        VerificationTask verificationTask =
            hPersistence.get(VerificationTask.class, orchestrator.getVerificationTaskId());
        if (verificationTask == null) {
          deletedVerificationTasks.add(orchestrator.getVerificationTaskId());
        }
      }
    }
    log.info("{} invalid orchestrators are being marked as done", deletedVerificationTasks.size());
    orchestrationService.markCompleted(deletedVerificationTasks);
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
