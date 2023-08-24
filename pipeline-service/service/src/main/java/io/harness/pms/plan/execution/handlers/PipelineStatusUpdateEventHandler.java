/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;

import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.events.OrchestrationEventEmitter;
import io.harness.engine.execution.PipelineStageResponseData;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.observers.OrchestrationEndObserver;
import io.harness.engine.observers.PlanStatusUpdateObserver;
import io.harness.execution.PlanExecution;
import io.harness.metrics.PipelineMetricUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.OrchestrationEvent;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.notification.orchestration.helpers.AbortInfoHelper;
import io.harness.pms.pipeline.observer.OrchestrationObserverUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Optional;
import java.util.Set;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineStatusUpdateEventHandler implements PlanStatusUpdateObserver, OrchestrationEndObserver {
  private static final String PIPELINE_EXECUTION_END_COUNT = "pipeline_execution_end_count";

  private final PlanExecutionService planExecutionService;
  private final PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  private final PipelineMetricUtils pipelineMetricUtils;
  private OrchestrationEventEmitter eventEmitter;
  private WaitNotifyEngine waitNotifyEngine;
  private AbortInfoHelper abortInfoHelper;

  @Inject
  public PipelineStatusUpdateEventHandler(PlanExecutionService planExecutionService,
      PmsExecutionSummaryRepository pmsExecutionSummaryRepository, PipelineMetricUtils pipelineMetricUtils,
      OrchestrationEventEmitter eventEmitter, WaitNotifyEngine waitNotifyEngine, AbortInfoHelper abortInfoHelper) {
    this.planExecutionService = planExecutionService;
    this.pmsExecutionSummaryRepository = pmsExecutionSummaryRepository;
    this.eventEmitter = eventEmitter;
    this.waitNotifyEngine = waitNotifyEngine;
    this.abortInfoHelper = abortInfoHelper;
    this.pipelineMetricUtils = pipelineMetricUtils;
  }

  @Override
  public void onPlanStatusUpdate(Ambiance ambiance) {
    String planExecutionId = ambiance.getPlanExecutionId();
    PlanExecution planExecution = planExecutionService.get(planExecutionId);

    ExecutionStatus status = ExecutionStatus.getExecutionStatus(planExecution.getStatus());

    Update update = new Update();

    update.set(PlanExecutionSummaryKeys.internalStatus, planExecution.getStatus());
    update.set(PlanExecutionSummaryKeys.status, status);
    if (status == ExecutionStatus.ABORTED) {
      update.set(PlanExecutionSummaryKeys.abortedBy, abortInfoHelper.fetchAbortedByInfoFromInterrupts(planExecutionId));
    }
    if (StatusUtils.isFinalStatus(status.getEngineStatus())) {
      update.set(PlanExecutionSummaryKeys.endTs, planExecution.getEndTs());
    }

    Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(planExecutionId);
    Query query = new Query(criteria);
    pmsExecutionSummaryRepository.update(query, update);

    performActionOnTerminalStatus(status.getEngineStatus(), planExecutionId);
  }

  private void performActionOnTerminalStatus(Status status, String planExecutionId) {
    if (StatusUtils.isFinalStatus(status)) {
      // This is required to notify parent pipeline in Pipeline chaining
      waitNotifyEngine.doneWith(planExecutionId, PipelineStageResponseData.builder().status(status).build());
    }
  }

  @Override
  public void onEnd(Ambiance ambiance, Status endStatus) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    Optional<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntity =
        pmsExecutionSummaryRepository
            .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndPipelineDeletedNot(accountId,
                AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance),
                ambiance.getPlanExecutionId(), true);
    if (pipelineExecutionSummaryEntity.isPresent()) {
      Set<String> executedModules =
          OrchestrationObserverUtils.getExecutedModulesInPipeline(pipelineExecutionSummaryEntity.get());
      Update update = new Update();
      update.set(PlanExecutionSummaryKeys.executedModules, executedModules);
      Criteria criteria = Criteria.where(PlanExecutionSummaryKeys.planExecutionId).is(ambiance.getPlanExecutionId());
      Query query = new Query(criteria);
      PipelineExecutionSummaryEntity pipelineExecutionSummaryUpdatedEntity =
          pmsExecutionSummaryRepository.update(query, update);
      for (String module : executedModules) {
        if (!module.equalsIgnoreCase(ModuleType.PMS.name())) {
          eventEmitter.emitEvent(
              buildEndEvent(ambiance, module, pipelineExecutionSummaryUpdatedEntity.getStatus().getEngineStatus(),
                  pipelineExecutionSummaryUpdatedEntity.getModuleInfo().get(module),
                  pipelineExecutionSummaryUpdatedEntity.getEndTs()));
        }
      }

      // Update pipeline execution metrics
      pipelineMetricUtils.publishPipelineExecutionMetrics(
          PIPELINE_EXECUTION_END_COUNT, pipelineExecutionSummaryUpdatedEntity.getStatus().getEngineStatus(), accountId);
    }
  }

  private OrchestrationEvent buildEndEvent(
      Ambiance ambiance, String module, Status status, Document moduleInfo, long endTs) {
    return OrchestrationEvent.newBuilder()
        .setAmbiance(ambiance)
        .setServiceName(module)
        .setEventType(OrchestrationEventType.ORCHESTRATION_END)
        .setModuleInfo(ByteString.copyFromUtf8(emptyIfNull(RecastOrchestrationUtils.toJson(moduleInfo))))
        .setStatus(status)
        .setEndTs(endTs)
        .build();
  }
}
