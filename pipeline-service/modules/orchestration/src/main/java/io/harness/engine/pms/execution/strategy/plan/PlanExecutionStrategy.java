/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.plan;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.Status.ERRORED;

import io.harness.ModuleType;
import io.harness.OrchestrationPublisherName;
import io.harness.PipelineSettingsService;
import io.harness.PlanExecutionSettingResponse;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.GovernanceService;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.events.OrchestrationEventEmitter;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.observers.OrchestrationEndObserver;
import io.harness.engine.observers.OrchestrationStartObserver;
import io.harness.engine.observers.beans.OrchestrationStartInfo;
import io.harness.engine.pms.execution.strategy.NodeExecutionStrategy;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.governance.GovernanceMetadata;
import io.harness.logging.AutoLogContext;
import io.harness.observer.Subject;
import io.harness.opaclient.model.OpaConstants;
import io.harness.plan.Node;
import io.harness.plan.Plan;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.OrchestrationEvent;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.springdata.TransactionHelper;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionStrategy implements NodeExecutionStrategy<Plan, PlanExecution, PlanExecutionMetadata> {
  public static final String ENFORCEMENT_CALLBACK_ID = "enforcement-%s";
  @Inject @Named("EngineExecutorService") private ExecutorService executorService;
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private OrchestrationEventEmitter eventEmitter;
  @Inject private TransactionHelper transactionHelper;
  @Inject private GovernanceService governanceService;
  @Inject private PlanService planService;

  @Inject private PipelineSettingsService pipelineSettingsService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject @Named(OrchestrationPublisherName.PUBLISHER_NAME) private String publisherName;

  @Getter private final Subject<OrchestrationStartObserver> orchestrationStartSubject = new Subject<>();
  @Getter private final Subject<OrchestrationEndObserver> orchestrationEndSubject = new Subject<>();
  @Inject private PlanExpansionService planExpansionService;

  @Override
  public PlanExecution runNode(@NonNull Ambiance ambiance, @NonNull Plan plan, PlanExecutionMetadata metadata) {
    return runNode(ambiance, plan, metadata, InitiateMode.CREATE_AND_START);
  }

  @Override
  public PlanExecution runNode(
      @NonNull Ambiance ambiance, @NonNull Plan plan, PlanExecutionMetadata metadata, InitiateMode initiateMode) {
    long startTs = System.currentTimeMillis();
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      String accountId = ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId);
      String orgIdentifier = ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.orgIdentifier);
      String projectIdentifier = ambiance.getSetupAbstractionsMap().get(SetupAbstractionKeys.projectIdentifier);
      String expandedPipelineJson = metadata.getExpandedPipelineJson();
      PlanExecution planExecution;
      PlanExecutionSettingResponse planExecutionSettingResponse =
          pipelineSettingsService.shouldQueuePlanExecution(accountId, ambiance.getMetadata().getPipelineIdentifier());
      GovernanceMetadata governanceMetadata = governanceService.evaluateGovernancePolicies(expandedPipelineJson,
          accountId, orgIdentifier, projectIdentifier, OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_RUN,
          ambiance.getPlanExecutionId(), ambiance.getMetadata().getHarnessVersion());

      planExecution = createPlanExecution(ambiance, metadata, governanceMetadata, planExecutionSettingResponse);
      if (governanceMetadata.getDeny()) {
        log.info(
            "Not starting the planExecution with planExecutionId: {} because the governance check denied the execution.",
            ambiance.getPlanExecutionId());
        return planExecutionService.markPlanExecutionErrored(ambiance.getPlanExecutionId());
      }

      // isNewFlow: for restrictions using the enforcements.
      if (planExecutionSettingResponse.isUseNewFlow() || planExecutionSettingResponse.isShouldQueue()) {
        // Attach a Callback so that if this finishes then next execution starts
        PlanExecutionResumeCallback callback = PlanExecutionResumeCallback.builder()
                                                   .accountIdIdentifier(accountId)
                                                   .orgIdentifier(orgIdentifier)
                                                   .projectIdentifier(projectIdentifier)
                                                   .pipelineIdentifier(ambiance.getMetadata().getPipelineIdentifier())
                                                   .build();

        waitNotifyEngine.waitForAllOn(
            publisherName, callback, String.format(ENFORCEMENT_CALLBACK_ID, planExecution.getUuid()));
      }

      if (!planExecutionSettingResponse.isShouldQueue()) {
        // Start the planExecution if it should not be queued.
        startPlanExecution(plan, ambiance);
      }
      return planExecution;
    } finally {
      log.info("[PMS_PlanExecution] Time taken to runNode plan in PlanExecutionStrategy: {} ",
          System.currentTimeMillis() - startTs);
    }
  }

  public boolean startPlanExecution(Plan plan, Ambiance ambiance) {
    Node planNode = planService.fetchNode(plan.getUuid(), plan.getStartingNodeId());
    if (planNode == null) {
      throw new InvalidRequestException("Starting node for plan cannot be null");
    }
    Ambiance cloned = AmbianceUtils.cloneForChild(ambiance, PmsLevelUtils.buildLevelFromNode(generateUuid(), planNode));
    executorService.submit(() -> orchestrationEngine.runNode(cloned, planNode, null));
    return true;
  }

  private PlanExecution createPlanExecution(Ambiance ambiance, PlanExecutionMetadata planExecutionMetadata,
      GovernanceMetadata governanceMetadata, PlanExecutionSettingResponse planExecutionSettingResponse) {
    // Will start the planExecution with running status if its not being queued.
    Status status = Status.RUNNING;
    if (planExecutionSettingResponse.isShouldQueue()) {
      status = Status.QUEUED;
    }
    PlanExecution planExecution = PlanExecution.builder()
                                      .uuid(ambiance.getPlanExecutionId())
                                      .planId(ambiance.getPlanId())
                                      .setupAbstractions(ambiance.getSetupAbstractionsMap())
                                      .status(status)
                                      .startTs(System.currentTimeMillis())
                                      .governanceMetadata(governanceMetadata)
                                      .metadata(ambiance.getMetadata())
                                      .ambiance(ambiance)
                                      .build();

    PlanExecution createdPlanExecution = transactionHelper.performTransaction(() -> {
      planExecutionMetadataService.save(planExecutionMetadata);
      planExpansionService.create(planExecution.getUuid());
      return planExecutionService.save(planExecution);
    });

    try {
      orchestrationStartSubject.fireInform(OrchestrationStartObserver::onStart,
          OrchestrationStartInfo.builder().ambiance(ambiance).planExecutionMetadata(planExecutionMetadata).build());
    } catch (Exception e) {
      // Marking the planExecution Errored if OrchestrationStartObservers failed.
      planExecutionService.markPlanExecutionErrored(ambiance.getPlanExecutionId());
      log.error("Not starting the PlanExecution:", e);
      throw e;
    }
    return createdPlanExecution;
  }

  @Override
  public void endNodeExecution(Ambiance ambiance) {
    Status status = planExecutionService.calculateStatus(ambiance.getPlanExecutionId());
    PlanExecution planExecution = planExecutionService.updateStatus(
        ambiance.getPlanExecutionId(), status, ops -> ops.set(PlanExecutionKeys.endTs, System.currentTimeMillis()));
    if (planExecution == null) {
      log.error("Cannot transition plan execution to status : {}", status);
      // TODO: Incorporate error handling
      planExecution = planExecutionService.updateStatus(
          ambiance.getPlanExecutionId(), ERRORED, ops -> ops.set(PlanExecutionKeys.endTs, System.currentTimeMillis()));
    }
    if (planExecution != null) {
      eventEmitter.emitEvent(buildEndEvent(ambiance, planExecution.getStatus()));
    }
    orchestrationEndSubject.fireInform(OrchestrationEndObserver::onEnd, ambiance);
  }

  private OrchestrationEvent buildEndEvent(Ambiance ambiance, Status status) {
    return OrchestrationEvent.newBuilder()
        .setAmbiance(ambiance)
        .setServiceName(ModuleType.PMS.name().toLowerCase())
        .setEventType(OrchestrationEventType.ORCHESTRATION_END)
        .setStatus(status)
        .build();
  }

  @Override
  public void handleError(Ambiance ambiance, Exception exception) {
    // TODO: Add implementation here
  }
}
