/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.cvng.statemachine.beans.AnalysisOrchestratorStatus;
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
public class FixOrchestratorStatusMigration implements CVNGMigration {
  @Inject private HPersistence hPersistence;
  @Inject private VerificationTaskService verificationTaskService;

  @Override
  public void migrate() {
    log.info("Begin migration for updating the status of Orchestrator");
    Query<AnalysisOrchestrator> orchestratorQuery =
        hPersistence.createQuery(AnalysisOrchestrator.class)
            .filter(AnalysisOrchestratorKeys.status, AnalysisOrchestratorStatus.CREATED);
    List<AnalysisOrchestrator> orchestratorsToUpdate = new ArrayList<>();
    try (HIterator<AnalysisOrchestrator> iterator = new HIterator<>(orchestratorQuery.fetch())) {
      while (iterator.hasNext()) {
        AnalysisOrchestrator orchestrator = iterator.next();
        if (isEmpty(orchestrator.getAnalysisStateMachineQueue())) {
          orchestrator.setStatus(AnalysisOrchestratorStatus.RUNNING);
          orchestratorsToUpdate.add(orchestrator);
        }
      }
    }
    log.info("Updating {} orchestrators to runing state from created state", orchestratorsToUpdate.size());
    hPersistence.save(orchestratorsToUpdate);
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
