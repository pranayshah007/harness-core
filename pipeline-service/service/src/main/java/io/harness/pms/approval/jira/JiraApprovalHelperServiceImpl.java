/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.jira;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.shell.ShellScriptTaskNG.COMMAND_UNIT;
import static io.harness.pms.approval.ApprovalUtils.sendTaskIdProgressUpdate;
import static io.harness.steps.approval.step.entities.ApprovalInstance.ASYNC_DELEGATE_TIMEOUT;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.OrchestrationPublisherName;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.TaskDetails;
import io.harness.delegate.TaskMode;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.TaskType;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.task.jira.JiraTaskNGParameters;
import io.harness.engine.pms.tasks.NgDelegate2TaskExecutor;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HarnessJiraException;
import io.harness.exception.InvalidRequestException;
import io.harness.iterator.PersistenceIterator;
import io.harness.jira.JiraActionNG;
import io.harness.logging.AutoLogContext;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccessWithEncryptionConsumer;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.NGRestUtils;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance.ApprovalInstanceKeys;
import io.harness.steps.approval.step.jira.JiraApprovalHelperService;
import io.harness.steps.approval.step.jira.entities.JiraApprovalInstance;
import io.harness.steps.approval.step.jira.entities.JiraApprovalInstance.JiraApprovalInstanceKeys;
import io.harness.steps.jira.JiraStepHelperService;
import io.harness.utils.IdentifierRefHelper;
import io.harness.waiter.NotifyCallback;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
public class JiraApprovalHelperServiceImpl implements JiraApprovalHelperService {
  private final NgDelegate2TaskExecutor ngDelegate2TaskExecutor;
  private final ConnectorResourceClient connectorResourceClient;
  private final KryoSerializer referenceFalseKryoSerializer;
  private final SecretNGManagerClient secretManagerClient;
  private final WaitNotifyEngine waitNotifyEngine;
  private final LogStreamingStepClientFactory logStreamingStepClientFactory;
  private final String publisherName;
  private final PmsGitSyncHelper pmsGitSyncHelper;
  private final ApprovalInstanceService approvalInstanceService;

  @Inject
  public JiraApprovalHelperServiceImpl(NgDelegate2TaskExecutor ngDelegate2TaskExecutor,
      ConnectorResourceClient connectorResourceClient,
      @Named("referenceFalseKryoSerializer") KryoSerializer referenceFalseKryoSerializer,
      @Named("PRIVILEGED") SecretNGManagerClient secretManagerClient, WaitNotifyEngine waitNotifyEngine,
      LogStreamingStepClientFactory logStreamingStepClientFactory,
      @Named(OrchestrationPublisherName.PUBLISHER_NAME) String publisherName, PmsGitSyncHelper pmsGitSyncHelper,
      JiraStepHelperService jiraStepHelperService, ApprovalInstanceService approvalInstanceService) {
    this.ngDelegate2TaskExecutor = ngDelegate2TaskExecutor;
    this.connectorResourceClient = connectorResourceClient;
    this.referenceFalseKryoSerializer = referenceFalseKryoSerializer;
    this.secretManagerClient = secretManagerClient;
    this.waitNotifyEngine = waitNotifyEngine;
    this.logStreamingStepClientFactory = logStreamingStepClientFactory;
    this.publisherName = publisherName;
    this.pmsGitSyncHelper = pmsGitSyncHelper;
    this.approvalInstanceService = approvalInstanceService;
  }

  @Override
  public void handlePollingEvent(PersistenceIterator<ApprovalInstance> iterator, JiraApprovalInstance instance) {
    try (PmsGitSyncBranchContextGuard ignore1 =
             pmsGitSyncHelper.createGitSyncBranchContextGuard(instance.getAmbiance(), true);
         AutoLogContext ignore2 = instance.autoLogContext()) {
      handlePollingEventInternal(iterator, instance);
    }
  }

  private void handlePollingEventInternal(
      PersistenceIterator<ApprovalInstance> iterator, JiraApprovalInstance instance) {
    Ambiance ambiance = instance.getAmbiance();
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, COMMAND_UNIT, false);

    try {
      log.info("Polling jira approval instance");
      logCallback.saveExecutionLog("-----");
      logCallback.saveExecutionLog(
          LogHelper.color("Fetching jira issue to check approval/rejection criteria", LogColor.White, LogWeight.Bold));

      String instanceId = instance.getId();
      String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
      String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
      String issueKey = instance.getIssueKey();
      String connectorRef = instance.getConnectorRef();
      log.info(String.format("Creating parameters for JiraApproval Instance with id : %s", instanceId));

      validateField(instanceId, ApprovalInstanceKeys.id);
      validateField(accountIdentifier, "accountIdentifier");
      validateField(orgIdentifier, "orgIdentifier");
      validateField(projectIdentifier, "projectIdentifier");
      validateField(issueKey, JiraApprovalInstanceKeys.issueKey);
      validateField(connectorRef, JiraApprovalInstanceKeys.connectorRef);

      JiraTaskNGParameters jiraTaskNGParameters = prepareJiraTaskParameters(
          accountIdentifier, orgIdentifier, projectIdentifier, issueKey, connectorRef, instance.getDelegateSelectors());
      logCallback.saveExecutionLog(
          String.format("Jira url: %s", jiraTaskNGParameters.getJiraConnectorDTO().getJiraUrl()));

      log.info("Queuing delegate task");
      String taskName = "Jira Task: Get Issue";
      String taskId = queueTask(ambiance, instanceId, jiraTaskNGParameters, taskName,
          TaskSelectorYaml.toTaskSelector(instance.getDelegateSelectors()));

      sendTaskIdProgressUpdate(taskId, taskName, instanceId, waitNotifyEngine);

      log.info("Jira Approval Instance queued task with taskId - {}", taskId);
      logCallback.saveExecutionLog(String.format("Jira task: %s", taskId));
    } catch (Exception ex) {
      logCallback.saveExecutionLog(
          String.format("Error creating task for fetching jira issue: %s", ExceptionUtils.getMessage(ex)));
      log.warn("Error creating task for fetching jira issue while polling", ex);
      if (iterator != null && ParameterField.isNotNull(instance.getRetryInterval())) {
        resetNextIteration(iterator, instance);
      }
    }
  }

  private JiraTaskNGParameters prepareJiraTaskParameters(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String issueId, String connectorRef,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    JiraConnectorDTO jiraConnectorDTO =
        getJiraConnector(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef);
    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier(accountIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .build();

    NGAccessWithEncryptionConsumer ngAccessWithEncryptionConsumer;
    if (!isNull(jiraConnectorDTO.getAuth()) && !isNull(jiraConnectorDTO.getAuth().getCredentials())) {
      ngAccessWithEncryptionConsumer = NGAccessWithEncryptionConsumer.builder()
                                           .ngAccess(baseNGAccess)
                                           .decryptableEntity(jiraConnectorDTO.getAuth().getCredentials())
                                           .build();
    } else {
      ngAccessWithEncryptionConsumer =
          NGAccessWithEncryptionConsumer.builder().ngAccess(baseNGAccess).decryptableEntity(jiraConnectorDTO).build();
    }
    List<EncryptedDataDetail> encryptionDataDetails = NGRestUtils.getResponse(
        secretManagerClient.getEncryptionDetails(accountIdentifier, ngAccessWithEncryptionConsumer));

    return JiraTaskNGParameters.builder()
        .action(JiraActionNG.GET_ISSUE)
        .encryptionDetails(encryptionDataDetails)
        .jiraConnectorDTO(jiraConnectorDTO)
        .issueKey(issueId)
        .delegateSelectors(StepUtils.getDelegateSelectorListFromTaskSelectorYaml(delegateSelectors))
        .build();
  }

  private String queueTask(Ambiance ambiance, String approvalInstanceId, JiraTaskNGParameters jiraTaskNGParameters,
      String taskName, List<TaskSelector> selectors) {
    TaskRequest jiraTaskRequest = prepareJiraTaskRequest(ambiance, jiraTaskNGParameters, taskName, selectors);
    String taskId =
        ngDelegate2TaskExecutor.queueTask(ambiance.getSetupAbstractionsMap(), jiraTaskRequest, Duration.ofSeconds(0));
    NotifyCallback callback = JiraApprovalCallback.builder().approvalInstanceId(approvalInstanceId).build();
    waitNotifyEngine.waitForAllOn(publisherName, callback, taskId);
    return taskId;
  }

  private TaskRequest prepareJiraTaskRequest(
      Ambiance ambiance, JiraTaskNGParameters jiraTaskNGParameters, String taskName, List<TaskSelector> selectors) {
    TaskDetails taskDetails =
        TaskDetails.newBuilder()
            .setKryoParameters(
                ByteString.copyFrom(referenceFalseKryoSerializer.asDeflatedBytes(jiraTaskNGParameters) == null
                        ? new byte[] {}
                        : referenceFalseKryoSerializer.asDeflatedBytes(jiraTaskNGParameters)))
            .setExecutionTimeout(com.google.protobuf.Duration.newBuilder()
                                     .setSeconds(TimeUnit.MILLISECONDS.toSeconds(ASYNC_DELEGATE_TIMEOUT))
                                     .build())
            .setMode(TaskMode.ASYNC)
            .setParked(false)
            .setType(TaskType.newBuilder().setType(software.wings.beans.TaskType.JIRA_TASK_NG.name()).build())
            .build();

    return TaskRequestsUtils.prepareTaskRequest(ambiance, taskDetails, new ArrayList<>(), selectors, taskName, false);
  }

  @Override
  public JiraConnectorDTO getJiraConnector(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifierRef) {
    try {
      IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
          connectorIdentifierRef, accountIdentifier, orgIdentifier, projectIdentifier);
      Optional<ConnectorDTO> connectorDTO = NGRestUtils.getResponse(
          connectorResourceClient.get(connectorRef.getIdentifier(), connectorRef.getAccountIdentifier(),
              connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier()));

      if (!connectorDTO.isPresent()) {
        throw new InvalidRequestException(
            String.format("Connector not found for identifier : [%s]", connectorIdentifierRef));
      }
      ConnectorInfoDTO connectorInfoDTO = connectorDTO.get().getConnectorInfo();
      ConnectorConfigDTO connectorConfigDTO = connectorInfoDTO.getConnectorConfig();
      if (connectorConfigDTO instanceof JiraConnectorDTO) {
        return (JiraConnectorDTO) connectorConfigDTO;
      }
      throw new HarnessJiraException(
          format("Connector of other then Jira type was found : [%s] ", connectorIdentifierRef));
    } catch (Exception e) {
      throw new HarnessJiraException(
          format("Error while getting connector information : [%s]", connectorIdentifierRef), e, null);
    }
  }

  private void validateField(String name, String value) {
    if (isBlank(value)) {
      throw new InvalidRequestException(format("Field %s can't be empty", name));
    }
  }
  private void resetNextIteration(PersistenceIterator<ApprovalInstance> iterator, JiraApprovalInstance instance) {
    approvalInstanceService.resetNextIterations(instance.getId(), instance.recalculateNextIterations());
    if (iterator != null) {
      iterator.wakeup();
    }
  }
}
