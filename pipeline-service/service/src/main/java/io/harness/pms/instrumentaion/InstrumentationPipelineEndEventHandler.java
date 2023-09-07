/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.instrumentaion;

import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.ACCOUNT_NAME;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.ERROR_MESSAGES;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.EVENT_TYPES;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.EXCEPTION_MESSAGE;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.EXECUTION_TIME;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.FAILED_STEPS;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.FAILED_STEP_TYPES;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.FAILURE_TYPES;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.IS_RERUN;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.LEVEL;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.NOTIFICATION_METHODS;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.NOTIFICATION_RULES_COUNT;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.ORG_IDENTIFIER;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.PIPELINE_EXECUTION;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.PIPELINE_NOTIFICATION;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.PLAN_EXECUTION_ID;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.PROJECT_IDENTIFIER;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.STAGE_COUNT;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.STAGE_TYPES;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.STATUS;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.STEP_COUNT;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.STEP_TYPES;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.TRIGGER_TYPE;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.observers.OrchestrationEndObserver;
import io.harness.execution.NodeExecution;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.notification.bean.NotificationRules;
import io.harness.notification.bean.PipelineEvent;
import io.harness.observer.AsyncInformObserver;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.notification.NotificationInstrumentationHelper;
import io.harness.pms.pipeline.observer.OrchestrationObserverUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.sdk.SdkStepHelper;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.CloseableIterator;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class InstrumentationPipelineEndEventHandler implements OrchestrationEndObserver, AsyncInformObserver {
  @Inject TelemetryReporter telemetryReporter;
  @Inject PMSExecutionService pmsExecutionService;
  @Inject PmsExecutionSummaryService pmsExecutionSummaryService;
  @Inject NotificationInstrumentationHelper notificationInstrumentationHelper;
  @Inject AccountService accountService;
  @Inject @Named("PipelineExecutorService") ExecutorService executorService;
  @Inject NodeExecutionService nodeExecutionService;
  @Inject SdkStepHelper sdkStepHelper;

  @Override
  public void onEnd(Ambiance ambiance, Status endStatus) {
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    AccountDTO accountDTO = accountService.getAccount(accountId);
    String accountName = accountDTO.getName();
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    Set<String> allSdkSteps = sdkStepHelper.getAllStepVisibleInUI();

    List<String> stepTypes = new LinkedList<>();
    List<String> failedSteps = new LinkedList<>();
    List<String> failedStepTypes = new LinkedList<>();

    try (CloseableIterator<NodeExecution> iterator = nodeExecutionService.fetchAllStepNodeExecutions(
             planExecutionId, NodeProjectionUtils.fieldsForInstrumentationHandler)) {
      while (iterator.hasNext()) {
        NodeExecution currentNodeExecution = iterator.next();

        String currentStepType = currentNodeExecution.getStepType().getType();
        if (allSdkSteps.contains(currentStepType)) {
          stepTypes.add(currentStepType);
          // If step is in broken status then only add to results
          if (StatusUtils.brokeStatuses().contains(currentNodeExecution.getStatus())) {
            // Add step identifier
            failedSteps.add(currentNodeExecution.getIdentifier());
            failedStepTypes.add(currentStepType);
          }
        }
      }
    }

    // TODO(Projection)
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(accountId, orgId, projectId, planExecutionId, false);

    List<NotificationRules> notificationRulesList =
        notificationInstrumentationHelper.getNotificationRules(planExecutionId, ambiance);

    Set<String> executedModules =
        OrchestrationObserverUtils.getExecutedModulesInPipeline(pipelineExecutionSummaryEntity);

    HashMap<String, Object> propertiesMap = new HashMap<>();
    propertiesMap.put(ACCOUNT_NAME, accountName);
    propertiesMap.put(PROJECT_IDENTIFIER, projectId);
    propertiesMap.put(ORG_IDENTIFIER, orgId);
    propertiesMap.put(PLAN_EXECUTION_ID, planExecutionId);
    propertiesMap.put(STAGE_TYPES, executedModules);
    // step types
    propertiesMap.put(TRIGGER_TYPE, pipelineExecutionSummaryEntity.getExecutionTriggerInfo().getTriggerType());
    propertiesMap.put(STATUS, pipelineExecutionSummaryEntity.getStatus());
    propertiesMap.put(LEVEL, StepCategory.PIPELINE);
    propertiesMap.put(IS_RERUN, pipelineExecutionSummaryEntity.getExecutionTriggerInfo().getIsRerun());
    propertiesMap.put(STAGE_COUNT, pipelineExecutionSummaryEntity.getLayoutNodeMap().size());
    propertiesMap.put(STEP_TYPES, new HashSet<>(stepTypes));
    propertiesMap.put(FAILED_STEPS, failedSteps);
    propertiesMap.put(FAILED_STEP_TYPES, failedStepTypes);
    propertiesMap.put(STEP_COUNT, stepTypes.size());
    propertiesMap.put(EXECUTION_TIME, getExecutionTimeInSeconds(pipelineExecutionSummaryEntity));
    propertiesMap.put(NOTIFICATION_RULES_COUNT, notificationRulesList.size());
    propertiesMap.put(FAILURE_TYPES,
        PipelineInstrumentationUtils.getFailureTypesFromPipelineExecutionSummary(pipelineExecutionSummaryEntity));
    propertiesMap.put(ERROR_MESSAGES,
        PipelineInstrumentationUtils.getErrorMessagesFromPipelineExecutionSummary(pipelineExecutionSummaryEntity));
    propertiesMap.put(
        EXCEPTION_MESSAGE, PipelineInstrumentationUtils.extractExceptionMessage(pipelineExecutionSummaryEntity));
    propertiesMap.put(
        NOTIFICATION_METHODS, notificationInstrumentationHelper.getNotificationMethodTypes(notificationRulesList));
    String identity = ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getExtraInfoMap().get("email");
    telemetryReporter.sendTrackEvent(PIPELINE_EXECUTION, identity, accountId, propertiesMap,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL,
        TelemetryOption.builder().sendForCommunity(false).build());

    sendNotificationEvents(notificationRulesList, ambiance, accountId, accountName);
  }

  // TODO: Handle forStages case in PipelineEvents
  private void sendNotificationEvents(
      List<NotificationRules> notificationRulesList, Ambiance ambiance, String accountId, String accountName) {
    for (NotificationRules notificationRules : notificationRulesList) {
      HashMap<String, Object> propertiesMap = new HashMap<>();
      propertiesMap.put(EVENT_TYPES,
          notificationRules.getPipelineEvents().stream().map(PipelineEvent::getType).collect(Collectors.toSet()));
      propertiesMap.put(ACCOUNT_NAME, accountName);
      String email = PipelineInstrumentationUtils.getIdentityFromAmbiance(ambiance);
      telemetryReporter.sendTrackEvent(PIPELINE_NOTIFICATION, email, accountId, propertiesMap,
          Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL,
          TelemetryOption.builder().sendForCommunity(false).build());
    }
  }

  private Long getExecutionTimeInSeconds(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    return (pipelineExecutionSummaryEntity.getEndTs() - pipelineExecutionSummaryEntity.getStartTs()) / 1000;
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }
}
