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
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator.AnalysisOrchestratorKeys;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class DeleteOldAnalysisOrchestratorMigration implements CVNGMigration {
  @Inject private HPersistence hPersistence;
  @Inject private VerificationTaskService verificationTaskService;
  @Override
  public void migrate() {
    log.info("Begin migration for updating the status of Orchestrator");
    Query<AnalysisOrchestrator> orchestratorQuery =
        hPersistence.createQuery(AnalysisOrchestrator.class)
            .field(AnalysisOrchestratorKeys.status)
            .in(Arrays.asList(AnalysisStatus.CREATED, AnalysisStatus.RUNNING))
            .field(AnalysisOrchestratorKeys.createdAt)
            .lessThan(Instant.now().minus(Duration.ofDays(2)).toEpochMilli());
    try (HIterator<AnalysisOrchestrator> iterator = new HIterator<>(orchestratorQuery.fetch())) {
      while (iterator.hasNext()) {
        AnalysisOrchestrator orchestrator = iterator.next();
        log.info("Trying to update {}", orchestrator);
        Optional<VerificationTask> verificationTaskOptional =
            verificationTaskService.maybeGet(orchestrator.getVerificationTaskId());
        if ((verificationTaskOptional.isPresent()
                && verificationTaskOptional.get().getTaskInfo().getTaskType() == VerificationTask.TaskType.DEPLOYMENT)
            || !verificationTaskOptional.isPresent()) {
          UpdateOperations<AnalysisOrchestrator> updateOperations =
              hPersistence.createUpdateOperations(AnalysisOrchestrator.class)
                  .set(AnalysisOrchestratorKeys.status, AnalysisOrchestratorStatus.COMPLETED);
          hPersistence.update(orchestrator, updateOperations);
          log.info("Updated analysis status to completed for {}", orchestrator);
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
