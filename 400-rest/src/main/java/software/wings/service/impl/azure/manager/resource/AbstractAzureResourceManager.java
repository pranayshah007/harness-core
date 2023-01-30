/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.impl.azure.manager.resource;

import static io.harness.azure.model.AzureConstants.DEFAULT_SYNC_AZURE_RESOURCE_TIMEOUT_MIN;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.ExceptionUtils.getMessage;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.FAILURE;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static java.lang.String.format;

import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.azure.AzureConfigDTO;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.AzureTaskResponse;
import io.harness.delegate.task.azure.resource.AzureResourceTaskParameters;
import io.harness.delegate.task.azure.resource.AzureResourceTaskResponse;
import io.harness.delegate.task.azure.resource.operation.AzureResourceOperation;
import io.harness.delegate.task.azure.resource.operation.AzureResourceOperationResponse;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AzureConfig;
import software.wings.beans.TaskType;
import software.wings.service.impl.azure.manager.AzureTaskExecutionRequest;
import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractAzureResourceManager {
  @Inject private DelegateService delegateService;

  protected AzureResourceOperationResponse executionOperation(AzureConfig azureConfig,
      List<EncryptedDataDetail> encryptionDetails, AzureResourceOperation operation, String appId) {
    AzureResourceTaskParameters resourceTaskParameters =
        AzureResourceTaskParameters.builder().operationRequest(operation).build();

    log.info("Start executing operation on manager, operationName: {}, operationDetails: {}",
        operation.getOperationName().getValue(), operation);
    AzureTaskResponse azureTaskExecutionResponse =
        executeTask(resourceTaskParameters, azureConfig, encryptionDetails, appId);

    AzureResourceTaskResponse azureResourceTaskResponse = (AzureResourceTaskResponse) azureTaskExecutionResponse;

    return azureResourceTaskResponse.getOperationResponse();
  }

  private AzureTaskResponse executeTask(AzureResourceTaskParameters parameters, AzureConfig azureConfig,
      List<EncryptedDataDetail> encryptionDetails, String appId) {
    AzureTaskExecutionRequest request = AzureTaskExecutionRequest.builder()
                                            .azureConfigDTO(createAzureConfigDTO(azureConfig))
                                            .azureConfigEncryptionDetails(encryptionDetails)
                                            .azureTaskParameters(parameters)
                                            .build();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(azureConfig.getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, isNotEmpty(appId) ? appId : GLOBAL_APP_ID)
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.AZURE_RESOURCE_TASK.name())
                      .parameters(new Object[] {request})
                      .timeout(TimeUnit.MINUTES.toMillis(DEFAULT_SYNC_AZURE_RESOURCE_TIMEOUT_MIN))
                      .build())
            .build();
    return executeTask(delegateTask);
  }

  private AzureTaskResponse executeTask(DelegateTask delegateTask) {
    try {
      DelegateResponseData notifyResponseData = delegateService.executeTaskV2(delegateTask);
      if (notifyResponseData instanceof ErrorNotifyResponseData) {
        throw new InvalidRequestException(((ErrorNotifyResponseData) notifyResponseData).getErrorMessage(), USER);
      } else if (notifyResponseData instanceof RemoteMethodReturnValueData) {
        throw new InvalidRequestException(
            getMessage(((RemoteMethodReturnValueData) notifyResponseData).getException()), USER);
      } else if (!(notifyResponseData instanceof AzureTaskExecutionResponse)) {
        throw new InvalidRequestException(
            format("Unknown response from delegate: [%s]", notifyResponseData.getClass().getSimpleName()), USER);
      }
      AzureTaskExecutionResponse response = (AzureTaskExecutionResponse) notifyResponseData;
      if (FAILURE == response.getCommandExecutionStatus()) {
        throw new InvalidRequestException(response.getErrorMessage());
      }
      return response.getAzureTaskResponse();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new InvalidRequestException(ex.getMessage(), ex, USER);
    }
  }

  private AzureConfigDTO createAzureConfigDTO(AzureConfig azureConfig) {
    return AzureConfigDTO.builder()
        .clientId(azureConfig.getClientId())
        .key(new SecretRefData(azureConfig.getEncryptedKey(), Scope.ACCOUNT, null))
        .tenantId(azureConfig.getTenantId())
        .azureEnvironmentType(azureConfig.getAzureEnvironmentType())
        .build();
  }
}
