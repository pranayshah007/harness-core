/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static software.wings.service.ApprovalUtils.checkApproval;

import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.api.ApprovalStateExecutionData;
import software.wings.api.jira.JiraExecutionData;
import software.wings.beans.approval.ApprovalPollingJobEntity;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.inject.Inject;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@Slf4j
public class ApprovalPollingCallback implements OldNotifyCallback {
  @Inject private transient StateExecutionService stateExecutionService;
  @Inject private transient WorkflowExecutionService workflowExecutionService;

  private String workflowExecutionId;
  private String stateExecutionInstanceId;
  private ApprovalPollingJobEntity entity;

  @Inject private transient WaitNotifyEngine waitNotifyEngine;
  @Override
  public void notify(Map<String, ResponseData> response) {
    JiraExecutionData jiraExecutionData = (JiraExecutionData) response.values().iterator().next();

    ExecutionStatus issueStatus = jiraExecutionData.getExecutionStatus();
    log.info("Issue: {} Status from JIRA: {} Current Status {} for approvalId: {}, workflowExecutionId: {} ",
        jiraExecutionData.getIssueId(), issueStatus, jiraExecutionData.getCurrentStatus(), "approval id",
        workflowExecutionId);

    ApprovalStateExecutionData approvalStateExecutionData = ApprovalStateExecutionData.builder()
                                                                .appId(entity.getAppId())
                                                                .approvalId(entity.getApprovalId())
                                                                .workflowExecutionService(workflowExecutionService)
                                                                .issueKey(entity.getIssueId())
                                                                .currentStatus(jiraExecutionData.getCurrentStatus())
                                                                .waitingForChangeWindow(false)
                                                                .build();

    checkApproval(stateExecutionService, waitNotifyEngine, workflowExecutionId, stateExecutionInstanceId,
        jiraExecutionData.getErrorMessage(), issueStatus, approvalStateExecutionData);
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    ErrorNotifyResponseData err = (ErrorNotifyResponseData) response.values().iterator().next();
    log.warn(err.getErrorMessage());
    log.warn(
        "Error occurred while callback polling JIRA status. Continuing to poll next minute. approvalId: {}, workflowExecutionId: {} , issueId: {}",
        entity.getApprovalId(), entity.getWorkflowExecutionId(), entity.getIssueId());
  }
}
