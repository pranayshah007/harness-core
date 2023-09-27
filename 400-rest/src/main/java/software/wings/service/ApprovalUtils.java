/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.INVALID_ARGUMENT;
import static io.harness.eraro.ErrorCode.SERVICENOW_ERROR;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.ServiceNowException;
import io.harness.exception.WingsException;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.api.ApprovalStateExecutionData;
import software.wings.beans.Application;
import software.wings.beans.ApprovalDetails;
import software.wings.beans.ApprovalDetails.Action;
import software.wings.beans.WorkflowExecution;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.states.ApprovalState;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class ApprovalUtils {
  private static void approveWorkflow(WorkflowExecutionService workflowExecutionService,
      StateExecutionService stateExecutionService, WaitNotifyEngine waitNotifyEngine, Action action, String approvalId,
      String appId, String workflowExecutionId, ExecutionStatus approvalStatus, String currentStatus,
      String stateExecutionInstanceId) {
    ApprovalStateExecutionData executionData;
    if (stateExecutionInstanceId == null) {
      ApprovalDetails approvalDetails = new ApprovalDetails();
      approvalDetails.setAction(action);
      approvalDetails.setApprovalId(approvalId);
      executionData = workflowExecutionService.fetchApprovalStateExecutionDataFromWorkflowExecution(
          appId, workflowExecutionId, null, approvalDetails);
    } else {
      StateExecutionInstance stateExecutionInstance =
          stateExecutionService.getStateExecutionData(appId, stateExecutionInstanceId);
      if (stateExecutionInstance == null) {
        throw new WingsException(INVALID_ARGUMENT, USER)
            .addParam("args", "No stateExecutionInstance found for id " + stateExecutionInstanceId);
      }
      executionData = (ApprovalStateExecutionData) stateExecutionInstance.getStateExecutionMap().get(
          stateExecutionInstance.getDisplayName());
    }
    String message;
    if (action == Action.APPROVE) {
      message = "Approval provided on ticket: ";
    } else {
      message = "Rejection provided on ticket: ";
    }

    executionData.setStatus(approvalStatus);
    executionData.setApprovedOn(System.currentTimeMillis());
    executionData.setCurrentStatus(currentStatus);
    executionData.setErrorMsg(message);

    log.info("Sending notify for approvalId: {}, workflowExecutionId: {} ", approvalId, workflowExecutionId);
    waitNotifyEngine.doneWith(approvalId, executionData);
  }

  private static void failWorkflow(StateExecutionService stateExecutionService, WaitNotifyEngine waitNotifyEngine,
      String workflowExecutionId, String stateExecutionInstanceId, String errorMessage,
      ApprovalStateExecutionData approvalData) {
    String approvalId = approvalData.getApprovalId();
    String appId = approvalData.getAppId();
    ApprovalStateExecutionData executionData;
    if (stateExecutionInstanceId == null) {
      ApprovalDetails approvalDetails = new ApprovalDetails();
      approvalDetails.setApprovalId(approvalId);
      executionData = approvalData.getWorkflowExecutionService().fetchApprovalStateExecutionDataFromWorkflowExecution(
          appId, workflowExecutionId, null, approvalDetails);
    } else {
      StateExecutionInstance stateExecutionInstance =
          stateExecutionService.getStateExecutionData(appId, stateExecutionInstanceId);
      if (stateExecutionInstance == null) {
        throw new ServiceNowException(
            "State execution instance " + stateExecutionInstanceId + " not found for service now approval",
            SERVICENOW_ERROR, USER);
      }
      executionData = (ApprovalStateExecutionData) stateExecutionInstance.getStateExecutionMap().get(
          stateExecutionInstance.getDisplayName());
    }

    executionData.setStatus(ExecutionStatus.FAILED);
    executionData.setApprovedOn(System.currentTimeMillis());
    executionData.setCurrentStatus(approvalData.getCurrentStatus());
    executionData.setErrorMsg("Jira/ServiceNow approval failed: " + errorMessage + " ticket: ");

    log.info("Sending notify for approvalId: {}, workflowExecutionId: {} ", approvalId, workflowExecutionId);
    waitNotifyEngine.doneWith(approvalId, executionData);
  }

  private static void continuePauseWorkflow(StateExecutionService stateExecutionService, String workflowExecutionId,
      String stateExecutionInstanceId, String message, ApprovalStateExecutionData approvalData) {
    if (stateExecutionInstanceId == null) {
      return;
    }
    String approvalId = approvalData.getApprovalId();
    String appId = approvalData.getAppId();
    String currentStatus = approvalData.getCurrentStatus();
    StateExecutionInstance stateExecutionInstance =
        stateExecutionService.getStateExecutionData(appId, stateExecutionInstanceId);
    if (stateExecutionInstance == null) {
      throw new ServiceNowException(
          "State execution instance " + stateExecutionInstanceId + " not found for service now approval",
          SERVICENOW_ERROR, USER);
    }

    ApprovalStateExecutionData executionData =
        (ApprovalStateExecutionData) stateExecutionInstance.getStateExecutionMap().get(
            stateExecutionInstance.getDisplayName());
    if (approvalData.isWaitingForChangeWindow()) {
      executionData.setWaitingForChangeWindow(true);
      executionData.setTimeoutMillis(Integer.MAX_VALUE);
      executionData.setErrorMsg("Approved but waiting for Change window ( " + message + " )");
      if (stateExecutionInstance.getExpiryTs() != Long.MAX_VALUE) {
        stateExecutionInstance.setExpiryTs(Long.MAX_VALUE);
        stateExecutionService.updateStateExecutionInstance(stateExecutionInstance);
      }
    }
    if (executionData.getCurrentStatus() != null && executionData.getCurrentStatus().equalsIgnoreCase(currentStatus)) {
      return;
    }

    executionData.setApprovedOn(System.currentTimeMillis());
    executionData.setCurrentStatus(currentStatus);

    log.info("Saving executionData for approvalId: {}, workflowExecutionId: {} ", approvalId, workflowExecutionId);
    stateExecutionService.updateStateExecutionData(appId, stateExecutionInstanceId, executionData);
  }

  public static void checkApproval(StateExecutionService stateExecutionService, WaitNotifyEngine waitNotifyEngine,
      String workflowExecutionId, String stateExecutionInstanceId, String errorMsg, ExecutionStatus issueStatus,
      ApprovalStateExecutionData approvalStateExecutionData) {
    String approvalId = approvalStateExecutionData.getApprovalId();
    String appId = approvalStateExecutionData.getAppId();
    try {
      String currentStatus = approvalStateExecutionData.getCurrentStatus();
      if (issueStatus == ExecutionStatus.SUCCESS || issueStatus == ExecutionStatus.REJECTED) {
        ApprovalDetails.Action action =
            issueStatus == ExecutionStatus.SUCCESS ? ApprovalDetails.Action.APPROVE : ApprovalDetails.Action.REJECT;

        approveWorkflow(approvalStateExecutionData.getWorkflowExecutionService(), stateExecutionService,
            waitNotifyEngine, action, approvalId, appId, workflowExecutionId, issueStatus, currentStatus,
            stateExecutionInstanceId);
      } else if (issueStatus == ExecutionStatus.PAUSED) {
        log.debug("Still waiting for approval or rejected for issueId {}. Issue Status {} and Current Status {}",
            approvalStateExecutionData.getIssueKey(), issueStatus, currentStatus);
        continuePauseWorkflow(
            stateExecutionService, workflowExecutionId, stateExecutionInstanceId, errorMsg, approvalStateExecutionData);
      } else if (issueStatus == ExecutionStatus.FAILED) {
        log.info("Jira/ServiceNow delegate task failed with error: " + errorMsg);
        failWorkflow(stateExecutionService, waitNotifyEngine, workflowExecutionId, stateExecutionInstanceId, errorMsg,
            approvalStateExecutionData);
      } else if (issueStatus == ExecutionStatus.ERROR) {
        log.info("Jira/ServiceNow delegate task has encountered the following error: " + errorMsg);
      }
    } catch (WingsException exception) {
      exception.addContext(Application.class, appId);
      exception.addContext(WorkflowExecution.class, workflowExecutionId);
      exception.addContext(ApprovalState.class, approvalId);
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (Exception exception) {
      log.warn("Error while getting execution data, approvalId: {}, workflowExecutionId: {} , issueId: {}", approvalId,
          workflowExecutionId, approvalStateExecutionData.getIssueKey(), exception);
    }
  }
}
