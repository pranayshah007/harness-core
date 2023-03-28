/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.network.SafeHttpCall.execute;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskResponse;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppListWebAppInstancesParameters;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.AzureWebAppInstanceSyncPerpetualProtoTaskParams;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;

import software.wings.delegatetasks.azure.appservice.webapp.taskhandler.AzureWebAppListWebAppInstancesTaskHandler;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Response;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureWebAppInstanceSyncDelegateExecutor implements PerpetualTaskExecutor {
  @Inject private EncryptionService encryptionService;
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private AzureWebAppListWebAppInstancesTaskHandler listWebAppInstancesTaskHandler;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    if (log.isDebugEnabled()) {
      log.debug("Running the InstanceSync perpetual task executor for task id: {}", taskId);
    }

    AzureWebAppInstanceSyncPerpetualProtoTaskParams taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), AzureWebAppInstanceSyncPerpetualProtoTaskParams.class);
    software.wings.beans.AzureConfig azureConfig =
        (software.wings.beans.AzureConfig) kryoSerializer.asObject(taskParams.getAzureConfig().toByteArray());
    AzureTaskExecutionResponse azureTaskExecutionResponse = executeSyncTask(taskParams, azureConfig);
    try {
      log.info(
          "Publish instance sync result to manager for Web App app name: {}, slot name: {} and perpetual task id: {}",
          taskParams.getAppName(), taskParams.getSlotName(), taskId.getId());
      execute(delegateAgentManagerClient.publishInstanceSyncResult(
          taskId.getId(), azureConfig.getAccountId(), azureTaskExecutionResponse));
    } catch (Exception ex) {
      log.error(
          "Failed to publish the instance sync collection result to manager for Web App app name: {}, slot name: {} and perpetual task id: {}",
          taskParams.getAppName(), taskParams.getSlotName(), taskId.getId(), ex);
    }
    return getPerpetualTaskResponse(azureTaskExecutionResponse);
  }

  private AzureTaskExecutionResponse executeSyncTask(
      AzureWebAppInstanceSyncPerpetualProtoTaskParams taskParams, software.wings.beans.AzureConfig azureConfig) {
    List<EncryptedDataDetail> encryptedDataDetails =
        (List<EncryptedDataDetail>) kryoSerializer.asObject(taskParams.getAzureEncryptedData().toByteArray());
    encryptionService.decrypt(azureConfig, encryptedDataDetails, true);

    AzureAppServiceTaskParameters parameters = AzureWebAppListWebAppInstancesParameters.builder()
                                                   .subscriptionId(taskParams.getSubscriptionId())
                                                   .resourceGroupName(taskParams.getResourceGroupName())
                                                   .slotName(taskParams.getSlotName())
                                                   .appName(taskParams.getAppName())
                                                   .build();
    try {
      AzureAppServiceTaskResponse azureAppServiceTaskResponse = listWebAppInstancesTaskHandler.executeTaskInternal(
          parameters, createAzureConfigForDelegateTask(azureConfig), null);
      return AzureTaskExecutionResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .azureTaskResponse(azureAppServiceTaskResponse)
          .build();
    } catch (Exception ex) {
      log.error("Failed to execute instance sync task for Web App app name: {}, slot name: {}", taskParams.getAppName(),
          taskParams.getSlotName(), ex);
      return AzureTaskExecutionResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(ex.getMessage())
          .build();
    }
  }

  private AzureConfig createAzureConfigForDelegateTask(software.wings.beans.AzureConfig azureConfig) {
    String clientId = azureConfig.getClientId();
    String tenantId = azureConfig.getTenantId();
    char[] key = azureConfig.getKey();
    return AzureConfig.builder()
        .clientId(clientId)
        .tenantId(tenantId)
        .key(key)
        .azureEnvironmentType(azureConfig.getAzureEnvironmentType())
        .build();
  }

  private PerpetualTaskResponse getPerpetualTaskResponse(AzureTaskExecutionResponse executionResponse) {
    String message = "success";
    if (CommandExecutionStatus.FAILURE == executionResponse.getCommandExecutionStatus()) {
      message = executionResponse.getErrorMessage();
    }
    return PerpetualTaskResponse.builder().responseCode(Response.SC_OK).responseMessage(message).build();
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }
}
