/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.servicenow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.task.shell.ShellScriptTaskNG.COMMAND_UNIT;
import static io.harness.pms.approval.ApprovalUtils.sendTaskIdProgressUpdate;
import static io.harness.steps.approval.step.entities.ApprovalInstance.ASYNC_DELEGATE_TIMEOUT;

import static software.wings.beans.TaskType.SERVICENOW_TASK_NG;

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
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters;
import io.harness.engine.pms.tasks.NgDelegate2TaskExecutor;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ServiceNowException;
import io.harness.exception.WingsException;
import io.harness.iterator.PersistenceIterator;
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
import io.harness.servicenow.ServiceNowActionNG;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance.ApprovalInstanceKeys;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalHelperService;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance.ServiceNowApprovalInstanceKeys;
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
public class ServiceNowApprovalHelperServiceImpl implements ServiceNowApprovalHelperService {
  private final ConnectorResourceClient connectorResourceClient;
  private final PmsGitSyncHelper pmsGitSyncHelper;
  private final LogStreamingStepClientFactory logStreamingStepClientFactory;
  private final SecretNGManagerClient secretManagerClient;
  private final NgDelegate2TaskExecutor ngDelegate2TaskExecutor;
  private final KryoSerializer referenceFalseKryoSerializer;
  private final String publisherName;
  private final WaitNotifyEngine waitNotifyEngine;
  private final ApprovalInstanceService approvalInstanceService;

  @Inject
  public ServiceNowApprovalHelperServiceImpl(ConnectorResourceClient connectorResourceClient,
      PmsGitSyncHelper pmsGitSyncHelper, LogStreamingStepClientFactory logStreamingStepClientFactory,
      @Named("PRIVILEGED") SecretNGManagerClient secretManagerClient, NgDelegate2TaskExecutor ngDelegate2TaskExecutor,
      @Named("referenceFalseKryoSerializer") KryoSerializer referenceFalseKryoSerializer,
      @Named(OrchestrationPublisherName.PUBLISHER_NAME) String publisherName, WaitNotifyEngine waitNotifyEngine,
      ApprovalInstanceService approvalInstanceService) {
    this.connectorResourceClient = connectorResourceClient;
    this.pmsGitSyncHelper = pmsGitSyncHelper;
    this.logStreamingStepClientFactory = logStreamingStepClientFactory;
    this.secretManagerClient = secretManagerClient;
    this.ngDelegate2TaskExecutor = ngDelegate2TaskExecutor;
    this.referenceFalseKryoSerializer = referenceFalseKryoSerializer;
    this.publisherName = publisherName;
    this.waitNotifyEngine = waitNotifyEngine;
    this.approvalInstanceService = approvalInstanceService;
  }

  @Override
  public ServiceNowConnectorDTO getServiceNowConnector(
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
      if (connectorConfigDTO instanceof ServiceNowConnectorDTO) {
        return (ServiceNowConnectorDTO) connectorConfigDTO;
      }
      throw new ServiceNowException(
          format("Connector configured in step is not of ServiceNow type : [%s] ", connectorIdentifierRef),
          ErrorCode.SERVICENOW_ERROR, WingsException.USER);
    } catch (Exception e) {
      throw new ServiceNowException(
          format("Error retrieving connector information : [%s]. Please check if the connector in the step is valid ",
              connectorIdentifierRef),
          ErrorCode.SERVICENOW_ERROR, WingsException.USER, e);
    }
  }

  @Override
  public void handlePollingEvent(
      PersistenceIterator<ApprovalInstance> iterator, ServiceNowApprovalInstance serviceNowApprovalInstance) {
    try (PmsGitSyncBranchContextGuard ignore1 =
             pmsGitSyncHelper.createGitSyncBranchContextGuard(serviceNowApprovalInstance.getAmbiance(), true);
         AutoLogContext ignore2 = serviceNowApprovalInstance.autoLogContext()) {
      handlePollingEventInternal(iterator, serviceNowApprovalInstance);
    }
  }

  private void handlePollingEventInternal(
      PersistenceIterator<ApprovalInstance> iterator, ServiceNowApprovalInstance instance) {
    Ambiance ambiance = instance.getAmbiance();
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, COMMAND_UNIT, false);
    try {
      log.info("Polling serviceNow approval instance");
      logCallback.saveExecutionLog("-----");
      logCallback.saveExecutionLog(LogHelper.color(
          "Fetching serviceNow ticket to check approval/rejection criteria", LogColor.White, LogWeight.Bold));

      String instanceId = instance.getId();
      String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
      String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
      String ticketNumber = instance.getTicketNumber();
      String connectorRef = instance.getConnectorRef();
      String ticketType = instance.getTicketType();

      validateField(instanceId, ApprovalInstanceKeys.id);
      validateField(accountIdentifier, "accountIdentifier");
      validateField(orgIdentifier, "orgIdentifier");
      validateField(projectIdentifier, "projectIdentifier");
      validateField(ticketNumber, ServiceNowApprovalInstanceKeys.ticketNumber);
      validateField(connectorRef, ServiceNowApprovalInstanceKeys.connectorRef);
      validateField(ticketType, ServiceNowApprovalInstanceKeys.ticketType);

      ServiceNowTaskNGParameters serviceNowTaskNGParameters = prepareServiceNowTaskParameters(accountIdentifier,
          orgIdentifier, projectIdentifier, ticketNumber, connectorRef, instance.getDelegateSelectors(), ticketType);
      logCallback.saveExecutionLog(String.format(
          "ServiceNow url: %s", serviceNowTaskNGParameters.getServiceNowConnectorDTO().getServiceNowUrl()));
      String taskName = "ServiceNow Task: Get Ticket";
      String taskId = queueTask(ambiance, instanceId, serviceNowTaskNGParameters, taskName,
          TaskSelectorYaml.toTaskSelector(instance.getDelegateSelectors()));

      sendTaskIdProgressUpdate(taskId, taskName, instanceId, waitNotifyEngine);

      logCallback.saveExecutionLog(String.format("Created ServiceNow task with id: %s", taskId));

    } catch (Exception ex) {
      logCallback.saveExecutionLog(
          String.format("Error creating task for fetching serviceNow ticket: %s", ExceptionUtils.getMessage(ex)));
      log.warn("Error creating task for fetching serviceNow ticket while polling", ex);
      if (iterator != null && ParameterField.isNotNull(instance.getRetryInterval())) {
        resetNextIteration(iterator, instance);
      }
    }
  }

  private ServiceNowTaskNGParameters prepareServiceNowTaskParameters(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String ticketNumber, String connectorRef,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, String ticketType) {
    ServiceNowConnectorDTO serviceNowConnector =
        getServiceNowConnector(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef);
    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier(accountIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .build();

    NGAccessWithEncryptionConsumer ngAccessWithEncryptionConsumer;
    if (!isNull(serviceNowConnector.getAuth()) && !isNull(serviceNowConnector.getAuth().getCredentials())) {
      ngAccessWithEncryptionConsumer = NGAccessWithEncryptionConsumer.builder()
                                           .ngAccess(baseNGAccess)
                                           .decryptableEntity(serviceNowConnector.getAuth().getCredentials())
                                           .build();
    } else {
      ngAccessWithEncryptionConsumer = NGAccessWithEncryptionConsumer.builder()
                                           .ngAccess(baseNGAccess)
                                           .decryptableEntity(serviceNowConnector)
                                           .build();
    }
    List<EncryptedDataDetail> encryptionDataDetails = NGRestUtils.getResponse(
        secretManagerClient.getEncryptionDetails(accountIdentifier, ngAccessWithEncryptionConsumer));

    return ServiceNowTaskNGParameters.builder()
        .action(ServiceNowActionNG.GET_TICKET)
        .encryptionDetails(encryptionDataDetails)
        .serviceNowConnectorDTO(serviceNowConnector)
        .ticketNumber(ticketNumber)
        .ticketType(ticketType)
        .delegateSelectors(StepUtils.getDelegateSelectorListFromTaskSelectorYaml(delegateSelectors))
        .build();
  }

  private String queueTask(Ambiance ambiance, String approvalInstanceId,
      ServiceNowTaskNGParameters serviceNowTaskNGParameters, String taskName, List<TaskSelector> selectors) {
    TaskRequest serviceNowTaskRequest =
        prepareServiceNowTaskRequest(ambiance, serviceNowTaskNGParameters, taskName, selectors);
    String taskId = ngDelegate2TaskExecutor.queueTask(
        ambiance.getSetupAbstractionsMap(), serviceNowTaskRequest, Duration.ofSeconds(0));
    NotifyCallback callback = ServiceNowApprovalCallback.builder().approvalInstanceId(approvalInstanceId).build();
    waitNotifyEngine.waitForAllOn(publisherName, callback, taskId);
    return taskId;
  }

  private TaskRequest prepareServiceNowTaskRequest(Ambiance ambiance,
      ServiceNowTaskNGParameters serviceNowTaskNGParameters, String taskName, List<TaskSelector> selectors) {
    TaskDetails taskDetails =
        TaskDetails.newBuilder()
            .setKryoParameters(
                ByteString.copyFrom(referenceFalseKryoSerializer.asDeflatedBytes(serviceNowTaskNGParameters) == null
                        ? new byte[] {}
                        : referenceFalseKryoSerializer.asDeflatedBytes(serviceNowTaskNGParameters)))
            .setExecutionTimeout(com.google.protobuf.Duration.newBuilder()
                                     .setSeconds(TimeUnit.MILLISECONDS.toSeconds(ASYNC_DELEGATE_TIMEOUT))
                                     .build())
            .setMode(TaskMode.ASYNC)
            .setParked(false)
            .setType(TaskType.newBuilder().setType(SERVICENOW_TASK_NG.name()).build())
            .build();

    return TaskRequestsUtils.prepareTaskRequest(ambiance, taskDetails, new ArrayList<>(), selectors, taskName, false);
  }

  private void validateField(String value, String name) {
    if (isBlank(value)) {
      throw new InvalidRequestException(format("Field %s can't be empty", name));
    }
  }
  private void resetNextIteration(PersistenceIterator<ApprovalInstance> iterator, ServiceNowApprovalInstance instance) {
    approvalInstanceService.resetNextIterations(instance.getId(), instance.recalculateNextIterations());
    if (iterator != null) {
      iterator.wakeup();
    }
  }
}
