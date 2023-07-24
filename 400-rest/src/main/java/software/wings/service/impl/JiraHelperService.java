/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.exception.WingsException.USER;
import static io.harness.jira.JiraAction.CHECK_APPROVAL;
import static io.harness.jira.JiraAction.FETCH_ISSUE;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.service.ApprovalUtils.checkApproval;
import static software.wings.service.impl.AssignDelegateServiceImpl.SCOPE_WILDCARD;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.HarnessJiraException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.jira.JiraAction;
import io.harness.jira.JiraCreateMetaResponse;
import io.harness.jira.JiraUserData;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.api.ApprovalStateExecutionData;
import software.wings.api.jira.JiraExecutionData;
import software.wings.app.MainConfiguration;
import software.wings.beans.JiraConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.approval.ApprovalPollingJobEntity;
import software.wings.beans.approval.JiraApprovalParams;
import software.wings.beans.jira.JiraTaskParameters;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.annotations.Transient;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONArray;

/**
 * All Jira apis should be accessed via this object.
 */
@Singleton
@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class JiraHelperService {
  private static final String WORKFLOW_EXECUTION_ID = "workflow";
  private static final long JIRA_DELEGATE_TIMEOUT_MILLIS = 60 * 1000 * 5;
  @Inject private DelegateServiceImpl delegateService;
  @Inject @Transient private transient SecretManager secretManager;
  @Inject SettingsService settingService;
  @Inject WorkflowExecutionService workflowExecutionService;
  @Inject WaitNotifyEngine waitNotifyEngine;
  @Inject private MainConfiguration mainConfiguration;
  @Inject StateExecutionService stateExecutionService;
  @Inject private FeatureFlagService featureFlagService;

  private static final String JIRA_APPROVAL_API_PATH = "api/ticketing/jira-approval/";
  public static final String APP_ID_KEY = "app_id";
  public static final String WORKFLOW_EXECUTION_ID_KEY = "workflow_execution_id";
  public static final String APPROVAL_FIELD_KEY = "approval_field";
  public static final String APPROVAL_VALUE_KEY = "approval_value";
  public static final String REJECTION_FIELD_KEY = "rejection_field";
  public static final String REJECTION_VALUE_KEY = "rejection_value";
  public static final String APPROVAL_ID_KEY = "approval_id";
  @Inject private software.wings.security.SecretManager secretManagerForToken;

  /**
   * Validate credential.
   */
  public void validateCredential(JiraConfig jiraConfig) {
    String accountId = jiraConfig.getAccountId();
    JiraTaskParameters jiraTaskParameters =
        JiraTaskParameters.builder().accountId(accountId).jiraAction(JiraAction.AUTH).build();

    jiraTaskParameters.setJiraConfig(jiraConfig);
    jiraTaskParameters.setEncryptionDetails(
        secretManager.getEncryptionDetails(jiraConfig, APP_ID_KEY, WORKFLOW_EXECUTION_ID));

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(accountId)
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, SCOPE_WILDCARD)
                                    .tags(jiraConfig.getDelegateSelectors())
                                    .data(TaskData.builder()
                                              .async(false)
                                              .taskType(TaskType.JIRA.name())
                                              .parameters(new Object[] {jiraTaskParameters})
                                              .timeout(JIRA_DELEGATE_TIMEOUT_MILLIS)
                                              .build())
                                    .build();

    try {
      DelegateResponseData responseData = delegateService.executeTaskV2(delegateTask);
      if (responseData instanceof RemoteMethodReturnValueData) {
        RemoteMethodReturnValueData remoteMethodReturnValueData = (RemoteMethodReturnValueData) responseData;
        if (remoteMethodReturnValueData.getException() instanceof InvalidRequestException) {
          throw(InvalidRequestException)(remoteMethodReturnValueData.getException());
        } else {
          throw new HarnessJiraException(
              "Unexpected error during authentication to JIRA server " + remoteMethodReturnValueData.getReturnValue(),
              WingsException.USER);
        }
      } else if (responseData instanceof JiraExecutionData) {
        JiraExecutionData jiraExecutionData = (JiraExecutionData) responseData;

        if (jiraExecutionData.getExecutionStatus() != ExecutionStatus.SUCCESS) {
          throw new HarnessJiraException(
              "Failed to Authenticate with JIRA Server. " + jiraExecutionData.getErrorMessage(), WingsException.USER);
        }
      } else {
        log.error("Unexpected error during authentication to JIRA server " + responseData);
        throw new HarnessJiraException("Unexpected error during authentication to JIRA server.", WingsException.USER);
      }
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new HarnessJiraException(
          "Unexpected error during authentication to JIRA server " + e.getMessage(), e, WingsException.USER);
    }
  }

  public void handleJiraPolling(ApprovalPollingJobEntity entity) {
    if (featureFlagService.isEnabled(FeatureName.CDS_PAUSE_JIRA_APPROVAL_CG, entity.getAccountId())) {
      return;
    }
    JiraExecutionData jiraExecutionData = null;
    String issueId = entity.getIssueId();
    String approvalId = entity.getApprovalId();
    String workflowExecutionId = entity.getWorkflowExecutionId();
    String appId = entity.getAppId();
    String stateExecutionInstanceId = entity.getStateExecutionInstanceId();
    try {
      jiraExecutionData = getApprovalStatus(entity);
    } catch (Exception ex) {
      log.warn(
          "Error occurred while polling JIRA status. Continuing to poll next minute. approvalId: {}, workflowExecutionId: {} , issueId: {}",
          entity.getApprovalId(), entity.getWorkflowExecutionId(), entity.getIssueId(), ex);
      return;
    }

    ExecutionStatus issueStatus = jiraExecutionData.getExecutionStatus();
    log.info("Issue: {} Status from JIRA: {} Current Status {} for approvalId: {}, workflowExecutionId: {} ", issueId,
        issueStatus, jiraExecutionData.getCurrentStatus(), approvalId, workflowExecutionId);

    ApprovalStateExecutionData approvalStateExecutionData = ApprovalStateExecutionData.builder()
                                                                .appId(appId)
                                                                .approvalId(approvalId)
                                                                .workflowExecutionService(workflowExecutionService)
                                                                .issueKey(issueId)
                                                                .currentStatus(jiraExecutionData.getCurrentStatus())
                                                                .waitingForChangeWindow(false)
                                                                .build();

    checkApproval(stateExecutionService, waitNotifyEngine, workflowExecutionId, stateExecutionInstanceId,
        jiraExecutionData.getErrorMessage(), issueStatus, approvalStateExecutionData);
  }

  public JSONArray getProjects(String connectorId, String accountId, String appId) {
    JiraTaskParameters jiraTaskParameters =
        JiraTaskParameters.builder().accountId(accountId).jiraAction(JiraAction.GET_PROJECTS).build();

    JiraExecutionData jiraExecutionData =
        runTask(accountId, appId, connectorId, jiraTaskParameters, DEFAULT_SYNC_CALL_TIMEOUT);

    if (jiraExecutionData.getExecutionStatus() != ExecutionStatus.SUCCESS) {
      throw new InvalidRequestException(jiraExecutionData.getErrorMessage(), USER);
    }
    return jiraExecutionData.getProjects();
  }

  public JiraExecutionData fetchIssue(
      JiraApprovalParams jiraApprovalParams, String accountId, String appId, String approvalId) {
    JiraTaskParameters jiraTaskParameters = JiraTaskParameters.builder()
                                                .accountId(accountId)
                                                .appId(appId)
                                                .approvalId(approvalId)
                                                .approvalField(jiraApprovalParams.getApprovalField())
                                                .jiraAction(FETCH_ISSUE)
                                                .issueId(jiraApprovalParams.getIssueId())
                                                .build();
    return runTask(
        accountId, appId, jiraApprovalParams.getJiraConnectorId(), jiraTaskParameters, DEFAULT_SYNC_CALL_TIMEOUT);
  }

  /**
   * Fetch list of fields and list of value options for each field.
   *
   * @param connectorId
   * @param project
   * @param accountId
   * @param appId
   * @return
   */
  public Object getFieldOptions(String connectorId, String project, String accountId, String appId) {
    JiraTaskParameters jiraTaskParameters = JiraTaskParameters.builder()
                                                .accountId(accountId)
                                                .jiraAction(JiraAction.GET_FIELDS_OPTIONS)
                                                .project(project)
                                                .build();

    JiraExecutionData jiraExecutionData =
        runTask(accountId, appId, connectorId, jiraTaskParameters, DEFAULT_SYNC_CALL_TIMEOUT);
    if (jiraExecutionData.getExecutionStatus() != ExecutionStatus.SUCCESS) {
      throw new HarnessJiraException("Failed to fetch IssueType and Priorities", WingsException.USER);
    }

    return jiraExecutionData.getFields();
  }

  public Object getStatuses(String connectorId, String project, String accountId, String appId) {
    JiraTaskParameters jiraTaskParameters =
        JiraTaskParameters.builder().accountId(accountId).jiraAction(JiraAction.GET_STATUSES).project(project).build();

    JiraExecutionData jiraExecutionData =
        runTask(accountId, appId, connectorId, jiraTaskParameters, DEFAULT_SYNC_CALL_TIMEOUT);
    if (jiraExecutionData.getExecutionStatus() != ExecutionStatus.SUCCESS) {
      throw new HarnessJiraException("Failed to fetch Status for this project", WingsException.USER);
    }
    return jiraExecutionData.getStatuses();
  }

  private JiraExecutionData runTask(
      String accountId, String appId, String connectorId, JiraTaskParameters jiraTaskParameters, long timeoutMillis) {
    SettingAttribute settingAttribute = settingService.getByAccountAndId(accountId, connectorId);
    notNullCheck("Jira connector may be deleted.", settingAttribute, USER);
    JiraConfig jiraConfig = (JiraConfig) settingAttribute.getValue();
    jiraTaskParameters.setJiraConfig(jiraConfig);
    jiraTaskParameters.setEncryptionDetails(
        secretManager.getEncryptionDetails(jiraConfig, appId, WORKFLOW_EXECUTION_ID));
    long timeout = Long.max(timeoutMillis, JIRA_DELEGATE_TIMEOUT_MILLIS);
    if (featureFlagService.isEnabled(FeatureName.CDS_RECONFIGURE_JIRA_APPROVAL_TIMEOUT, accountId)) {
      timeout = Long.min(timeoutMillis, JIRA_DELEGATE_TIMEOUT_MILLIS);
      log.info("Timeout configured for the Jira delegate task is {}", timeout);
    }
    try {
      DelegateTask delegateTask = DelegateTask.builder()
                                      .accountId(accountId)
                                      .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, appId)
                                      .tags(jiraConfig.getDelegateSelectors())
                                      .data(TaskData.builder()
                                                .async(false)
                                                .taskType(TaskType.JIRA.name())
                                                .parameters(new Object[] {jiraTaskParameters})
                                                .timeout(timeout)
                                                .build())
                                      .build();
      DelegateResponseData responseData = delegateService.executeTaskV2(delegateTask);

      if (jiraTaskParameters.getJiraAction() == CHECK_APPROVAL && delegateTask != null) {
        log.info("Delegate task Id = {}, for Polling Jira Approval for IssueId {}", delegateTask.getUuid(),
            jiraTaskParameters.getIssueId());
      }
      if (responseData instanceof JiraExecutionData) {
        return (JiraExecutionData) responseData;
      } else {
        return JiraExecutionData.builder().errorMessage("Delegate task failed with an exception").build();
      }
    } catch (Exception exception) {
      return JiraExecutionData.builder().errorMessage("Delegate task failed with an exception").build();
    }
  }

  public JiraCreateMetaResponse getCreateMetadata(String connectorId, String expand, String project, String accountId,
      String appId, long timeoutMillis, String issueType) {
    JiraTaskParameters jiraTaskParameters =
        JiraTaskParameters.builder()
            .accountId(accountId)
            .jiraAction(JiraAction.GET_CREATE_METADATA)
            .createmetaExpandParam(expand)
            .issueType(issueType)
            .useNewMeta(featureFlagService.isEnabled(FeatureName.SPG_USE_NEW_METADATA, accountId))
            .project(project)
            .build();

    JiraExecutionData jiraExecutionData = runTask(accountId, appId, connectorId, jiraTaskParameters, timeoutMillis);
    if (jiraExecutionData.getExecutionStatus() != ExecutionStatus.SUCCESS) {
      throw new HarnessJiraException("Failed to fetch Issue Metadata", WingsException.USER);
    }
    return jiraExecutionData.getCreateMetadata();
  }

  public List<JiraUserData> searchUser(
      String connectorId, String accountId, String appId, long timeoutMillis, String userQuery, String offset) {
    if (featureFlagService.isEnabled(FeatureName.ALLOW_USER_TYPE_FIELDS_JIRA, accountId)) {
      JiraTaskParameters jiraTaskParameters = JiraTaskParameters.builder()
                                                  .accountId(accountId)
                                                  .userQuery(userQuery)
                                                  .userQueryOffset(offset)
                                                  .jiraAction(JiraAction.SEARCH_USER)
                                                  .build();

      JiraExecutionData jiraExecutionData = runTask(accountId, appId, connectorId, jiraTaskParameters, timeoutMillis);
      if (jiraExecutionData.getExecutionStatus() != ExecutionStatus.SUCCESS) {
        throw new HarnessJiraException("Failed to fetch user list", WingsException.USER);
      }
      return jiraExecutionData.getUserSearchList();
    }
    throw new HarnessJiraException("Search User not available for this account", USER);
  }

  public JiraExecutionData getApprovalStatus(String connectorId, String accountId, String appId, String issueId,
      String approvalField, String approvalValue, String rejectionField, String rejectionValue) {
    JiraTaskParameters jiraTaskParameters = JiraTaskParameters.builder()
                                                .accountId(accountId)
                                                .issueId(issueId)
                                                .jiraAction(JiraAction.CHECK_APPROVAL)
                                                .approvalField(approvalField)
                                                .approvalValue(approvalValue)
                                                .rejectionField(rejectionField)
                                                .rejectionValue(rejectionValue)
                                                .build();
    JiraExecutionData jiraExecutionData =
        runTask(accountId, appId, connectorId, jiraTaskParameters, DEFAULT_SYNC_CALL_TIMEOUT);
    log.info("Polling Approval for IssueId = {}", issueId);
    return jiraExecutionData;
  }

  public JiraExecutionData createJira(
      String accountId, String appId, String jiraConfigId, JiraTaskParameters jiraTaskParameters) {
    jiraTaskParameters.setJiraAction(JiraAction.CREATE_TICKET);
    return runTask(accountId, appId, jiraConfigId, jiraTaskParameters, DEFAULT_SYNC_CALL_TIMEOUT);
  }

  public JiraExecutionData getApprovalStatus(ApprovalPollingJobEntity approvalPollingJobEntity) {
    JiraTaskParameters jiraTaskParameters = JiraTaskParameters.builder()
                                                .accountId(approvalPollingJobEntity.getAccountId())
                                                .issueId(approvalPollingJobEntity.getIssueId())
                                                .jiraAction(JiraAction.CHECK_APPROVAL)
                                                .approvalField(approvalPollingJobEntity.getApprovalField())
                                                .approvalValue(approvalPollingJobEntity.getApprovalValue())
                                                .rejectionField(approvalPollingJobEntity.getRejectionField())
                                                .rejectionValue(approvalPollingJobEntity.getRejectionValue())
                                                .build();
    JiraExecutionData jiraExecutionData =
        runTask(approvalPollingJobEntity.getAccountId(), approvalPollingJobEntity.getAppId(),
            approvalPollingJobEntity.getConnectorId(), jiraTaskParameters, DEFAULT_SYNC_CALL_TIMEOUT);
    log.info("Polling Approval for IssueId = {}", approvalPollingJobEntity.getIssueId());
    return jiraExecutionData;
  }
}
