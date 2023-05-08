/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesync;

import static io.harness.beans.DelegateTask.DELEGATE_QUEUE_TIMEOUT;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.service.InstanceSyncConstants.HARNESS_APPLICATION_ID;
import static software.wings.service.InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID;

import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.azure.request.AzureVMSSListVMDataParameters;
import io.harness.perpetualtask.PerpetualTaskClientBase;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskServiceClient;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AzureConfig;
import software.wings.beans.AzureVMSSInfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.service.impl.azure.manager.AzureVMSSCommandRequest;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.states.azure.AzureVMSSStateHelper;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class AzureVMSSInstanceSyncPerpetualTaskClient
    extends PerpetualTaskClientBase implements PerpetualTaskServiceClient {
  private static final String VMSS_ID = "vmssId";
  @Inject InfrastructureMappingService infraMappingService;
  @Inject SettingsService settingsService;
  @Inject SecretManager secretManager;
  @Inject private transient AzureVMSSStateHelper azureVMSSStateHelper;

  @Override
  public Message getTaskParams(PerpetualTaskClientContext clientContext, boolean referenceFalse) {
    PerpetualTaskData perpetualTaskData = getPerpetualTaskData(clientContext);

    ByteString azureConfigBytes =
        ByteString.copyFrom(getKryoSerializer(referenceFalse).asBytes(perpetualTaskData.getAzureConfig()));
    ByteString encryptedDataBytes =
        ByteString.copyFrom(getKryoSerializer(referenceFalse).asBytes(perpetualTaskData.getEncryptedDataDetails()));

    return AzureVmssInstanceSyncPerpetualTaskParams.newBuilder()
        .setSubscriptionId(perpetualTaskData.getSubscriptionId())
        .setResourceGroupName(perpetualTaskData.getResourceGroupName())
        .setVmssId(perpetualTaskData.getVmssId())
        .setAzureConfig(azureConfigBytes)
        .setAzureEncryptedData(encryptedDataBytes)
        .build();
  }

  @Override
  public DelegateTask getValidationTask(PerpetualTaskClientContext clientContext, String accountId) {
    PerpetualTaskData perpetualTaskData = getPerpetualTaskData(clientContext);
    AzureVMSSListVMDataParameters azureVMSSListVMDataParameters =
        AzureVMSSListVMDataParameters.builder()
            .subscriptionId(perpetualTaskData.getSubscriptionId())
            .resourceGroupName(perpetualTaskData.getResourceGroupName())
            .vmssId(perpetualTaskData.getVmssId())
            .build();
    AzureVMSSCommandRequest request =
        AzureVMSSCommandRequest.builder()
            .azureConfigDTO(azureVMSSStateHelper.createAzureConfigDTO(perpetualTaskData.azureConfig))
            .azureConfigEncryptionDetails(perpetualTaskData.getEncryptedDataDetails())
            .azureVMSSTaskParameters(azureVMSSListVMDataParameters)
            .build();

    return DelegateTask.builder()
        .accountId(accountId)
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
        .data(TaskData.builder()
                  .async(false)
                  .taskType(TaskType.AZURE_VMSS_COMMAND_TASK.name())
                  .parameters(new Object[] {request})
                  .build())
        .expiry(System.currentTimeMillis() + DELEGATE_QUEUE_TIMEOUT)
        .build();
  }

  private PerpetualTaskData getPerpetualTaskData(PerpetualTaskClientContext clientContext) {
    Map<String, String> clientParams = clientContext.getClientParams();
    String appId = clientParams.get(HARNESS_APPLICATION_ID);
    String infraMappingId = clientParams.get(INFRASTRUCTURE_MAPPING_ID);
    String vmssId = clientParams.get(VMSS_ID);
    AzureVMSSInfrastructureMapping infraMapping =
        (AzureVMSSInfrastructureMapping) infraMappingService.get(appId, infraMappingId);

    SettingAttribute settingAttribute = settingsService.get(infraMapping.getComputeProviderSettingId());
    AzureConfig azureConfig = (AzureConfig) settingAttribute.getValue();
    List<EncryptedDataDetail> encryptedDataDetails = secretManager.getEncryptionDetails(azureConfig, null, null);

    return PerpetualTaskData.builder()
        .subscriptionId(infraMapping.getSubscriptionId())
        .resourceGroupName(infraMapping.getResourceGroupName())
        .vmssId(vmssId)
        .azureConfig(azureConfig)
        .encryptedDataDetails(encryptedDataDetails)
        .build();
  }

  @Data
  @Builder
  static class PerpetualTaskData {
    private String subscriptionId;
    private String resourceGroupName;
    private String vmssId;
    private AzureConfig azureConfig;
    private List<EncryptedDataDetail> encryptedDataDetails;
  }
}
