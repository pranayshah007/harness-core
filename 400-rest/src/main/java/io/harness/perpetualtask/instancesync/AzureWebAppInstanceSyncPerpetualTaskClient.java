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
import static software.wings.service.InstanceSyncConstants.VALIDATION_TIMEOUT_MINUTES;

import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppListWebAppInstancesParameters;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskServiceClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.AzureConfig;
import software.wings.beans.AzureWebAppInfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.service.impl.azure.manager.AzureTaskExecutionRequest;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.states.azure.AzureVMSSStateHelper;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class AzureWebAppInstanceSyncPerpetualTaskClient implements PerpetualTaskServiceClient {
  private static final String APP_NAME = "appName";
  private static final String SLOT_NAME = "slotName";

  @Inject private InfrastructureMappingService infraMappingService;
  @Inject private SettingsService settingsService;
  @Inject private SecretManager secretManager;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private AzureVMSSStateHelper azureVMSSStateHelper;

  @Override
  public Message getTaskParams(PerpetualTaskClientContext clientContext) {
    AzureWebAppInstanceSyncPerpetualTaskClient.PerpetualTaskData perpetualTaskData =
        getPerpetualTaskData(clientContext);

    ByteString azureConfigBytes = ByteString.copyFrom(kryoSerializer.asBytes(perpetualTaskData.getAzureConfig()));
    ByteString encryptedDataBytes =
        ByteString.copyFrom(kryoSerializer.asBytes(perpetualTaskData.getEncryptedDataDetails()));

    return AzureWebAppInstanceSyncPerpetualProtoTaskParams.newBuilder()
        .setAzureConfig(azureConfigBytes)
        .setAzureEncryptedData(encryptedDataBytes)
        .setSubscriptionId(perpetualTaskData.getSubscriptionId())
        .setResourceGroupName(perpetualTaskData.getResourceGroupName())
        .setAppName(perpetualTaskData.getAppName())
        .setSlotName(perpetualTaskData.getSlotName())
        .build();
  }

  @Override
  public DelegateTask getValidationTask(PerpetualTaskClientContext clientContext, String accountId) {
    AzureWebAppInstanceSyncPerpetualTaskClient.PerpetualTaskData perpetualTaskData =
        getPerpetualTaskData(clientContext);
    Map<String, String> clientParams = clientContext.getClientParams();

    AzureWebAppListWebAppInstancesParameters listDeploymentDataParameters =
        AzureWebAppListWebAppInstancesParameters.builder()
            .subscriptionId(perpetualTaskData.getSubscriptionId())
            .resourceGroupName(perpetualTaskData.getResourceGroupName())
            .appName(perpetualTaskData.getAppName())
            .slotName(perpetualTaskData.getSlotName())
            .build();

    AzureTaskExecutionRequest request =
        AzureTaskExecutionRequest.builder()
            .azureConfigDTO(azureVMSSStateHelper.createAzureConfigDTO(perpetualTaskData.getAzureConfig()))
            .azureConfigEncryptionDetails(perpetualTaskData.getEncryptedDataDetails())
            .azureTaskParameters(listDeploymentDataParameters)
            .build();

    return DelegateTask.builder()
        .accountId(accountId)
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
        .data(TaskData.builder()
                  .async(false)
                  .taskType(TaskType.AZURE_APP_SERVICE_TASK.name())
                  .parameters(new Object[] {request})
                  .timeout(TimeUnit.MINUTES.toMillis(VALIDATION_TIMEOUT_MINUTES))
                  .build())
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, clientParams.get(HARNESS_APPLICATION_ID))
        .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, clientParams.get(INFRASTRUCTURE_MAPPING_ID))
        .expiry(System.currentTimeMillis() + DELEGATE_QUEUE_TIMEOUT)
        .build();
  }

  private AzureWebAppInstanceSyncPerpetualTaskClient.PerpetualTaskData getPerpetualTaskData(
      PerpetualTaskClientContext clientContext) {
    Map<String, String> clientParams = clientContext.getClientParams();
    String appId = clientParams.get(HARNESS_APPLICATION_ID);
    String infraMappingId = clientParams.get(INFRASTRUCTURE_MAPPING_ID);
    String appName = clientParams.get(APP_NAME);
    String slotName = clientParams.get(SLOT_NAME);
    AzureWebAppInfrastructureMapping infraMapping =
        (AzureWebAppInfrastructureMapping) infraMappingService.get(appId, infraMappingId);

    SettingAttribute settingAttribute = settingsService.get(infraMapping.getComputeProviderSettingId());
    AzureConfig azureConfig = (AzureConfig) settingAttribute.getValue();
    List<EncryptedDataDetail> encryptedDataDetails = secretManager.getEncryptionDetails(azureConfig, null, null);

    return AzureWebAppInstanceSyncPerpetualTaskClient.PerpetualTaskData.builder()
        .azureConfig(azureConfig)
        .encryptedDataDetails(encryptedDataDetails)
        .subscriptionId(infraMapping.getSubscriptionId())
        .resourceGroupName(infraMapping.getResourceGroup())
        .appName(appName)
        .slotName(slotName)
        .build();
  }

  @Data
  @Builder
  static class PerpetualTaskData {
    private AzureConfig azureConfig;
    private List<EncryptedDataDetail> encryptedDataDetails;
    private String subscriptionId;
    private String resourceGroupName;
    private String appName;
    private String slotName;
  }
}
