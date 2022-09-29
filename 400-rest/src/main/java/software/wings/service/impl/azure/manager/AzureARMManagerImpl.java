/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.azure.manager;

import static io.harness.azure.model.AzureConstants.DEFAULT_SYNC_AZURE_VMSS_TIMEOUT_MIN;
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
import io.harness.delegate.beans.azure.ManagementGroupData;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.AzureTaskResponse;
import io.harness.delegate.task.azure.arm.AzureARMTaskParameters;
import io.harness.delegate.task.azure.arm.AzureARMTaskType;
import io.harness.delegate.task.azure.arm.response.AzureARMListManagementGroupResponse;
import io.harness.delegate.task.azure.arm.response.AzureARMListSubscriptionLocationsResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AzureConfig;
import software.wings.beans.TaskType;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.azure.manager.AzureARMManager;
import software.wings.sm.states.azure.AzureStateHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class AzureARMManagerImpl implements AzureARMManager {
  @Inject private DelegateService delegateService;
  @Inject private AzureStateHelper azureStateHelper;

  @Override
  public List<String> listSubscriptionLocations(
      AzureConfig azureConfig, List<EncryptedDataDetail> encryptionDetails, String appId, String subscriptionId) {
    AzureARMTaskParameters parameters = new AzureARMTaskParameters();
    parameters.setCommandType(AzureARMTaskType.LIST_SUBSCRIPTION_LOCATIONS);
    parameters.setSubscriptionId(subscriptionId);

    AzureTaskResponse azureTaskExecutionResponse = executeTask(parameters, azureConfig, encryptionDetails, appId);
    return ((AzureARMListSubscriptionLocationsResponse) azureTaskExecutionResponse).getLocations();
  }

  @Override
  public List<String> listAzureCloudProviderLocations(
      AzureConfig azureConfig, List<EncryptedDataDetail> encryptionDetails, String appId) {
    AzureARMTaskParameters parameters = new AzureARMTaskParameters();
    parameters.setCommandType(AzureARMTaskType.LIST_SUBSCRIPTION_LOCATIONS);

    AzureTaskResponse azureTaskExecutionResponse = executeTask(parameters, azureConfig, encryptionDetails, appId);
    return ((AzureARMListSubscriptionLocationsResponse) azureTaskExecutionResponse).getLocations();
  }

  @Override
  public List<ManagementGroupData> listManagementGroups(
      AzureConfig azureConfig, List<EncryptedDataDetail> encryptionDetails, String appId) {
    AzureARMTaskParameters parameters = new AzureARMTaskParameters();
    parameters.setCommandType(AzureARMTaskType.LIST_MNG_GROUP);

    AzureTaskResponse azureTaskExecutionResponse = executeTask(parameters, azureConfig, encryptionDetails, appId);
    return ((AzureARMListManagementGroupResponse) azureTaskExecutionResponse).getMngGroups();
  }

  private AzureTaskResponse executeTask(AzureARMTaskParameters parameters, AzureConfig azureConfig,
      List<EncryptedDataDetail> encryptionDetails, String appId) {
    AzureTaskExecutionRequest request = AzureTaskExecutionRequest.builder()
                                            .azureConfigDTO(azureStateHelper.createAzureConfigDTO(azureConfig))
                                            .azureConfigEncryptionDetails(encryptionDetails)
                                            .azureTaskParameters(parameters)
                                            .build();
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(azureConfig.getAccountId())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, isNotEmpty(appId) ? appId : GLOBAL_APP_ID)
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.AZURE_ARM_TASK.name())
                      .parameters(new Object[] {request})
                      .timeout(TimeUnit.MINUTES.toMillis(DEFAULT_SYNC_AZURE_VMSS_TIMEOUT_MIN))
                      .build())
            .build();
    try {
      DelegateResponseData notifyResponseData = delegateService.executeTask(delegateTask);
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
}
