/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/**
 *
 */

package software.wings.sm;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionInterruptType.ABORT;
import static io.harness.beans.ExecutionInterruptType.ABORT_ALL;
import static io.harness.beans.ExecutionInterruptType.CONTINUE_PIPELINE_STAGE;
import static io.harness.beans.ExecutionInterruptType.IGNORE;
import static io.harness.beans.ExecutionInterruptType.MARK_EXPIRED;
import static io.harness.beans.ExecutionInterruptType.PAUSE;
import static io.harness.beans.ExecutionInterruptType.PAUSE_ALL;
import static io.harness.beans.ExecutionInterruptType.RESUME;
import static io.harness.beans.ExecutionInterruptType.RESUME_ALL;
import static io.harness.beans.ExecutionInterruptType.RETRY;
import static io.harness.beans.ExecutionInterruptType.ROLLBACK;
import static io.harness.beans.ExecutionInterruptType.ROLLBACK_ON_APPROVAL;
import static io.harness.beans.ExecutionInterruptType.ROLLBACK_PROVISIONER_AFTER_PHASES;
import static io.harness.beans.ExecutionInterruptType.ROLLBACK_PROVISIONER_AFTER_PHASES_ON_APPROVAL;
import static io.harness.beans.ExecutionStatus.ABORTED;
import static io.harness.beans.ExecutionStatus.DISCONTINUING;
import static io.harness.beans.ExecutionStatus.ERROR;
import static io.harness.beans.ExecutionStatus.EXPIRED;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.NEW;
import static io.harness.beans.ExecutionStatus.PAUSED;
import static io.harness.beans.ExecutionStatus.RESUMED;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.beans.ExecutionStatus.STARTING;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.ExecutionStatus.WAITING;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.GE;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.ABORT_ALL_ALREADY;
import static io.harness.eraro.ErrorCode.INVALID_ARGUMENT;
import static io.harness.eraro.ErrorCode.PAUSE_ALL_ALREADY;
import static io.harness.eraro.ErrorCode.RESUME_ALL_ALREADY;
import static io.harness.eraro.ErrorCode.ROLLBACK_ALREADY;
import static io.harness.eraro.ErrorCode.STATE_NOT_FOR_TYPE;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.exception.WingsException.USER;
import static io.harness.govern.Switch.noop;
import static io.harness.govern.Switch.unhandled;
import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.alert.AlertType.ApprovalNeeded;
import static software.wings.beans.alert.AlertType.ManualInterventionNeeded;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SortOrder.OrderType;
import io.harness.beans.WorkflowType;
import io.harness.event.usagemetrics.UsageMetricsEventPublisher;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.WorkflowExecution;
import software.wings.beans.alert.ApprovalNeededAlert;
import software.wings.beans.alert.ManualInterventionNeededAlert;
import software.wings.beans.alert.RuntimeInputsRequiredAlert;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.workflow.WorkflowNotificationHelper;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.sm.ExecutionInterrupt.ExecutionInterruptKeys;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;
import software.wings.sm.states.WorkflowState;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.mongodb.ReadPreference;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * The interface State machine event manager.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ExecutionInterruptManager {
  @Inject private AlertService alertService;
  @Inject private Injector injector;
  @Inject private StateExecutionService stateExecutionService;
  @Inject private StateMachineExecutor stateMachineExecutor;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private WorkflowNotificationHelper workflowNotificationHelper;
  @Inject private AppService appService;
  @Inject private UsageMetricsEventPublisher usageMetricsEventPublisher;

  Map<ExecutionInterruptType, List<ExecutionStatus>> acceptableIndividualStatusList =
      ImmutableMap.<ExecutionInterruptType, List<ExecutionStatus>>builder()
          .put(RESUME, asList(PAUSED))
          .put(IGNORE, asList(PAUSED, WAITING))
          .put(RETRY, asList(WAITING, FAILED, ERROR, EXPIRED, STARTING))
          .put(ABORT, asList(NEW, STARTING, RUNNING, PAUSED, WAITING))
          .put(MARK_EXPIRED, asList(NEW, STARTING, RUNNING, PAUSED, WAITING, DISCONTINUING))
          .put(PAUSE, asList(NEW, STARTING, RUNNING))
          .build();

  /**
   * Register execution event execution event.
   *
   * @param executionInterrupt the execution event
   * @return the execution event
   */
  public ExecutionInterrupt registerExecutionInterrupt(ExecutionInterrupt executionInterrupt) {
    StateExecutionInstance stateExecutionInstance = null;
    ExecutionInterruptType executionInterruptType = executionInterrupt.getExecutionInterruptType();
    if (acceptableIndividualStatusList.containsKey(executionInterruptType)) {
      if (executionInterrupt.getStateExecutionInstanceId() == null) {
        throw new WingsException(INVALID_ARGUMENT).addParam("args", "null stateExecutionInstanceId");
      }

      stateExecutionInstance = wingsPersistence.getWithAppId(StateExecutionInstance.class,
          executionInterrupt.getAppId(), executionInterrupt.getStateExecutionInstanceId());
      if (stateExecutionInstance == null) {
        throw new WingsException(INVALID_ARGUMENT, USER)
            .addParam("args", "invalid stateExecutionInstanceId: " + executionInterrupt.getStateExecutionInstanceId());
      }

      final List<ExecutionStatus> statuses = acceptableIndividualStatusList.get(executionInterruptType);
      if (!statuses.contains(stateExecutionInstance.getStatus())) {
        throw new WingsException(STATE_NOT_FOR_TYPE, USER)
            .addParam("displayName", stateExecutionInstance.getDisplayName())
            .addParam("type", executionInterruptType.name())
            .addParam("status", stateExecutionInstance.getStatus().name())
            .addParam("statuses", statuses);
      }
    }

    List<ExecutionInterrupt> res = listActiveExecutionInterrupts(executionInterrupt);

    if (executionInterruptType == ROLLBACK) {
      if (isPresent(res, ROLLBACK)) {
        throw new WingsException(ROLLBACK_ALREADY, USER);
      }
    }

    if (executionInterruptType == PAUSE_ALL) {
      if (isPresent(res, PAUSE_ALL)) {
        throw new WingsException(PAUSE_ALL_ALREADY, USER);
      }
      ExecutionInterrupt resumeAll = getExecutionInterrupt(res, ExecutionInterruptType.RESUME_ALL);
      if (resumeAll != null) {
        seize(resumeAll);
      }
    }

    if (executionInterruptType == ExecutionInterruptType.RESUME_ALL) {
      ExecutionInterrupt pauseAll = getExecutionInterrupt(res, PAUSE_ALL);
      if (pauseAll == null || isPresent(res, ExecutionInterruptType.RESUME_ALL)) {
        throw new WingsException(RESUME_ALL_ALREADY, USER);
      }
      seize(pauseAll);
      waitNotifyEngine.doneWith(pauseAll.getUuid(), ExecutionStatusData.builder().executionStatus(SUCCESS).build());
    }

    if (executionInterruptType == ABORT_ALL) {
      if (isPresent(res, ABORT_ALL)) {
        throw new InvalidRequestException("Execution has already been Aborted", ABORT_ALL_ALREADY, USER);
      }
      seizeAllInterrupts(res);
    }

    if (executionInterrupt.getAccountId() == null) {
      executionInterrupt.setAccountId(appService.getAccountIdByAppId(executionInterrupt.getAppId()));
    }
    wingsPersistence.save(executionInterrupt);

    try {
      usageMetricsEventPublisher.publishExecutionInterruptTimeSeriesEvent(
          executionInterrupt.getAccountId(), executionInterrupt);
    } catch (Exception e) {
      log.error("Failed to publish execution interrupt [{}] , [{}]", executionInterrupt, e);
    }

    stateMachineExecutor.handleInterrupt(executionInterrupt);

    sendNotificationIfRequired(executionInterrupt);
    closeAlertsIfOpened(stateExecutionInstance, executionInterrupt);
    return executionInterrupt;
  }

  private void sendNotificationIfRequired(ExecutionInterrupt executionInterrupt) {
    final ExecutionInterruptType executionInterruptType = executionInterrupt.getExecutionInterruptType();

    WorkflowExecution workflowExecution = wingsPersistence.getWithAppId(
        WorkflowExecution.class, executionInterrupt.getAppId(), executionInterrupt.getExecutionUuid());

    switch (executionInterruptType) {
      case PAUSE_ALL:
        if (workflowExecution.getWorkflowType() == WorkflowType.ORCHESTRATION) {
          sendWorkflowNotification(workflowExecution, executionInterrupt, PAUSED);
        }
        break;
      case RESUME_ALL:
      case CONTINUE_WITH_DEFAULTS:
        if (workflowExecution.getWorkflowType() == WorkflowType.ORCHESTRATION) {
          sendWorkflowNotification(workflowExecution, executionInterrupt, RESUMED);
        }
        break;
      case MARK_EXPIRED:
        if (workflowExecution.getWorkflowType() == WorkflowType.PIPELINE) {
          sendPipelineNotification(workflowExecution, executionInterrupt, EXPIRED);
        }
        break;
      case ABORT_ALL:
        if (workflowExecution.getWorkflowType() == WorkflowType.PIPELINE) {
          sendPipelineNotification(workflowExecution, executionInterrupt, ABORTED);
        }
        break;
      case ABORT:
      case PAUSE:
      case RESUME:
      case RETRY:
      case IGNORE:
      case MARK_FAILED:
      case MARK_SUCCESS:
      case ROLLBACK:
      case ROLLBACK_ON_APPROVAL:
      case ROLLBACK_PROVISIONER_AFTER_PHASES:
      case ROLLBACK_PROVISIONER_AFTER_PHASES_ON_APPROVAL:
      case END_EXECUTION:
      case ROLLBACK_DONE:
        noop();
        break;
      default:
        unhandled(executionInterruptType);
    }
  }

  /**
   * Closes alerts if any opened
   * @param stateExecutionInstance
   * @param executionInterrupt
   */
  protected void closeAlertsIfOpened(
      StateExecutionInstance stateExecutionInstance, ExecutionInterrupt executionInterrupt) {
    String appId = stateExecutionInstance != null ? stateExecutionInstance.getAppId() : executionInterrupt.getAppId();
    String stateExecutionInstanceId = stateExecutionInstance != null ? stateExecutionInstance.getUuid()
                                                                     : executionInterrupt.getStateExecutionInstanceId();
    String executionId = stateExecutionInstance != null ? stateExecutionInstance.getExecutionUuid()
                                                        : executionInterrupt.getExecutionUuid();
    try {
      final ExecutionInterruptType executionInterruptType = executionInterrupt.getExecutionInterruptType();
      switch (executionInterruptType) {
        case RESUME:
        case RETRY:
        case IGNORE:
        case ROLLBACK:
        case ROLLBACK_ON_APPROVAL:
        case ROLLBACK_PROVISIONER_AFTER_PHASES:
        case ROLLBACK_PROVISIONER_AFTER_PHASES_ON_APPROVAL:
        case ABORT:
        case MARK_EXPIRED:
        case RESUME_ALL:
        case CONTINUE_WITH_DEFAULTS:
        case CONTINUE_PIPELINE_STAGE:
        case MARK_SUCCESS:
        case MARK_FAILED:
        case END_EXECUTION:
        case ABORT_ALL:
          closeAlerts(executionInterrupt, stateExecutionInstanceId, executionId, appId);
          break;
        case PAUSE:
        case PAUSE_ALL:
        case ROLLBACK_DONE:
          noop();
          break;
        default:
          unhandled(executionInterruptType);
      }
    } catch (Exception e) {
      log.error("Failed to close the ManualNeededAlert/ ApprovalNeededAlert  for appId, executionId  ", appId,
          executionId, e);
    }
  }

  private void closeAlerts(
      ExecutionInterrupt executionInterrupt, String stateExecutionInstanceId, String executionId, String appId) {
    ApprovalNeededAlert approvalNeededAlert =
        ApprovalNeededAlert.builder().executionId(executionId).approvalId(executionId).build();
    alertService.closeAlert(executionInterrupt.getAccountId(), appId, ApprovalNeeded, approvalNeededAlert);

    ManualInterventionNeededAlert manualInterventionNeededAlert =
        ManualInterventionNeededAlert.builder()
            .executionId(executionId)
            .stateExecutionInstanceId(stateExecutionInstanceId)
            .build();
    alertService.closeAlert(null, appId, ManualInterventionNeeded, manualInterventionNeededAlert);

    RuntimeInputsRequiredAlert runtimeInputsRequiredAlert =
        RuntimeInputsRequiredAlert.builder().executionId(executionId).build();
    alertService.closeAlert(
        executionInterrupt.getAccountId(), appId, ManualInterventionNeeded, runtimeInputsRequiredAlert);
  }

  private void sendWorkflowNotification(
      WorkflowExecution workflowExecution, ExecutionInterrupt executionInterrupt, ExecutionStatus status) {
    try {
      final StateExecutionInstance stateExecutionInstance =
          getStateExecutionInstance(workflowExecution, executionInterrupt);

      if (stateExecutionInstance == null) {
        return;
      }

      StateMachine sm = stateExecutionService.obtainStateMachine(stateExecutionInstance);
      ExecutionContextImpl context = new ExecutionContextImpl(stateExecutionInstance, sm, injector);
      injector.injectMembers(context);

      workflowNotificationHelper.sendWorkflowStatusChangeNotification(context, status);

    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (RuntimeException exception) {
      log.error("Unknown runtime exception: ", exception);
    }
  }

  private void sendPipelineNotification(
      WorkflowExecution workflowExecution, ExecutionInterrupt executionInterrupt, ExecutionStatus status) {
    try {
      final StateExecutionInstance stateExecutionInstance =
          getStateExecutionInstance(workflowExecution, executionInterrupt);

      if (stateExecutionInstance == null) {
        return;
      }

      StateMachine sm = stateExecutionService.obtainStateMachine(stateExecutionInstance);
      ExecutionContextImpl context = new ExecutionContextImpl(stateExecutionInstance, sm, injector);
      injector.injectMembers(context);

      WorkflowState workflowState = getWorkflowState(stateExecutionInstance, context);
      stateMachineExecutor.sendPipelineNotification(
          context, workflowState.getUserGroupIds(), stateExecutionInstance, status);

    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (RuntimeException exception) {
      log.error("Unknown runtime exception: ", exception);
    }
  }

  WorkflowState getWorkflowState(StateExecutionInstance stateExecutionInstance, ExecutionContextImpl context) {
    return (WorkflowState) stateMachineExecutor.getStateForExecution(context, stateExecutionInstance);
  }

  private StateExecutionInstance getStateExecutionInstance(
      WorkflowExecution workflowExecution, ExecutionInterrupt executionInterrupt) {
    PageRequest<StateExecutionInstance> pageRequest =
        aPageRequest()
            .withLimit("1")
            .addFilter("appId", EQ, executionInterrupt.getAppId())
            .addFilter("executionUuid", EQ, executionInterrupt.getExecutionUuid())
            .addFilter(StateExecutionInstanceKeys.createdAt, GE, workflowExecution.getCreatedAt())
            .addOrder(StateExecutionInstanceKeys.createdAt, OrderType.DESC)
            .build();

    PageResponse<StateExecutionInstance> pageResponse =
        wingsPersistence.query(StateExecutionInstance.class, pageRequest);
    if (isEmpty(pageResponse)) {
      log.error("No StateExecutionInstance found for sendNotification");
      return null;
    }
    return pageResponse.get(0);
  }

  public void seize(ExecutionInterrupt executionInterrupt) {
    UpdateOperations<ExecutionInterrupt> updateOps =
        wingsPersistence.createUpdateOperations(ExecutionInterrupt.class).set("seized", true);
    wingsPersistence.update(executionInterrupt, updateOps);
  }

  private boolean isPresent(List<ExecutionInterrupt> res, ExecutionInterruptType eventType) {
    return getExecutionInterrupt(res, eventType) != null;
  }

  private ExecutionInterrupt getExecutionInterrupt(List<ExecutionInterrupt> res, ExecutionInterruptType eventType) {
    if (isEmpty(res)) {
      return null;
    }
    for (ExecutionInterrupt evt : res) {
      if (evt.getExecutionInterruptType() == eventType) {
        return evt;
      }
    }
    return null;
  }

  private List<ExecutionInterrupt> listActiveExecutionInterrupts(ExecutionInterrupt executionInterrupt) {
    return wingsPersistence.createQuery(ExecutionInterrupt.class)
        .filter(ExecutionInterruptKeys.appId, executionInterrupt.getAppId())
        .filter(ExecutionInterruptKeys.executionUuid, executionInterrupt.getExecutionUuid())
        .filter(ExecutionInterruptKeys.seized, false)
        .order(Sort.descending(ExecutionInterruptKeys.createdAt))
        .project(ExecutionInterruptKeys.uuid, true)
        .project(ExecutionInterruptKeys.executionInterruptType, true)
        .limit(NO_LIMIT)
        .asList();
  }

  public List<ExecutionInterrupt> listByIdsUsingSecondary(Collection<String> ids) {
    if (isEmpty(ids)) {
      return Collections.emptyList();
    }

    return wingsPersistence.createAnalyticsQuery(ExecutionInterrupt.class, excludeAuthority)
        .field(ExecutionInterruptKeys.uuid)
        .in(ids)
        .asList(new FindOptions().readPreference(ReadPreference.secondaryPreferred()));
  }

  public List<ExecutionInterrupt> listByStateExecutionIdsUsingSecondary(Collection<String> stateExecutionIds) {
    if (isEmpty(stateExecutionIds)) {
      return Collections.emptyList();
    }

    return wingsPersistence.createAnalyticsQuery(ExecutionInterrupt.class, excludeAuthority)
        .field(ExecutionInterruptKeys.stateExecutionInstanceId)
        .in(stateExecutionIds)
        .asList(new FindOptions().readPreference(ReadPreference.secondaryPreferred()));
  }

  /**
   * Check for event workflow execution event.
   *
   * @param appId         the app id
   * @param executionUuid the execution uuid
   * @return the workflow execution event
   */
  public List<ExecutionInterrupt> checkForExecutionInterrupt(String appId, String executionUuid) {
    PageRequest<ExecutionInterrupt> req = aPageRequest()
                                              .addFilter("appId", EQ, appId)
                                              .addFilter("executionUuid", EQ, executionUuid)
                                              .addFilter("executionInterruptType", IN, ABORT_ALL, PAUSE_ALL, RESUME_ALL,
                                                  ROLLBACK, ROLLBACK_PROVISIONER_AFTER_PHASES, CONTINUE_PIPELINE_STAGE,
                                                  ROLLBACK_ON_APPROVAL, ROLLBACK_PROVISIONER_AFTER_PHASES_ON_APPROVAL)
                                              .addFilter("seized", EQ, false)
                                              .addOrder(ExecutionInterrupt.CREATED_AT_KEY, OrderType.DESC)
                                              .build();
    PageResponse<ExecutionInterrupt> res = wingsPersistence.query(ExecutionInterrupt.class, req);
    if (res == null) {
      return null;
    }
    return res.getResponse();
  }

  private void seizeAllInterrupts(List<ExecutionInterrupt> executionInterrupts) {
    for (ExecutionInterrupt executionInterrupt : executionInterrupts) {
      seize(executionInterrupt);
    }
  }
}
