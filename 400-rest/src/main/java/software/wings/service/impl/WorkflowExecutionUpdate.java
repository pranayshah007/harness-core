/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.mongo.MongoUtils.setUnset;

import static software.wings.sm.StateType.PHASE;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.beans.EventPayload;
import io.harness.beans.EventType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;
import io.harness.beans.event.cg.CgPipelineCompletePayload;
import io.harness.beans.event.cg.CgPipelinePausePayload;
import io.harness.beans.event.cg.CgPipelineResumePayload;
import io.harness.beans.event.cg.CgWorkflowCompletePayload;
import io.harness.beans.event.cg.CgWorkflowPausePayload;
import io.harness.beans.event.cg.CgWorkflowResumePayload;
import io.harness.beans.event.cg.application.ApplicationEventData;
import io.harness.beans.event.cg.entities.EnvironmentEntity;
import io.harness.beans.event.cg.entities.InfraDefinitionEntity;
import io.harness.beans.event.cg.entities.ServiceEntity;
import io.harness.beans.event.cg.pipeline.ExecutionArgsEventData;
import io.harness.beans.event.cg.pipeline.PipelineEventData;
import io.harness.beans.event.cg.pipeline.PipelineExecData;
import io.harness.beans.event.cg.pipeline.PipelineStageInfo;
import io.harness.beans.event.cg.workflow.WorkflowEventData;
import io.harness.beans.event.cg.workflow.WorkflowExecData;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.event.handler.impl.segment.SegmentHandler;
import io.harness.event.usagemetrics.UsageMetricsEventPublisher;
import io.harness.event.usagemetrics.UsageMetricsHelper;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.queue.QueuePublisher;
import io.harness.service.EventService;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.EnvSummary;
import software.wings.beans.HarnessTagLink;
import software.wings.beans.NameValuePair;
import software.wings.beans.PipelineStageExecution;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.workflow.WorkflowNotificationHelper;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.BarrierService;
import software.wings.service.intfc.HarnessTagService;
import software.wings.service.intfc.ResourceConstraintService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.PipelineSummary;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;
import software.wings.sm.StateMachineExecutionCallback;
import software.wings.sm.states.EnvState.EnvExecutionResponseData;
import software.wings.sm.status.StateStatusUpdateInfo;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import io.fabric8.utils.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * The Class WorkflowExecutionUpdate.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WorkflowExecutionUpdate implements StateMachineExecutionCallback {
  private static final Pattern wingsVariablePattern = Pattern.compile("\\$\\{[^{}]*}");
  private static final String UNRESOLVED = "unresolved";

  private String appId;
  private String workflowExecutionId;
  private boolean needToNotifyPipeline;

  @Inject private WingsPersistence wingsPersistence;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private WorkflowNotificationHelper workflowNotificationHelper;
  @Inject private EventService eventService;
  @Inject private QueuePublisher<ExecutionEvent> executionEventQueue;
  @Inject private AlertService alertService;
  @Inject private TriggerService triggerService;
  @Inject private transient AppService appService;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private ResourceConstraintService resourceConstraintService;
  @Inject private BarrierService barrierService;
  @Inject private EventPublishHelper eventPublishHelper;
  @Inject private UsageMetricsEventPublisher usageMetricsEventPublisher;
  @Inject private AccountService accountService;
  @Inject private UsageMetricsHelper usageMetricsHelper;
  @Inject private SegmentHandler segmentHandler;
  @Inject private HarnessTagService harnessTagService;

  /**
   * Instantiates a new workflow execution update.
   */
  WorkflowExecutionUpdate() {}

  /**
   * Instantiates a new workflow execution update.
   *
   * @param appId               the app id
   * @param workflowExecutionId the workflow execution id
   */
  WorkflowExecutionUpdate(String appId, String workflowExecutionId) {
    this.appId = appId;
    this.workflowExecutionId = workflowExecutionId;
  }

  @UtilityClass
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static final class Keys {
    private static final String SUCCESS = "Deployment Succeeded";
    private static final String REJECTED = "Deployment Rejected";
    private static final String RUNNING = "Deployment Running";
    private static final String PAUSED = "Deployment Paused";
    private static final String EXPIRED = "Deployment Expired";
    private static final String ABORTED = "Deployment Aborted";
    private static final String FAILED = "Deployment Failed";
    private static final String MODULE = "module";
    private static final String DEPLOYMENT = "Deployment";
    private static final String PRODUCTION = "production";
    private static final String DEPLOYMENT_ID = "deployment_id";
    private static final String SERVICE_ID = "service_id";
    private static final String DEPLOYMENT_TYPE = "deployment_type";
    private static final String WORKFLOW = "workflow";
  }

  /**
   * Gets app id.
   *
   * @return the app id
   */
  public String getAppId() {
    return appId;
  }

  /**
   * Sets app id.
   *
   * @param appId the app id
   */
  public void setAppId(String appId) {
    this.appId = appId;
  }

  /**
   * Gets workflow execution id.
   *
   * @return the workflow execution id
   */
  public String getWorkflowExecutionId() {
    return workflowExecutionId;
  }

  /**
   * Sets workflow execution id.
   *
   * @param workflowExecutionId the workflow execution id
   */
  public void setWorkflowExecutionId(String workflowExecutionId) {
    this.workflowExecutionId = workflowExecutionId;
  }

  private static final FindAndModifyOptions callbackFindAndModifyOptions = new FindAndModifyOptions().returnNew(false);

  public StateExecutionInstance getRollbackInstance(String workflowExecutionId) {
    return wingsPersistence.createQuery(StateExecutionInstance.class)
        .filter(StateExecutionInstanceKeys.executionUuid, workflowExecutionId)
        .filter(StateExecutionInstanceKeys.stateType, PHASE.name())
        .filter(StateExecutionInstanceKeys.rollback, true)
        .order(Sort.ascending(StateExecutionInstanceKeys.createdAt))
        .project(StateExecutionInstanceKeys.startTs, true)
        .get();
  }

  @Override
  public void callback(ExecutionContext context, ExecutionStatus status, Exception ex) {
    final WorkflowExecution execution = wingsPersistence.createQuery(WorkflowExecution.class)
                                            .filter(WorkflowExecutionKeys.appId, appId)
                                            .filter(WorkflowExecutionKeys.uuid, workflowExecutionId)
                                            .get();

    Long startTs = execution == null ? null : execution.getStartTs();
    Long endTs = startTs == null ? null : System.currentTimeMillis();
    Long duration = startTs == null ? null : endTs - startTs;

    final StateExecutionInstance rollbackInstance = getRollbackInstance(workflowExecutionId);
    Long rollbackStartTs = rollbackInstance == null ? null : rollbackInstance.getStartTs();
    Long rollbackDuration = rollbackStartTs == null ? null : endTs - rollbackStartTs;

    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.appId, appId)
                                         .filter(WorkflowExecutionKeys.uuid, workflowExecutionId)
                                         .field(WorkflowExecutionKeys.status)
                                         .in(ExecutionStatus.activeStatuses());

    UpdateOperations<WorkflowExecution> updateOps =
        wingsPersistence.createUpdateOperations(WorkflowExecution.class).set(WorkflowExecutionKeys.status, status);
    setUnset(updateOps, WorkflowExecutionKeys.endTs, endTs);
    setUnset(updateOps, WorkflowExecutionKeys.duration, duration);
    setUnset(updateOps, WorkflowExecutionKeys.rollbackStartTs, rollbackStartTs);
    setUnset(updateOps, WorkflowExecutionKeys.rollbackDuration, rollbackDuration);

    wingsPersistence.findAndModify(query, updateOps, callbackFindAndModifyOptions);

    handlePostExecution(context);

    final String workflowId = context.getWorkflowId(); // this will be pipelineId in case of pipeline
    List<NameValuePair> resolvedTags = resolveDeploymentTags(context, workflowId);
    addTagsToWorkflowExecution(resolvedTags);

    if (WorkflowType.PIPELINE != context.getWorkflowType()) {
      try {
        deliverWorkflowEvent(execution, status, endTs);
        workflowNotificationHelper.sendWorkflowStatusChangeNotification(context, status);
      } catch (Exception exception) {
        // Failing to send notification is not considered critical to interrupt the status update.
        log.error("Failed to send notification.", exception);
      }
      if (needToNotifyPipeline) {
        try {
          log.info("Need to notify the pipeline");
          waitNotifyEngine.doneWith(workflowExecutionId, new EnvExecutionResponseData(workflowExecutionId, status));
          log.info("Successfully notified the pipeline");
        } catch (WingsException exception) {
          ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
        }
      }
    } else {
      deliverEvent(execution, status, endTs);
      if (status == SUCCESS) {
        triggerService.triggerExecutionPostPipelineCompletionAsync(appId, workflowId);
      }
    }
    if (ExecutionStatus.isFinalStatus(status)) {
      try {
        generateEventsForWorkflowExecution(context, status);

      } catch (Exception e) {
        log.error("Failed to generate events for workflowExecution:[{}], appId:[{}],", workflowExecutionId, appId, e);
      }
    }
  }

  private void generateEventsForWorkflowExecution(ExecutionContext context, ExecutionStatus status) {
    alertService.deploymentCompleted(appId, context.getWorkflowExecutionId());

    // MULTIPLE FIELDS OF WORKFLOW EXECUTION ARE USED, LEAVE IT WITHOUT PROJECTION AT FIRST MOMENT.
    WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(appId, workflowExecutionId);
    if (workflowExecution == null) {
      log.warn("No workflowExecution for workflowExecution:[{}], appId:[{}],", workflowExecutionId, appId);
      return;
    }
    final Application applicationDataForReporting = usageMetricsHelper.getApplication(appId);
    String accountID = applicationDataForReporting.getAccountId();
    /**
     * PL-2326 : Workflow execution did not even start -> was in queued state. In
     * this case, startTS and endTS are not populated. Ignoring these events.
     */
    if (workflowExecution.getStartTs() != null && workflowExecution.getEndTs() != null) {
      updateDeploymentInformation(workflowExecution);
      workflowExecution = workflowExecutionService.getWorkflowExecutionWithFailureDetails(appId, workflowExecutionId);
      /**
       * Had to do a double check on the finalStatus since workflowStatus is still not in finalStatus while
       * the callBack says it is finalStatus (Check with Srinivas)
       */
      if (ExecutionStatus.isFinalStatus(workflowExecution.getStatus())) {
        usageMetricsEventPublisher.publishDeploymentTimeSeriesEvent(
            accountID, workflowExecution, Collections.emptyMap());
        if (workflowExecution.isOnDemandRollback() && workflowExecution.getOriginalExecution() != null
            && workflowExecution.getOriginalExecution().getExecutionId() != null
            && workflowExecution.getStatus() == SUCCESS) {
          WorkflowExecution originalExecution = workflowExecutionService.getUpdatedWorkflowExecution(
              appId, workflowExecution.getOriginalExecution().getExecutionId());
          originalExecution.setRollbackDuration(workflowExecution.getDuration());
          try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("manuallyRolledBack", true);
            usageMetricsEventPublisher.publishDeploymentTimeSeriesEvent(accountID, originalExecution, metadata);
          } catch (Exception e) {
            log.error("Exception while syncing the data for original workflow execution", e);
          }
        }
      } else {
        log.warn("Workflow [{}] has executionStatus:[{}], different status:[{}]", workflowExecutionId,
            workflowExecution.getStatus(), status);
      }
    }

    eventPublishHelper.handleDeploymentCompleted(workflowExecution);
    if (workflowExecution.getPipelineExecutionId() == null) {
      String applicationName = applicationDataForReporting.getName();
      Account account = accountService.getFromCache(accountID);
      // The null check is in case the account has been physical deleted.
      if (account == null) {
        log.warn(
            "Workflow execution in application {} is associated with deleted account {}", applicationName, accountID);
      }
    }
    if (WorkflowType.PIPELINE != context.getWorkflowType()) {
      if (workflowExecution.getPipelineExecutionId() != null) {
        workflowExecutionService.refreshCollectedArtifacts(
            appId, workflowExecution.getPipelineExecutionId(), workflowExecutionId);
      }
    }

    reportDeploymentEventToSegment(workflowExecution);
  }

  private void deliverWorkflowEvent(WorkflowExecution execution, ExecutionStatus status, Long endTs) {
    Application application = appService.get(appId);
    if (application == null) {
      return;
    }
    String accountId = application.getAccountId();
    if (execution == null) {
      return;
    }
    PipelineSummary summary = execution.getPipelineSummary();
    eventService.deliverEvent(accountId, appId, getWorkflowEndPayload(application, execution, status, endTs, summary));
  }

  private EventPayload getWorkflowEndPayload(Application application, WorkflowExecution execution,
      ExecutionStatus status, Long endTs, PipelineSummary summary) {
    return EventPayload.builder()
        .eventType(EventType.WORKFLOW_END.getEventValue())
        .data(CgWorkflowCompletePayload.builder()
                  .application(ApplicationEventData.builder().id(appId).name(application.getName()).build())
                  .services(isEmpty(execution.getServiceIds()) ? Collections.emptyList()
                                                               : execution.getServiceIds()
                                                                     .stream()
                                                                     .map(id -> ServiceEntity.builder().id(id).build())
                                                                     .collect(Collectors.toList()))
                  .infraDefinitions(isEmpty(execution.getInfraDefinitionIds())
                          ? Collections.emptyList()
                          : execution.getInfraDefinitionIds()
                                .stream()
                                .map(id -> InfraDefinitionEntity.builder().id(id).build())
                                .collect(Collectors.toList()))
                  .environments(isEmpty(execution.getEnvIds())
                          ? Collections.emptyList()
                          : execution.getEnvIds()
                                .stream()
                                .map(id -> EnvironmentEntity.builder().id(id).build())
                                .collect(Collectors.toList()))
                  .workflow(WorkflowEventData.builder().id(execution.getWorkflowId()).name(execution.getName()).build())
                  .pipeline(getPipelineEventData(summary))
                  .startedAt(execution.getCreatedAt())
                  .completedAt(endTs)
                  .status(status.name())
                  .triggeredByType(execution.getCreatedByType())
                  .triggeredBy(execution.getCreatedBy())
                  .executionArgs(
                      ExecutionArgsEventData.builder()
                          .notes(execution.getExecutionArgs() == null ? null : execution.getExecutionArgs().getNotes())
                          .build())
                  .pipelineExecution(PipelineExecData.builder().id(execution.getPipelineExecutionId()).build())
                  .workflowExecution(WorkflowExecData.builder().id(execution.getUuid()).build())
                  .build())
        .build();
  }

  private PipelineEventData getPipelineEventData(PipelineSummary summary) {
    if (summary == null) {
      return null;
    }
    return PipelineEventData.builder().id(summary.getPipelineId()).name(summary.getPipelineName()).build();
  }

  private void deliverEvent(WorkflowExecution execution, ExecutionStatus status, Long endTs) {
    Application application = appService.get(appId);
    if (application == null || execution == null || execution.getPipelineSummary() == null) {
      return;
    }
    String accountId = application.getAccountId();
    PipelineSummary summary = execution.getPipelineSummary();
    eventService.deliverEvent(accountId, appId,
        EventPayload.builder()
            .eventType(EventType.PIPELINE_END.getEventValue())
            .data(
                CgPipelineCompletePayload.builder()
                    .application(ApplicationEventData.builder().id(appId).name(application.getName()).build())
                    .executionId(execution.getUuid())
                    .pipelineExecution(PipelineExecData.builder().id(execution.getUuid()).build())
                    .services(isEmpty(execution.getServiceIds())
                            ? Collections.emptyList()
                            : execution.getServiceIds()
                                  .stream()
                                  .map(id -> ServiceEntity.builder().id(id).build())
                                  .collect(Collectors.toList()))
                    .infraDefinitions(isEmpty(execution.getInfraDefinitionIds())
                            ? Collections.emptyList()
                            : execution.getInfraDefinitionIds()
                                  .stream()
                                  .map(id -> InfraDefinitionEntity.builder().id(id).build())
                                  .collect(Collectors.toList()))
                    .environments(isEmpty(execution.getEnvIds())
                            ? Collections.emptyList()
                            : execution.getEnvIds()
                                  .stream()
                                  .map(id -> EnvironmentEntity.builder().id(id).build())
                                  .collect(Collectors.toList()))
                    .pipeline(
                        PipelineEventData.builder().id(summary.getPipelineId()).name(summary.getPipelineName()).build())
                    .startedAt(execution.getCreatedAt())
                    .completedAt(endTs)
                    .status(status.name())
                    .triggeredByType(execution.getCreatedByType())
                    .triggeredBy(execution.getCreatedBy())
                    .executionArgs(
                        ExecutionArgsEventData.builder()
                            .notes(
                                execution.getExecutionArgs() == null ? null : execution.getExecutionArgs().getNotes())
                            .build())
                    .build())
            .build());
  }

  public void publish(WorkflowExecution workflowExecution) {
    final Application applicationDataForReporting = usageMetricsHelper.getApplication(workflowExecution.getAppId());
    String accountID = applicationDataForReporting.getAccountId();

    updateDeploymentInformation(workflowExecution);
    WorkflowExecution updatedWorkflowExecution =
        workflowExecutionService.getUpdatedWorkflowExecution(workflowExecution.getAppId(), workflowExecution.getUuid());
    usageMetricsEventPublisher.publishDeploymentTimeSeriesEvent(
        accountID, updatedWorkflowExecution, Collections.emptyMap());
    reportDeploymentEventToSegment(updatedWorkflowExecution);
  }

  public void publish(WorkflowExecution execution, StateStatusUpdateInfo statusUpdateInfo, EventType eventType) {
    if (execution == null) {
      return;
    }
    Application application = appService.get(execution.getAppId());
    if (application == null) {
      return;
    }
    switch (eventType) {
      case PIPELINE_CONTINUE:
        deliverPipelineResume(application, execution, statusUpdateInfo);
        break;
      case PIPELINE_PAUSE:
        deliverPipelinePause(application, execution, statusUpdateInfo);
        break;
      case WORKFLOW_PAUSE:
        deliverWorkflowPause(application, execution, statusUpdateInfo);
        break;
      case WORKFLOW_CONTINUE:
        deliverWorkflowResume(application, execution, statusUpdateInfo);
        break;
      default:
        break;
    }
  }

  private void deliverWorkflowPause(
      Application application, WorkflowExecution execution, StateStatusUpdateInfo statusUpdateInfo) {
    PipelineSummary summary = execution.getPipelineSummary();
    eventService.deliverEvent(application.getAccountId(), application.getUuid(),
        EventPayload.builder()
            .eventType(EventType.WORKFLOW_PAUSE.getEventValue())
            .data(CgWorkflowPausePayload.builder()
                      .application(ApplicationEventData.builder().id(appId).name(application.getName()).build())
                      .services(isEmpty(execution.getServiceIds())
                              ? Collections.emptyList()
                              : execution.getServiceIds()
                                    .stream()
                                    .map(id -> ServiceEntity.builder().id(id).build())
                                    .collect(Collectors.toList()))
                      .infraDefinitions(isEmpty(execution.getInfraDefinitionIds())
                              ? Collections.emptyList()
                              : execution.getInfraDefinitionIds()
                                    .stream()
                                    .map(id -> InfraDefinitionEntity.builder().id(id).build())
                                    .collect(Collectors.toList()))
                      .environments(isEmpty(execution.getEnvIds())
                              ? Collections.emptyList()
                              : execution.getEnvIds()
                                    .stream()
                                    .map(id -> EnvironmentEntity.builder().id(id).build())
                                    .collect(Collectors.toList()))
                      .workflow(
                          WorkflowEventData.builder().id(execution.getWorkflowId()).name(execution.getName()).build())
                      .pipeline(getPipelineEventData(summary))
                      .startedAt(execution.getCreatedAt())
                      .triggeredByType(execution.getCreatedByType())
                      .triggeredBy(execution.getCreatedBy())
                      .executionArgs(
                          ExecutionArgsEventData.builder()
                              .notes(
                                  execution.getExecutionArgs() == null ? null : execution.getExecutionArgs().getNotes())
                              .build())
                      .pipelineExecution(PipelineExecData.builder().id(execution.getPipelineExecutionId()).build())
                      .workflowExecution(WorkflowExecData.builder().id(execution.getUuid()).build())
                      .build())
            .build());
  }

  private void deliverWorkflowResume(
      Application application, WorkflowExecution execution, StateStatusUpdateInfo statusUpdateInfo) {
    PipelineSummary summary = execution.getPipelineSummary();
    eventService.deliverEvent(application.getAccountId(), application.getUuid(),
        EventPayload.builder()
            .eventType(EventType.WORKFLOW_CONTINUE.getEventValue())
            .data(CgWorkflowResumePayload.builder()
                      .application(ApplicationEventData.builder().id(appId).name(application.getName()).build())
                      .services(isEmpty(execution.getServiceIds())
                              ? Collections.emptyList()
                              : execution.getServiceIds()
                                    .stream()
                                    .map(id -> ServiceEntity.builder().id(id).build())
                                    .collect(Collectors.toList()))
                      .infraDefinitions(isEmpty(execution.getInfraDefinitionIds())
                              ? Collections.emptyList()
                              : execution.getInfraDefinitionIds()
                                    .stream()
                                    .map(id -> InfraDefinitionEntity.builder().id(id).build())
                                    .collect(Collectors.toList()))
                      .environments(isEmpty(execution.getEnvIds())
                              ? Collections.emptyList()
                              : execution.getEnvIds()
                                    .stream()
                                    .map(id -> EnvironmentEntity.builder().id(id).build())
                                    .collect(Collectors.toList()))
                      .workflow(
                          WorkflowEventData.builder().id(execution.getWorkflowId()).name(execution.getName()).build())
                      .pipeline(getPipelineEventData(summary))
                      .startedAt(execution.getCreatedAt())
                      .triggeredByType(execution.getCreatedByType())
                      .triggeredBy(execution.getCreatedBy())
                      .executionArgs(
                          ExecutionArgsEventData.builder()
                              .notes(
                                  execution.getExecutionArgs() == null ? null : execution.getExecutionArgs().getNotes())
                              .build())
                      .pipelineExecution(PipelineExecData.builder().id(execution.getPipelineExecutionId()).build())
                      .workflowExecution(WorkflowExecData.builder().id(execution.getUuid()).build())
                      .build())
            .build());
  }

  private void deliverPipelinePause(
      Application application, WorkflowExecution execution, StateStatusUpdateInfo statusUpdateInfo) {
    PipelineSummary summary = execution.getPipelineSummary();
    if (summary == null) {
      return;
    }
    eventService.deliverEvent(application.getAccountId(), application.getUuid(),
        EventPayload.builder()
            .eventType(EventType.PIPELINE_PAUSE.getEventValue())
            .data(
                CgPipelinePausePayload.builder()
                    .application(ApplicationEventData.builder().id(appId).name(application.getName()).build())
                    .executionId(execution.getUuid())
                    .pipelineExecution(PipelineExecData.builder().id(execution.getUuid()).build())
                    .services(isEmpty(execution.getServiceIds())
                            ? Collections.emptyList()
                            : execution.getServiceIds()
                                  .stream()
                                  .map(id -> ServiceEntity.builder().id(id).build())
                                  .collect(Collectors.toList()))
                    .infraDefinitions(isEmpty(execution.getInfraDefinitionIds())
                            ? Collections.emptyList()
                            : execution.getInfraDefinitionIds()
                                  .stream()
                                  .map(id -> InfraDefinitionEntity.builder().id(id).build())
                                  .collect(Collectors.toList()))
                    .environments(isEmpty(execution.getEnvIds())
                            ? Collections.emptyList()
                            : execution.getEnvIds()
                                  .stream()
                                  .map(id -> EnvironmentEntity.builder().id(id).build())
                                  .collect(Collectors.toList()))
                    .pipeline(
                        PipelineEventData.builder().id(summary.getPipelineId()).name(summary.getPipelineName()).build())
                    .startedAt(execution.getCreatedAt())
                    .triggeredByType(execution.getCreatedByType())
                    .triggeredBy(execution.getCreatedBy())
                    .executionArgs(
                        ExecutionArgsEventData.builder()
                            .notes(
                                execution.getExecutionArgs() == null ? null : execution.getExecutionArgs().getNotes())
                            .build())
                    .stages(getStageInfo(execution))
                    .build())
            .build());
  }

  private List<PipelineStageInfo> getStageInfo(WorkflowExecution execution) {
    if (execution == null || execution.getPipelineExecution() == null
        || isEmpty(execution.getPipelineExecution().getPipelineStageExecutions())) {
      return Collections.emptyList();
    }
    List<PipelineStageExecution> stageExecutions = execution.getPipelineExecution().getPipelineStageExecutions();
    return stageExecutions.stream()
        .map(se
            -> PipelineStageInfo.builder()
                   .id(se.getPipelineStageElementId())
                   .stageType(se.getStateType())
                   .name(se.getStateName())
                   .endTime(se.getEndTs())
                   .startTime(se.getStartTs())
                   .status(se.getStatus().name())
                   .build())
        .collect(Collectors.toList());
  }

  private void deliverPipelineResume(
      Application application, WorkflowExecution execution, StateStatusUpdateInfo statusUpdateInfo) {
    PipelineSummary summary = execution.getPipelineSummary();
    if (summary == null) {
      return;
    }
    eventService.deliverEvent(application.getAccountId(), application.getUuid(),
        EventPayload.builder()
            .eventType(EventType.PIPELINE_CONTINUE.getEventValue())
            .data(
                CgPipelineResumePayload.builder()
                    .application(ApplicationEventData.builder().id(appId).name(application.getName()).build())
                    .executionId(execution.getUuid())
                    .pipelineExecution(PipelineExecData.builder().id(execution.getUuid()).build())
                    .services(isEmpty(execution.getServiceIds())
                            ? Collections.emptyList()
                            : execution.getServiceIds()
                                  .stream()
                                  .map(id -> ServiceEntity.builder().id(id).build())
                                  .collect(Collectors.toList()))
                    .infraDefinitions(isEmpty(execution.getInfraDefinitionIds())
                            ? Collections.emptyList()
                            : execution.getInfraDefinitionIds()
                                  .stream()
                                  .map(id -> InfraDefinitionEntity.builder().id(id).build())
                                  .collect(Collectors.toList()))
                    .environments(isEmpty(execution.getEnvIds())
                            ? Collections.emptyList()
                            : execution.getEnvIds()
                                  .stream()
                                  .map(id -> EnvironmentEntity.builder().id(id).build())
                                  .collect(Collectors.toList()))
                    .pipeline(
                        PipelineEventData.builder().id(summary.getPipelineId()).name(summary.getPipelineName()).build())
                    .startedAt(execution.getCreatedAt())
                    .triggeredByType(execution.getCreatedByType())
                    .triggeredBy(execution.getCreatedBy())
                    .executionArgs(
                        ExecutionArgsEventData.builder()
                            .notes(
                                execution.getExecutionArgs() == null ? null : execution.getExecutionArgs().getNotes())
                            .build())
                    .stages(getStageInfo(execution))
                    .build())
            .build());
  }

  @VisibleForTesting
  public List<NameValuePair> resolveDeploymentTags(ExecutionContext context, String workflowId) {
    String accountId = appService.getAccountIdByAppId(appId);
    List<HarnessTagLink> harnessTagLinks = harnessTagService.getTagLinksWithEntityId(accountId, workflowId);
    List<NameValuePair> resolvedTags = new ArrayList<>();
    if (isNotEmpty(harnessTagLinks)) {
      for (HarnessTagLink harnessTagLink : harnessTagLinks) {
        String tagKey = context.renderExpression(harnessTagLink.getKey());
        // checking string equals null as the jexl library seems to be returning the string "null" in some cases when
        // the expression can't be evaluated instead of returning the original expression
        // if key can't be evaluated, don't store it
        // if the evaluated key contains . or contains some unresolved expressions like ${service.name} don't store it
        if (isEmpty(tagKey) || tagKey.equals("null")
            || (harnessTagLink.getKey().startsWith("${") && harnessTagLink.getKey().equals(tagKey))
            || tagKey.contains(".") || tagKey.contains("${") || wingsVariablePattern.matcher(tagKey).find()) {
          continue;
        }
        String tagValue = context.renderExpression(harnessTagLink.getValue());
        // if value can't be evaluated, set it to ""
        if (tagValue == null || tagValue.equals("null")
            || (harnessTagLink.getValue().startsWith("${") && harnessTagLink.getValue().equals(tagValue))
            || wingsVariablePattern.matcher(tagValue).find()) {
          tagValue = UNRESOLVED;
        }
        resolvedTags.add(NameValuePair.builder().name(tagKey).value(tagValue).build());
      }
    }
    return resolvedTags;
  }

  @VisibleForTesting
  public void addTagsToWorkflowExecution(List<NameValuePair> resolvedTags) {
    if (isNotEmpty(resolvedTags)) {
      Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                           .filter(WorkflowExecutionKeys.appId, appId)
                                           .filter(WorkflowExecutionKeys.uuid, workflowExecutionId);
      UpdateOperations<WorkflowExecution> updateOps = wingsPersistence.createUpdateOperations(WorkflowExecution.class)
                                                          .set(WorkflowExecutionKeys.tags, resolvedTags);
      wingsPersistence.findAndModify(query, updateOps, callbackFindAndModifyOptions);
      log.info(format("[%d] tags updated for workflow execution: [%s]", resolvedTags.size(), workflowExecutionId));
    }
  }

  public void updateDeploymentInformation(WorkflowExecution workflowExecution) {
    UpdateOperations<WorkflowExecution> updateOps;
    updateOps = wingsPersistence.createUpdateOperations(WorkflowExecution.class);
    boolean update = false;
    final List<String> deployedCloudProviders =
        workflowExecutionService.getCloudProviderIdsForExecution(workflowExecution);

    if (!Lists.isNullOrEmpty(deployedCloudProviders)) {
      update = true;
      setUnset(updateOps, WorkflowExecutionKeys.deployedCloudProviders, deployedCloudProviders);
    }
    final List<String> deployedServices = workflowExecutionService.getServiceIdsForExecution(workflowExecution);
    if (!Lists.isNullOrEmpty(deployedServices)) {
      update = true;
      setUnset(updateOps, WorkflowExecutionKeys.deployedServices, deployedServices);
    }
    final List<EnvSummary> deployedEnvironments =
        workflowExecutionService.getEnvironmentsForExecution(workflowExecution);

    if (!Lists.isNullOrEmpty(deployedEnvironments)) {
      update = true;
      setUnset(updateOps, WorkflowExecutionKeys.deployedEnvironments, deployedEnvironments);
    }

    if (update) {
      wingsPersistence.findAndModify(wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.uuid, workflowExecution.getUuid()),
          updateOps, callbackFindAndModifyOptions);
    }
  }

  private boolean isProdEnv(WorkflowExecution workflowExecution) {
    return EnvironmentType.PROD == workflowExecution.getEnvType();
  }

  private String getDeploymentType(WorkflowExecution workflowExecution) {
    if (WorkflowType.ORCHESTRATION == workflowExecution.getWorkflowType()) {
      return Keys.WORKFLOW;
    } else {
      return WorkflowType.PIPELINE.name().toLowerCase();
    }
  }

  @VisibleForTesting
  public void reportDeploymentEventToSegment(WorkflowExecution workflowExecution) {
    try {
      String accountId = workflowExecution.getAccountId();

      Map<String, String> properties = new HashMap<>();
      properties.put(SegmentHandler.Keys.GROUP_ID, accountId);
      properties.put(Keys.MODULE, Keys.DEPLOYMENT);
      properties.put(Keys.PRODUCTION, Boolean.toString(isProdEnv(workflowExecution)));
      properties.put(Keys.DEPLOYMENT_ID, workflowExecution.getUuid());
      if (workflowExecution.getWorkflowType() != null) {
        properties.put(Keys.DEPLOYMENT_TYPE, getDeploymentType(workflowExecution));
        if (WorkflowType.ORCHESTRATION == workflowExecution.getWorkflowType()
            && isNotEmpty(workflowExecution.getServiceIds())) {
          properties.put(Keys.SERVICE_ID, workflowExecution.getServiceIds().get(0));
        }
      }

      Map<String, Boolean> integrations = new HashMap<>();
      integrations.put(SegmentHandler.Keys.NATERO, true);
      integrations.put(SegmentHandler.Keys.SALESFORCE, false);

      Account account = accountService.getFromCacheWithFallback(accountId);
      EmbeddedUser triggeredBy = workflowExecution.getTriggeredBy();
      String userId = null;
      if (triggeredBy != null) {
        userId = triggeredBy.getUuid();
      }

      String deploymentEvent = getSegmentDeploymentEvent(workflowExecution);
      if (deploymentEvent != null) {
        segmentHandler.reportTrackEvent(account, deploymentEvent, userId, properties, integrations);
      } else {
        log.info("Skipping the deployment track event since the status {} doesn't need to be reported",
            workflowExecution.getStatus());
      }
    } catch (Exception e) {
      log.error("Exception while reporting track event for deployment {}", workflowExecutionId, e);
    }
  }

  private String getSegmentDeploymentEvent(WorkflowExecution workflowExecution) {
    if (workflowExecution == null || workflowExecution.getStatus() == null) {
      return null;
    }

    switch (workflowExecution.getStatus()) {
      case RUNNING:
        return Keys.RUNNING;
      case PAUSED:
        return Keys.PAUSED;
      case REJECTED:
        return Keys.REJECTED;
      case FAILED:
        return Keys.FAILED;
      case ABORTED:
        return Keys.ABORTED;
      case EXPIRED:
        return Keys.EXPIRED;
      case SUCCESS:
        return Keys.SUCCESS;
      default:
        return null;
    }
  }

  private void handlePostExecution(ExecutionContext context) {
    // TODO: this is temporary. this should be part of its own callback and with more precise filter
    try {
      log.info("Update Active Barriers if any");
      barrierService.updateAllActiveBarriers(context.getAppId());
    } catch (RuntimeException exception) {
      // Do not block the execution for possible exception in the barrier update
      log.error("Something wrong with barrier update", exception);
    }

    // TODO: this is temporary. this should be part of its own callback and with more precise filter
    try {
      log.info("Update Active Resource constraints");
      final Set<String> constraintIds =
          resourceConstraintService.updateActiveConstraints(context.getAppId(), workflowExecutionId);

      log.info("Update Blocked Resource constraints");
      resourceConstraintService.updateBlockedConstraints(constraintIds);

    } catch (RuntimeException exception) {
      // Do not block the execution for possible exception in the barrier update
      log.error("Something wrong with resource constraints update", exception);
    }
    try {
      WorkflowExecution workflowExecution =
          workflowExecutionService.getExecutionDetails(appId, workflowExecutionId, true, false);
      log.info("Breakdown refresh happened for workflow execution {}", workflowExecution.getUuid());
    } catch (Exception e) {
      log.error("Error in breakdown refresh", e);
    }
  }

  void setNeedToNotifyPipeline(boolean needToNotifyPipeline) {
    this.needToNotifyPipeline = needToNotifyPipeline;
  }
}
