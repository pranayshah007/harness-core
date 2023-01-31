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

import io.harness.azure.model.AzureVMData;
import io.harness.azure.model.SubscriptionData;
import io.harness.azure.model.VirtualMachineScaleSetData;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.azure.request.AzureVMSSGetVirtualMachineScaleSetParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListLoadBalancerBackendPoolsNamesParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListLoadBalancersNamesParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListResourceGroupsNamesParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListSubscriptionsParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListVMDataParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListVirtualMachineScaleSetsParameters;
import io.harness.delegate.task.azure.request.AzureVMSSTaskParameters;
import io.harness.delegate.task.azure.response.AzureVMSSGetVirtualMachineScaleSetResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListLoadBalancerBackendPoolsNamesResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListLoadBalancersNamesResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListResourceGroupsNamesResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListSubscriptionsResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListVMDataResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListVirtualMachineScaleSetsResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AzureConfig;
import software.wings.beans.TaskType;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.azure.manager.AzureVMSSHelperServiceManager;
import software.wings.sm.states.azure.AzureStateHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class AzureVMSSHelperServiceManagerImpl implements AzureVMSSHelperServiceManager {
  @Inject private DelegateService delegateService;
  @Inject private transient AzureStateHelper azureStateHelper;

  @Override
  public List<SubscriptionData> listSubscriptions(
      AzureConfig azureConfig, List<EncryptedDataDetail> encryptionDetails, String appId) {
    AzureVMSSTaskResponse response = executeTask(azureConfig.getAccountId(),
        AzureVMSSListSubscriptionsParameters.builder().build(), azureConfig, encryptionDetails, appId);

    return ((AzureVMSSListSubscriptionsResponse) response).getSubscriptions();
  }

  @Override
  public List<String> listResourceGroupsNames(
      AzureConfig azureConfig, String subscriptionId, List<EncryptedDataDetail> encryptionDetails, String appId) {
    AzureVMSSTaskResponse response = executeTask(azureConfig.getAccountId(),
        AzureVMSSListResourceGroupsNamesParameters.builder().subscriptionId(subscriptionId).build(), azureConfig,
        encryptionDetails, appId);

    return ((AzureVMSSListResourceGroupsNamesResponse) response).getResourceGroupsNames();
  }

  @Override
  public List<VirtualMachineScaleSetData> listVirtualMachineScaleSets(AzureConfig azureConfig, String subscriptionId,
      String resourceGroupName, List<EncryptedDataDetail> encryptionDetails, String appId) {
    AzureVMSSTaskResponse response = executeTask(azureConfig.getAccountId(),
        AzureVMSSListVirtualMachineScaleSetsParameters.builder()
            .resourceGroupName(resourceGroupName)
            .subscriptionId(subscriptionId)
            .build(),
        azureConfig, encryptionDetails, appId);

    return ((AzureVMSSListVirtualMachineScaleSetsResponse) response).getVirtualMachineScaleSets();
  }

  @Override
  public VirtualMachineScaleSetData getVirtualMachineScaleSet(AzureConfig azureConfig, String subscriptionId,
      String resourceGroupName, String vmssName, List<EncryptedDataDetail> encryptionDetails, String appId) {
    AzureVMSSTaskResponse response = executeTask(azureConfig.getAccountId(),
        AzureVMSSGetVirtualMachineScaleSetParameters.builder()
            .subscriptionId(subscriptionId)
            .resourceGroupName(resourceGroupName)
            .vmssName(vmssName)
            .build(),
        azureConfig, encryptionDetails, appId);

    return ((AzureVMSSGetVirtualMachineScaleSetResponse) response).getVirtualMachineScaleSet();
  }

  @Override
  public List<String> listLoadBalancersNames(AzureConfig azureConfig, String subscriptionId, String resourceGroupName,
      List<EncryptedDataDetail> encryptionDetails, String appId) {
    AzureVMSSTaskResponse response = executeTask(azureConfig.getAccountId(),
        AzureVMSSListLoadBalancersNamesParameters.builder()
            .subscriptionId(subscriptionId)
            .resourceGroupName(resourceGroupName)
            .build(),
        azureConfig, encryptionDetails, appId);

    return ((AzureVMSSListLoadBalancersNamesResponse) response).getLoadBalancersNames();
  }

  @Override
  public List<String> listLoadBalancerBackendPoolsNames(AzureConfig azureConfig, String subscriptionId,
      String resourceGroupName, String loadBalancerName, List<EncryptedDataDetail> encryptionDetails, String appId) {
    AzureVMSSTaskResponse response = executeTask(azureConfig.getAccountId(),
        AzureVMSSListLoadBalancerBackendPoolsNamesParameters.builder()
            .subscriptionId(subscriptionId)
            .resourceGroupName(resourceGroupName)
            .loadBalancerName(loadBalancerName)
            .build(),
        azureConfig, encryptionDetails, appId);

    return ((AzureVMSSListLoadBalancerBackendPoolsNamesResponse) response).getLoadBalancerBackendPoolsNames();
  }

  @Override
  public List<AzureVMData> listVMSSVirtualMachines(AzureConfig azureConfig, String subscriptionId,
      String resourceGroupName, String vmssId, List<EncryptedDataDetail> encryptionDetails, String appId) {
    AzureVMSSTaskResponse response = executeTask(azureConfig.getAccountId(),
        AzureVMSSListVMDataParameters.builder()
            .subscriptionId(subscriptionId)
            .resourceGroupName(resourceGroupName)
            .vmssId(vmssId)
            .build(),
        azureConfig, encryptionDetails, appId);
    return ((AzureVMSSListVMDataResponse) response).getVmData();
  }

  private AzureVMSSTaskResponse executeTask(String accountId, AzureVMSSTaskParameters parameters,
      AzureConfig azureConfig, List<EncryptedDataDetail> azureEncryptionDetails, String appId) {
    AzureVMSSCommandRequest request = AzureVMSSCommandRequest.builder()
                                          .azureConfigDTO(azureStateHelper.createAzureConfigDTO(azureConfig))
                                          .azureConfigEncryptionDetails(azureEncryptionDetails)
                                          .azureVMSSTaskParameters(parameters)
                                          .build();

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, isNotEmpty(appId) ? appId : GLOBAL_APP_ID)
            .data(TaskData.builder()
                      .async(false)
                      .taskType(TaskType.AZURE_VMSS_COMMAND_TASK.name())
                      .parameters(new Object[] {request})
                      .timeout(TimeUnit.MINUTES.toMillis(DEFAULT_SYNC_AZURE_VMSS_TIMEOUT_MIN))
                      .build())
            .build();
    try {
      DelegateResponseData notifyResponseData = delegateService.executeTaskV2(delegateTask);
      if (notifyResponseData instanceof ErrorNotifyResponseData) {
        throw new InvalidRequestException(((ErrorNotifyResponseData) notifyResponseData).getErrorMessage(), USER);
      } else if (notifyResponseData instanceof RemoteMethodReturnValueData) {
        throw new InvalidRequestException(
            getMessage(((RemoteMethodReturnValueData) notifyResponseData).getException()), USER);
      } else if (!(notifyResponseData instanceof AzureVMSSTaskExecutionResponse)) {
        throw new InvalidRequestException(
            format("Unknown response from delegate: [%s]", notifyResponseData.getClass().getSimpleName()), USER);
      }
      AzureVMSSTaskExecutionResponse response = (AzureVMSSTaskExecutionResponse) notifyResponseData;
      if (FAILURE == response.getCommandExecutionStatus()) {
        throw new InvalidRequestException(response.getErrorMessage());
      }
      return response.getAzureVMSSTaskResponse();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new InvalidRequestException(ex.getMessage(), ex, USER);
    }
  }
}
