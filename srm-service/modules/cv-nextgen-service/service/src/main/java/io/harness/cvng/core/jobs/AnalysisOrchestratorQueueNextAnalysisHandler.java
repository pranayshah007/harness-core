/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.mongo.iterator.MongoPersistenceIterator;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.List;

public class AnalysisOrchestratorQueueNextAnalysisHandler
    implements MongoPersistenceIterator.Handler<AnalysisOrchestrator> {
  private static final List<VerificationTask.TaskType> queueAnalysisFortaskTypeList =
      List.of(VerificationTask.TaskType.COMPOSITE_SLO);

  @Inject VerificationTaskService verificationTaskService;

  @Inject OrchestrationService orchestrationService;
  @Override
  public void handle(AnalysisOrchestrator entity) {
    VerificationTask verificationTask = verificationTaskService.get(entity.getVerificationTaskId());
    if (shouldQueueAnalysisForTaskType(verificationTask)) {
      orchestrationService.queueAnalysisWithoutEventPublish(entity.getAccountId(),
          AnalysisInput.builder()
              .verificationTaskId(entity.getVerificationTaskId())
              .startTime(Instant.now())
              .endTime(Instant.now())
              .build());
    }
  }

  private boolean shouldQueueAnalysisForTaskType(VerificationTask verificationTask) {
    return queueAnalysisFortaskTypeList.contains(verificationTask.getTaskInfo().getTaskType());
  }
}
