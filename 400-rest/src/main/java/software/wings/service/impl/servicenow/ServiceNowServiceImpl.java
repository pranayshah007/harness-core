/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.eraro.ErrorCode.SERVICENOW_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.service.ApprovalUtils.checkApproval;
import static software.wings.service.impl.AssignDelegateServiceImpl.SCOPE_WILDCARD;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.exception.ServiceNowException;
import io.harness.exception.WingsException;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.api.ApprovalStateExecutionData;
import software.wings.api.ServiceNowExecutionData;
import software.wings.beans.ServiceNowConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.approval.ApprovalPollingJobEntity;
import software.wings.beans.approval.ServiceNowApprovalParams;
import software.wings.beans.servicenow.ServiceNowFieldType;
import software.wings.beans.servicenow.ServiceNowMetaDTO;
import software.wings.beans.servicenow.ServiceNowTaskParameters;
import software.wings.beans.servicenow.ServiceNowTicketType;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.servicenow.ServiceNowDelegateService;
import software.wings.service.intfc.servicenow.ServiceNowService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.mongodb.morphia.annotations.Transient;

@Singleton
@OwnedBy(CDC)
@Slf4j
public class ServiceNowServiceImpl implements ServiceNowService {
  @Inject @Transient private transient SecretManager secretManager;
  @Inject WorkflowExecutionService workflowExecutionService;
  @Inject WaitNotifyEngine waitNotifyEngine;
  @Inject StateExecutionService stateExecutionService;

  private static final String WORKFLOW_EXECUTION_ID = "workflow-execution-id";
  private static final String APP_ID = "app-id";

  @Inject protected DelegateProxyFactory delegateProxyFactory;
  @Inject SettingsService settingService;

  @Override
  public void validateCredential(SettingAttribute settingAttribute) {
    ServiceNowConfig serviceNowConfig = (ServiceNowConfig) settingAttribute.getValue();
    SyncTaskContext snowTaskContext = SyncTaskContext.builder()
                                          .accountId(settingAttribute.getAccountId())
                                          .appId(SCOPE_WILDCARD)
                                          .tags(serviceNowConfig.getDelegateSelectors())
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                          .build();

    ServiceNowTaskParameters taskParameters =
        ServiceNowTaskParameters.builder()
            .serviceNowConfig(serviceNowConfig)
            .encryptionDetails(secretManager.getEncryptionDetails(serviceNowConfig, APP_ID, WORKFLOW_EXECUTION_ID))
            .build();
    delegateProxyFactory.get(ServiceNowDelegateService.class, snowTaskContext).validateConnector(taskParameters);
  }

  @Override
  public List<ServiceNowMetaDTO> getStates(
      ServiceNowTicketType ticketType, String accountId, String connectorId, String appId) {
    ServiceNowConfig serviceNowConfig = getServiceNowConfig(accountId, connectorId);
    ServiceNowTaskParameters taskParameters =
        getServiceNowTaskParameters(ticketType, accountId, serviceNowConfig, appId);

    SyncTaskContext snowTaskContext = SyncTaskContext.builder()
                                          .accountId(accountId)
                                          .appId(GLOBAL_APP_ID)
                                          .tags(serviceNowConfig.getDelegateSelectors())
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                          .build();

    return delegateProxyFactory.get(ServiceNowDelegateService.class, snowTaskContext).getStates(taskParameters);
  }

  public List<ServiceNowMetaDTO> getApprovalValues(
      ServiceNowTicketType ticketType, String accountId, String connectorId, String appId) {
    ServiceNowConfig serviceNowConfig = getServiceNowConfig(accountId, connectorId);
    ServiceNowTaskParameters taskParameters =
        getServiceNowTaskParameters(ticketType, accountId, serviceNowConfig, appId);

    SyncTaskContext snowTaskContext = SyncTaskContext.builder()
                                          .accountId(accountId)
                                          .appId(GLOBAL_APP_ID)
                                          .tags(serviceNowConfig.getDelegateSelectors())
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                          .build();

    return delegateProxyFactory.get(ServiceNowDelegateService.class, snowTaskContext).getApprovalStates(taskParameters);
  }

  private ServiceNowTaskParameters getServiceNowTaskParameters(
      ServiceNowTicketType ticketType, String accountId, ServiceNowConfig serviceNowConfig, String appId) {
    return ServiceNowTaskParameters.builder()
        .accountId(accountId)
        .ticketType(ticketType)
        .serviceNowConfig(serviceNowConfig)
        .encryptionDetails(secretManager.getEncryptionDetails(serviceNowConfig, appId, WORKFLOW_EXECUTION_ID))
        .build();
  }

  @Override
  public Map<String, List<ServiceNowMetaDTO>> getCreateMeta(
      ServiceNowTicketType ticketType, String accountId, String connectorId, String appId) {
    ServiceNowConfig serviceNowConfig = getServiceNowConfig(accountId, connectorId);
    ServiceNowTaskParameters taskParameters =
        ServiceNowTaskParameters.builder()
            .accountId(accountId)
            .ticketType(ticketType)
            .serviceNowConfig(serviceNowConfig)
            .encryptionDetails(secretManager.getEncryptionDetails(serviceNowConfig, appId, WORKFLOW_EXECUTION_ID))
            .build();
    SyncTaskContext snowTaskContext = SyncTaskContext.builder()
                                          .accountId(accountId)
                                          .appId(GLOBAL_APP_ID)
                                          .tags(serviceNowConfig.getDelegateSelectors())
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                          .build();

    return delegateProxyFactory.get(ServiceNowDelegateService.class, snowTaskContext).getCreateMeta(taskParameters);
  }

  @Override
  public List<ServiceNowMetaDTO> getAdditionalFields(ServiceNowTicketType ticketType, String accountId,
      String connectorId, String appId, ServiceNowFieldType typeFilter) {
    ServiceNowConfig serviceNowConfig = getServiceNowConfig(accountId, connectorId);
    ServiceNowTaskParameters taskParameters =
        ServiceNowTaskParameters.builder()
            .accountId(accountId)
            .ticketType(ticketType)
            .serviceNowConfig(serviceNowConfig)
            .typeFilter(typeFilter)
            .encryptionDetails(secretManager.getEncryptionDetails(serviceNowConfig, appId, WORKFLOW_EXECUTION_ID))
            .build();
    SyncTaskContext snowTaskContext = SyncTaskContext.builder()
                                          .accountId(accountId)
                                          .appId(GLOBAL_APP_ID)
                                          .tags(serviceNowConfig.getDelegateSelectors())
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                          .build();

    return delegateProxyFactory.get(ServiceNowDelegateService.class, snowTaskContext)
        .getAdditionalFields(taskParameters);
  }

  @Override
  public ServiceNowExecutionData getIssueUrl(String appId, String accountId, ServiceNowApprovalParams approvalParams) {
    ServiceNowConfig serviceNowConfig = getServiceNowConfig(accountId, approvalParams.getSnowConnectorId());

    ServiceNowTaskParameters taskParameters =
        ServiceNowTaskParameters.builder()
            .ticketType(approvalParams.getTicketType())
            .issueNumber(approvalParams.getIssueNumber())
            .serviceNowConfig(serviceNowConfig)
            .encryptionDetails(secretManager.getEncryptionDetails(serviceNowConfig, appId, WORKFLOW_EXECUTION_ID))
            .build();

    SyncTaskContext snowTaskContext = SyncTaskContext.builder()
                                          .accountId(accountId)
                                          .appId(GLOBAL_APP_ID)
                                          .tags(serviceNowConfig.getDelegateSelectors())
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                          .build();

    return delegateProxyFactory.get(ServiceNowDelegateService.class, snowTaskContext)
        .getIssueUrl(taskParameters, approvalParams);
  }

  private ServiceNowConfig getServiceNowConfig(String accountId, String connectorId) {
    try {
      SettingAttribute settingAttribute = settingService.getByAccountAndId(accountId, connectorId);
      notNullCheck("Service Now connector may be deleted.", settingAttribute, USER);
      return (ServiceNowConfig) settingAttribute.getValue();
    } catch (Exception e) {
      log.error("Error getting ServiceNow connector for ID: {}", connectorId);
      throw new ServiceNowException(
          "Error getting ServiceNow connector " + ExceptionUtils.getMessage(e), SERVICENOW_ERROR, USER, e);
    }
  }

  @Override
  public ServiceNowExecutionData getApprovalStatus(ApprovalPollingJobEntity approvalPollingJobEntity) {
    ServiceNowConfig serviceNowConfig =
        getServiceNowConfig(approvalPollingJobEntity.getAccountId(), approvalPollingJobEntity.getConnectorId());
    ServiceNowTaskParameters taskParameters =
        ServiceNowTaskParameters.builder()
            .accountId(approvalPollingJobEntity.getAccountId())
            .issueNumber(approvalPollingJobEntity.getIssueNumber())
            .serviceNowConfig(serviceNowConfig)
            .ticketType(approvalPollingJobEntity.getIssueType())
            .encryptionDetails(secretManager.getEncryptionDetails(serviceNowConfig, approvalPollingJobEntity.getAppId(),
                approvalPollingJobEntity.getWorkflowExecutionId()))
            .build();

    SyncTaskContext snowTaskContext = SyncTaskContext.builder()
                                          .accountId(approvalPollingJobEntity.getAccountId())
                                          .appId(approvalPollingJobEntity.getAppId())
                                          .tags(serviceNowConfig.getDelegateSelectors())
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                          .build();

    try {
      // Considering only approval field on the assumption that both approval and rejection fields are equal.
      // Would need to pass rejection field too if we decide to support different fields in the future.
      ServiceNowDelegateService serviceNowDelegateService =
          delegateProxyFactory.get(ServiceNowDelegateService.class, snowTaskContext);

      return checkApprovalStatus(approvalPollingJobEntity, taskParameters, serviceNowDelegateService);

    } catch (ServiceNowException sne) {
      log.error("Exception in servicenow approval", sne);
      ServiceNowExecutionData serviceNowExecutionData =
          ServiceNowExecutionData.builder().executionStatus(ExecutionStatus.FAILED).build();
      serviceNowExecutionData.setErrorMsg(sne.getMessage());
      return serviceNowExecutionData;
    } catch (WingsException we) {
      log.error("Exception in servicenow approval", we);
      return ServiceNowExecutionData.builder().executionStatus(ExecutionStatus.ERROR).build();
    }
  }

  private ServiceNowExecutionData checkApprovalStatus(ApprovalPollingJobEntity approvalPollingJobEntity,
      ServiceNowTaskParameters taskParameters, ServiceNowDelegateService serviceNowDelegateService) {
    ServiceNowExecutionData executionData = serviceNowDelegateService.getIssueUrl(taskParameters,
        ServiceNowApprovalParams.builder()
            .approval(approvalPollingJobEntity.getApproval())
            .rejection(approvalPollingJobEntity.getRejection())
            .changeWindowPresent(approvalPollingJobEntity.isChangeWindowPresent())
            .changeWindowStartField(approvalPollingJobEntity.getChangeWindowStartField())
            .changeWindowEndField(approvalPollingJobEntity.getChangeWindowEndField())
            .build());

    Map<String, String> currentStatus = executionData.getCurrentStatus();

    if (approvalPollingJobEntity.getApproval() != null
        && approvalPollingJobEntity.getApproval().satisfied(currentStatus)) {
      if (approvalPollingJobEntity.withinChangeWindow(currentStatus)) {
        executionData.setWaitingForChangeWindow(false);
        executionData.setExecutionStatus(ExecutionStatus.SUCCESS);
        return executionData;
      }
      executionData.setWaitingForChangeWindow(true);
      executionData.setExecutionStatus(ExecutionStatus.PAUSED);
      return executionData;
    }
    if (approvalPollingJobEntity.getRejection() != null
        && approvalPollingJobEntity.getRejection().satisfied(currentStatus)) {
      executionData.setExecutionStatus(ExecutionStatus.REJECTED);
      return executionData;
    }
    executionData.setExecutionStatus(ExecutionStatus.PAUSED);
    return executionData;
  }

  @Override
  public void handleServiceNowPolling(ApprovalPollingJobEntity entity) {
    ServiceNowExecutionData serviceNowExecutionData = null;
    String approvalId = entity.getApprovalId();
    String issueNumber = entity.getIssueNumber();
    String workflowExecutionId = entity.getWorkflowExecutionId();
    String appId = entity.getAppId();
    String stateExecutionInstanceId = entity.getStateExecutionInstanceId();
    try {
      serviceNowExecutionData = getApprovalStatus(entity);
    } catch (Exception ex) {
      log.error(
          "Error occurred while polling issue status. Continuing to poll next minute. approvalId: {}, workflowExecutionId: {} , issueNumber: {}",
          approvalId, workflowExecutionId, entity.getIssueNumber(), ex);
      return;
    }
    ExecutionStatus issueStatus = serviceNowExecutionData.getExecutionStatus();
    log.info("Issue: {} Status from SNOW: {} Current Status {} for approvalId: {}, workflowExecutionId: {} ",
        entity.getIssueNumber(), issueStatus, serviceNowExecutionData.getCurrentState(), approvalId,
        workflowExecutionId);

    ApprovalStateExecutionData approvalStateExecutionData =
        ApprovalStateExecutionData.builder()
            .appId(appId)
            .approvalId(approvalId)
            .workflowExecutionService(workflowExecutionService)
            .issueKey(issueNumber)
            .currentStatus(serviceNowExecutionData.getCurrentState())
            .waitingForChangeWindow(serviceNowExecutionData.isWaitingForChangeWindow())
            .build();

    checkApproval(stateExecutionService, waitNotifyEngine, workflowExecutionId, stateExecutionInstanceId,
        issueStatus == ExecutionStatus.FAILED ? serviceNowExecutionData.getErrorMsg()
                                              : serviceNowExecutionData.getMessage(),
        issueStatus, approvalStateExecutionData);
  }
}
