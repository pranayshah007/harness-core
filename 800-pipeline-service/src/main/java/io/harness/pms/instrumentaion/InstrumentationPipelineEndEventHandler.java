package io.harness.pms.instrumentaion;

import static io.harness.instrumentation.ServiceInstrumentationConstants.ACTIVE_SERVICES_ACCOUNT_ID;
import static io.harness.instrumentation.ServiceInstrumentationConstants.ACTIVE_SERVICES_ACCOUNT_NAME;
import static io.harness.instrumentation.ServiceInstrumentationConstants.ACTIVE_SERVICES_COUNT;
import static io.harness.instrumentation.ServiceInstrumentationConstants.ACTIVE_SERVICES_COUNT_EVENT;
import static io.harness.instrumentation.ServiceInstrumentationConstants.ACTIVE_SERVICES_ORG_ID;
import static io.harness.instrumentation.ServiceInstrumentationConstants.ACTIVE_SERVICES_PIPELINE_ID;
import static io.harness.instrumentation.ServiceInstrumentationConstants.ACTIVE_SERVICES_PROJECT_ID;
import static io.harness.instrumentation.ServiceInstrumentationConstants.SERVICE_USED_ACCOUNT_ID;
import static io.harness.instrumentation.ServiceInstrumentationConstants.SERVICE_USED_ACCOUNT_NAME;
import static io.harness.instrumentation.ServiceInstrumentationConstants.SERVICE_USED_EVENT;
import static io.harness.instrumentation.ServiceInstrumentationConstants.SERVICE_USED_EVENT_PIPELINE_ID;
import static io.harness.instrumentation.ServiceInstrumentationConstants.SERVICE_USED_EXECUTION_ID;
import static io.harness.instrumentation.ServiceInstrumentationConstants.SERVICE_USED_ORG_ID;
import static io.harness.instrumentation.ServiceInstrumentationConstants.SERVICE_USED_PROJECT_ID;
import static io.harness.instrumentation.ServiceInstrumentationConstants.SERVICE_USED_SERVICE_NAME;
import static io.harness.instrumentation.ServiceInstrumentationConstants.SERVICE_USED_SERVICE_PIPELINE_ID;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.ACCOUNT_NAME;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.ERROR_MESSAGES;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.EVENT_TYPES;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.EXECUTION_TIME;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.FAILURE_TYPES;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.IS_RERUN;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.LEVEL;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.NOTIFICATION_METHODS;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.NOTIFICATION_RULES_COUNT;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.PIPELINE_EXECUTION;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.PIPELINE_NOTIFICATION;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.STAGE_COUNT;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.STAGE_TYPES;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.STATUS;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.TRIGGER_TYPE;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.observers.OrchestrationEndObserver;
import io.harness.execution.PlanExecution;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.notification.bean.NotificationRules;
import io.harness.notification.bean.PipelineEvent;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.notification.NotificationInstrumentationHelper;
import io.harness.pms.pipeline.observer.OrchestrationObserverUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class InstrumentationPipelineEndEventHandler implements OrchestrationEndObserver {
  @Inject TelemetryReporter telemetryReporter;
  @Inject PMSExecutionService pmsExecutionService;
  @Inject NotificationInstrumentationHelper notificationInstrumentationHelper;
  @Inject PlanExecutionService planExecutionService;
  @Inject AccountService accountService;

  @Override
  public void onEnd(Ambiance ambiance) {
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    AccountDTO accountDTO = accountService.getAccount(accountId);
    String accountName = accountDTO.getName();
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String pipelineId = ambiance.getMetadata().getPipelineIdentifier();
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(accountId, orgId, projectId, planExecutionId, false);

    List<NotificationRules> notificationRulesList =
        notificationInstrumentationHelper.getNotificationRules(planExecutionId, ambiance);

    Set<String> executedModules =
        OrchestrationObserverUtils.getExecutedModulesInPipeline(pipelineExecutionSummaryEntity);

    HashMap<String, Object> propertiesMap = new HashMap<>();
    propertiesMap.put(ACCOUNT_NAME, accountName);
    propertiesMap.put(STAGE_TYPES, executedModules);
    propertiesMap.put(TRIGGER_TYPE, pipelineExecutionSummaryEntity.getExecutionTriggerInfo().getTriggerType());
    propertiesMap.put(STATUS, pipelineExecutionSummaryEntity.getStatus());
    propertiesMap.put(LEVEL, StepCategory.PIPELINE);
    propertiesMap.put(IS_RERUN, pipelineExecutionSummaryEntity.getExecutionTriggerInfo().getIsRerun());
    propertiesMap.put(STAGE_COUNT, pipelineExecutionSummaryEntity.getLayoutNodeMap().size());
    propertiesMap.put(EXECUTION_TIME, getExecutionTimeInSeconds(pipelineExecutionSummaryEntity));
    propertiesMap.put(NOTIFICATION_RULES_COUNT, notificationRulesList.size());
    propertiesMap.put(FAILURE_TYPES,
        PipelineInstrumentationUtils.getFailureTypesFromPipelineExecutionSummary(pipelineExecutionSummaryEntity));
    propertiesMap.put(ERROR_MESSAGES,
        PipelineInstrumentationUtils.getErrorMessagesFromPipelineExecutionSummary(pipelineExecutionSummaryEntity));
    propertiesMap.put(
        NOTIFICATION_METHODS, notificationInstrumentationHelper.getNotificationMethodTypes(notificationRulesList));
    String identity = ambiance.getMetadata().getTriggerInfo().getTriggeredBy().getExtraInfoMap().get("email");
    telemetryReporter.sendTrackEvent(PIPELINE_EXECUTION, identity, accountId, propertiesMap,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL,
        TelemetryOption.builder().sendForCommunity(true).build());

    sendNotificationEvents(notificationRulesList, ambiance, accountId, accountName);

    Set<String> serviceNames = getServiceNamesForPipelineExecution(pipelineExecutionSummaryEntity);

    for (String serviceName : serviceNames) {
      sendServiceUsedInPipelineExecutionEvent(pipelineId, identity, accountId, accountName, orgId, projectId,
          planExecutionId, serviceName, pipelineExecutionSummaryEntity.getPipelineIdentifier());
    }

    sendCountOfDistinctActiveServicesEvent(pipelineId, identity, accountId, accountName, orgId, projectId);
  }

  @VisibleForTesting
  private void sendCountOfDistinctActiveServicesEvent(
      String pipelineId, String identity, String accountId, String accountName, String orgId, String projectId) {
    long currentTS = System.currentTimeMillis();
    long searchingPeriod = 30L * 24 * 60 * 60 * 1000; // 30 days
    List<PlanExecution> planExecutionList =
        planExecutionService.findAllByAccountIdAndOrgIdAndProjectIdAndLastUpdatedAtInBetweenTimestamps(
            accountId, orgId, projectId, currentTS - searchingPeriod, currentTS);

    Set<String> virtualServices = new HashSet<>();
    try {
      for (PlanExecution planExecution : planExecutionList) {
        PipelineExecutionSummaryEntity executionSummary = pmsExecutionService.getPipelineExecutionSummaryEntity(
            planExecution.getSetupAbstractions().get(SetupAbstractionKeys.accountId),
            planExecution.getSetupAbstractions().get(SetupAbstractionKeys.orgIdentifier),
            planExecution.getSetupAbstractions().get(SetupAbstractionKeys.projectIdentifier), planExecution.getUuid());

        virtualServices.addAll(getServiceNamesForPipelineExecution(executionSummary));
      }

      HashMap<String, Object> activeServicesCountPropMap = new HashMap<>();
      activeServicesCountPropMap.put(ACTIVE_SERVICES_COUNT, virtualServices.size());
      activeServicesCountPropMap.put(ACTIVE_SERVICES_ACCOUNT_ID, accountId);
      activeServicesCountPropMap.put(ACTIVE_SERVICES_ACCOUNT_NAME, accountName);
      activeServicesCountPropMap.put(ACTIVE_SERVICES_PROJECT_ID, projectId);
      activeServicesCountPropMap.put(ACTIVE_SERVICES_ORG_ID, orgId);
      activeServicesCountPropMap.put(ACTIVE_SERVICES_PIPELINE_ID, pipelineId);
      telemetryReporter.sendTrackEvent(ACTIVE_SERVICES_COUNT_EVENT, identity, accountId, activeServicesCountPropMap,
          Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL,
          TelemetryOption.builder().sendForCommunity(true).build());

    } catch (Exception e) {
      log.error(String.format(
          "Failed to read PipelineExecutionSummaries for PlanExecutions: [accountId=%s], [orgId=%s], [projectId=%s], [startTS=%s], [endTS=%s]",
          accountId, orgId, projectId, currentTS - searchingPeriod, currentTS));
    }
  }

  private void sendServiceUsedInPipelineExecutionEvent(String eventPipelineId, String identity, String accountId,
      String accountName, String orgId, String projectId, String planExecutionId, String serviceName,
      String servicePipelineId) {
    HashMap<String, Object> serviceUsedPropMap = new HashMap<>();
    serviceUsedPropMap.put(SERVICE_USED_EXECUTION_ID, planExecutionId);
    serviceUsedPropMap.put(SERVICE_USED_SERVICE_NAME, serviceName);
    serviceUsedPropMap.put(SERVICE_USED_ACCOUNT_ID, accountId);
    serviceUsedPropMap.put(SERVICE_USED_ACCOUNT_NAME, accountName);
    serviceUsedPropMap.put(SERVICE_USED_PROJECT_ID, projectId);
    serviceUsedPropMap.put(SERVICE_USED_ORG_ID, orgId);
    serviceUsedPropMap.put(SERVICE_USED_EVENT_PIPELINE_ID, eventPipelineId);
    serviceUsedPropMap.put(SERVICE_USED_SERVICE_PIPELINE_ID, servicePipelineId);
    telemetryReporter.sendTrackEvent(SERVICE_USED_EVENT, identity, accountId, serviceUsedPropMap,
        Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL,
        TelemetryOption.builder().sendForCommunity(true).build());
  }

  private Set<String> getServiceNamesForPipelineExecution(PipelineExecutionSummaryEntity executionSummary) {
    Set<String> virtualServices = new HashSet<>();
    if (executionSummary.getModuleInfo().size() > 0) {
      Document cdModuleInfo = executionSummary.getModuleInfo().get("cd");
      if (cdModuleInfo != null && EmptyPredicate.isNotEmpty(cdModuleInfo)) {
        List serviceIdentifiers = (List) cdModuleInfo.get("serviceIdentifiers");
        if (serviceIdentifiers != null && EmptyPredicate.isNotEmpty(serviceIdentifiers)) {
          for (int i = 0; i < serviceIdentifiers.size(); i++) {
            virtualServices.add(String.valueOf(serviceIdentifiers.get(i)));
          }
        }
      }
    }
    return virtualServices;
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
          TelemetryOption.builder().sendForCommunity(true).build());
    }
  }

  private Long getExecutionTimeInSeconds(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    return (pipelineExecutionSummaryEntity.getEndTs() - pipelineExecutionSummaryEntity.getStartTs()) / 1000;
  }
}
