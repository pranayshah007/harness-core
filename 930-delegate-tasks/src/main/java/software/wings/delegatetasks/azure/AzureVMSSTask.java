/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure;

import static io.harness.azure.model.AzureConstants.UNRECOGNIZED_PARAMETERS;
import static io.harness.azure.model.AzureConstants.UNRECOGNIZED_TASK;
import static io.harness.logging.CommandExecutionStatus.FAILURE;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.azure.AzureConfigDTO;
import io.harness.delegate.beans.azure.AzureVMAuthDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.azure.request.AzureVMSSDeployTaskParameters;
import io.harness.delegate.task.azure.request.AzureVMSSSetupTaskParameters;
import io.harness.delegate.task.azure.request.AzureVMSSSwitchRouteTaskParameters;
import io.harness.delegate.task.azure.request.AzureVMSSTaskParameters;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.delegatetasks.azure.taskhandler.AzureVMSSDeployTaskHandler;
import software.wings.delegatetasks.azure.taskhandler.AzureVMSSRollbackTaskHandler;
import software.wings.delegatetasks.azure.taskhandler.AzureVMSSSetupTaskHandler;
import software.wings.delegatetasks.azure.taskhandler.AzureVMSSSwitchRouteTaskHandler;
import software.wings.delegatetasks.azure.taskhandler.AzureVMSSSyncTaskHandler;
import software.wings.delegatetasks.azure.taskhandler.AzureVMSSTaskHandler;
import software.wings.service.impl.azure.manager.AzureVMSSCommandRequest;

import com.google.inject.Inject;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureVMSSTask extends AbstractDelegateRunnableTask {
  @Inject private AzureVMSSSyncTaskHandler azureVMSSSyncTaskHandler;
  @Inject private AzureVMSSSetupTaskHandler setupTaskHandler;
  @Inject private AzureVMSSDeployTaskHandler deployTaskHandler;
  @Inject private AzureVMSSRollbackTaskHandler rollbackTaskHandler;
  @Inject private AzureVMSSSwitchRouteTaskHandler switchRouteTaskHandler;
  @Inject private SecretDecryptionService secretDecryptionService;

  public AzureVMSSTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public AzureVMSSTaskExecutionResponse run(Object[] parameters) {
    throw new NotImplementedException("Not implemented.");
  }

  @Override
  public AzureVMSSTaskExecutionResponse run(TaskParameters parameters) {
    if (!(parameters instanceof AzureVMSSCommandRequest)) {
      String message = format(UNRECOGNIZED_TASK, parameters.getClass().getSimpleName());
      log.error(message);
      return AzureVMSSTaskExecutionResponse.builder().commandExecutionStatus(FAILURE).errorMessage(message).build();
    }

    AzureVMSSCommandRequest azureVMSSCommandRequest = (AzureVMSSCommandRequest) parameters;
    AzureVMSSTaskParameters azureVMSSTaskParameters = azureVMSSCommandRequest.getAzureVMSSTaskParameters();
    decryptTaskParameters(azureVMSSTaskParameters);
    AzureConfig azureConfigForDelegateTask = createAzureConfigForDelegateTask(azureVMSSCommandRequest);
    AzureVMSSTaskHandler handler;
    if (azureVMSSTaskParameters.isSyncTask()) {
      handler = azureVMSSSyncTaskHandler;
    } else {
      switch (azureVMSSTaskParameters.getCommandType()) {
        case AZURE_VMSS_SETUP: {
          if (!(azureVMSSTaskParameters instanceof AzureVMSSSetupTaskParameters)) {
            return failureResponse(azureVMSSTaskParameters);
          }
          handler = setupTaskHandler;
          break;
        }

        case AZURE_VMSS_DEPLOY: {
          if (!(azureVMSSTaskParameters instanceof AzureVMSSDeployTaskParameters)) {
            return failureResponse(azureVMSSTaskParameters);
          }
          AzureVMSSDeployTaskParameters deployTaskParameters = (AzureVMSSDeployTaskParameters) azureVMSSTaskParameters;
          handler = deployTaskParameters.isRollback() ? rollbackTaskHandler : deployTaskHandler;
          break;
        }

        case AZURE_VMSS_SWITCH_ROUTE: {
          if (!(azureVMSSTaskParameters instanceof AzureVMSSSwitchRouteTaskParameters)) {
            return failureResponse(azureVMSSTaskParameters);
          }
          handler = switchRouteTaskHandler;
          break;
        }

        default: {
          String message = format(UNRECOGNIZED_TASK, azureVMSSTaskParameters.getCommandType().name());
          log.error(message);
          return AzureVMSSTaskExecutionResponse.builder().commandExecutionStatus(FAILURE).errorMessage(message).build();
        }
      }
    }
    return handler.executeTask(azureVMSSTaskParameters, azureConfigForDelegateTask);
  }

  private AzureConfig createAzureConfigForDelegateTask(AzureVMSSCommandRequest azureVMSSCommandRequest) {
    AzureConfigDTO azureConfigDTO = azureVMSSCommandRequest.getAzureConfigDTO();
    secretDecryptionService.decrypt(azureConfigDTO, azureVMSSCommandRequest.getAzureConfigEncryptionDetails());
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        azureConfigDTO, azureVMSSCommandRequest.getAzureConfigEncryptionDetails());

    String clientId = azureConfigDTO.getClientId();
    String tenantId = azureConfigDTO.getTenantId();
    char[] key = azureConfigDTO.getKey().getDecryptedValue();
    return AzureConfig.builder()
        .clientId(clientId)
        .tenantId(tenantId)
        .key(key)
        .azureEnvironmentType(azureConfigDTO.getAzureEnvironmentType())
        .build();
  }

  private void decryptTaskParameters(AzureVMSSTaskParameters azureVMSSTaskParameters) {
    if (AzureVMSSTaskParameters.AzureVMSSTaskType.AZURE_VMSS_SETUP == azureVMSSTaskParameters.getCommandType()) {
      AzureVMSSSetupTaskParameters setupTaskParameters = (AzureVMSSSetupTaskParameters) azureVMSSTaskParameters;
      AzureVMAuthDTO azureVmAuthDTO = setupTaskParameters.getAzureVmAuthDTO();
      List<EncryptedDataDetail> vmAuthDTOEncryptionDetails = setupTaskParameters.getVmAuthDTOEncryptionDetails();
      secretDecryptionService.decrypt(azureVmAuthDTO, vmAuthDTOEncryptionDetails);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(azureVmAuthDTO, vmAuthDTOEncryptionDetails);
    }
  }

  private AzureVMSSTaskExecutionResponse failureResponse(AzureVMSSTaskParameters azureVMSSTaskParameters) {
    String message = format(UNRECOGNIZED_PARAMETERS, azureVMSSTaskParameters.getClass().getSimpleName());
    log.error(message);
    return AzureVMSSTaskExecutionResponse.builder().commandExecutionStatus(FAILURE).errorMessage(message).build();
  }
}
