/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.shell.ShellScriptTaskNG.COMMAND_UNIT;
import static io.harness.exception.WingsException.USER_SRE;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.task.jira.JiraTaskNGResponse;
import io.harness.exception.ApprovalStepNGException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HarnessJiraException;
import io.harness.jira.JiraIssueNG;
import io.harness.logging.AutoLogContext;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.approval.AbstractApprovalCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.serializer.KryoSerializer;
import io.harness.servicenow.misc.TicketNG;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.CriteriaSpecDTO;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.evaluation.CriteriaEvaluator;
import io.harness.steps.approval.step.jira.entities.JiraApprovalInstance;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ResponseData;
import io.harness.waiter.PushThroughNotifyCallback;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Data
@Slf4j
public class JiraApprovalCallback extends AbstractApprovalCallback implements PushThroughNotifyCallback {
  private final String approvalInstanceId;

  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Builder
  public JiraApprovalCallback(String approvalInstanceId) {
    this.approvalInstanceId = approvalInstanceId;
  }

  @Override
  public void push(Map<String, ResponseData> response) {
    JiraApprovalInstance instance = (JiraApprovalInstance) approvalInstanceService.get(approvalInstanceId);
    try (AutoLogContext ignore = instance.autoLogContext()) {
      pushInternal(response);
    }
  }

  private void pushInternal(Map<String, ResponseData> response) {
    JiraApprovalInstance instance = (JiraApprovalInstance) approvalInstanceService.get(approvalInstanceId);
    log.info("Jira Approval Instance callback for instance id - {}", instance.getId());
    Ambiance ambiance = instance.getAmbiance();
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, COMMAND_UNIT, false);

    if (instance.hasExpired()) {
      log.warn("Jira Approval Instance expired for instance id - {}", instance.getId());
      updateApprovalInstanceAndLog(logCallback, "Approval instance has expired", LogColor.Red,
          CommandExecutionStatus.FAILURE, ApprovalStatus.EXPIRED, instance.getId());
    }

    JiraTaskNGResponse jiraTaskNGResponse;
    try {
      ResponseData responseData = response.values().iterator().next();
      BinaryResponseData binaryResponseData = (BinaryResponseData) responseData;
      responseData = (ResponseData) (binaryResponseData.isUsingKryoWithoutReference()
              ? referenceFalseKryoSerializer.asInflatedObject(binaryResponseData.getData())
              : kryoSerializer.asInflatedObject(binaryResponseData.getData()));
      if (responseData instanceof ErrorNotifyResponseData) {
        log.warn("Jira Approval Instance failed to fetch jira issue for instance id - {}", instance.getId());
        handleErrorNotifyResponse(logCallback, (ErrorNotifyResponseData) responseData, "Failed to fetch jira issue:");
        return;
      }

      jiraTaskNGResponse = (JiraTaskNGResponse) responseData;

      // this is checked first to prevent NPE in following validations
      if (isNull(jiraTaskNGResponse.getIssue())) {
        log.info("Invalid issue key");
        String errorMessage = String.format("Invalid issue key: %s", instance.getIssueKey());
        logCallback.saveExecutionLog(LogHelper.color(errorMessage, LogColor.Red), LogLevel.ERROR);
        approvalInstanceService.finalizeStatus(instance.getId(), ApprovalStatus.FAILED, errorMessage);
        return;
      }

      if (!validateProject(instance, jiraTaskNGResponse)) {
        return;
      }

      if (!validateIssueType(instance, jiraTaskNGResponse)) {
        return;
      }

      logCallback.saveExecutionLog(String.format("Issue url: %s", jiraTaskNGResponse.getIssue().getUrl()));
    } catch (Exception ex) {
      logCallback.saveExecutionLog(
          LogHelper.color(String.format("Error fetching jira issue response: %s. Retrying in sometime...",
                              ExceptionUtils.getMessage(ex)),
              LogColor.Red),
          LogLevel.ERROR);
      log.error("Failed to fetch jira issue", ex);
      return;
    }

    try {
      updateTicketFieldsInApprovalInstance(jiraTaskNGResponse.getIssue(), instance);
    } catch (Exception ex) {
      logCallback.saveExecutionLog(
          LogHelper.color(String.format("Error while updating approval with jira ticket fields: %s. Ignoring it...",
                              ExceptionUtils.getMessage(ex)),
              LogColor.Red),
          LogLevel.WARN);
      log.warn("Error while updating approval instance with jira ticket fields", ex);
    }

    try {
      checkApprovalAndRejectionCriteriaAndWithinChangeWindow(jiraTaskNGResponse.getIssue(), instance, logCallback,
          instance.getApprovalCriteria(), instance.getRejectionCriteria());
    } catch (Exception ex) {
      if (ex instanceof ApprovalStepNGException && ((ApprovalStepNGException) ex).isFatal()) {
        handleFatalException(instance, logCallback, (ApprovalStepNGException) ex);
        return;
      }

      logCallback.saveExecutionLog(LogHelper.color(String.format("Error evaluating approval/rejection criteria: %s.",
                                                       ExceptionUtils.getMessage(ex)),
                                       LogColor.Red),
          LogLevel.ERROR);
      throw new HarnessJiraException("Error while evaluating approval/rejection criteria", ex, USER_SRE);
    }
  }

  @VisibleForTesting
  // Return false if validation fails
  protected boolean validateIssueType(JiraApprovalInstance instance, JiraTaskNGResponse jiraTaskNGResponse) {
    if (!isNull(instance.getIssueType())) {
      final String issueType = jiraTaskNGResponse.getIssue().getFields().getOrDefault("Issue Type", "").toString();
      if (!instance.getIssueType().equals(issueType)) {
        String errorMessage = String.format(
            "Invalid issue type. Execution sent: %s. Parsed from issue: %s", instance.getIssueType(), issueType);
        log.warn(errorMessage);
        approvalInstanceService.finalizeStatus(instance.getId(), ApprovalStatus.FAILED, errorMessage);
        return false;
      }
    }
    return true;
  }

  @VisibleForTesting
  // Return false if validation fails
  protected boolean validateProject(JiraApprovalInstance instance, JiraTaskNGResponse jiraTaskNGResponse) {
    if (!isNull(instance.getProjectKey())) {
      final String projectKey = jiraTaskNGResponse.getIssue().getFields().getOrDefault("Project Key", "").toString();
      if (!instance.getProjectKey().equals(projectKey)) {
        String errorMessage = String.format(
            "Invalid project key. Execution sent: %s. Parsed from issue: %s", instance.getProjectKey(), projectKey);
        log.warn(errorMessage);
        approvalInstanceService.finalizeStatus(instance.getId(), ApprovalStatus.FAILED, errorMessage);
        return false;
      }
    }
    return true;
  }

  @Override
  protected void updateTicketFieldsInApprovalInstance(TicketNG ticket, ApprovalInstance instance) {
    approvalInstanceService.updateTicketFieldsInJiraApprovalInstance(
        (JiraApprovalInstance) instance, (JiraIssueNG) ticket);
  }

  @Override
  protected boolean evaluateCriteria(TicketNG ticket, CriteriaSpecDTO criteriaSpec) {
    return CriteriaEvaluator.evaluateCriteria((JiraIssueNG) ticket, criteriaSpec);
  }
}
