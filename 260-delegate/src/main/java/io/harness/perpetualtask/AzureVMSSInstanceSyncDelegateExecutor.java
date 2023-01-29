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
import io.harness.delegate.task.azure.request.AzureVMSSListVMDataParameters;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.AzureVmssInstanceSyncPerpetualTaskParams;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;

import software.wings.delegatetasks.azure.taskhandler.AzureVMSSSyncTaskHandler;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Response;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureVMSSInstanceSyncDelegateExecutor implements PerpetualTaskExecutor {
  @Inject private EncryptionService encryptionService;
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private AzureVMSSSyncTaskHandler azureVMSSSyncTaskHandler;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    if (log.isDebugEnabled()) {
      log.debug("Running the InstanceSync perpetual task executor for task id: {}", taskId);
    }
    AzureVmssInstanceSyncPerpetualTaskParams taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), AzureVmssInstanceSyncPerpetualTaskParams.class);
    software.wings.beans.AzureConfig azureConfig =
        (software.wings.beans.AzureConfig) kryoSerializer.asObject(taskParams.getAzureConfig().toByteArray());
    AzureVMSSTaskExecutionResponse azureVMSSTaskExecutionResponse = executeSyncTask(taskParams, azureConfig);
    try {
      log.info("Publish instance sync result to manager for VMSS id {} and perpetual task {}", taskParams.getVmssId(),
          taskId.getId());
      execute(delegateAgentManagerClient.publishInstanceSyncResult(
          taskId.getId(), azureConfig.getAccountId(), azureVMSSTaskExecutionResponse));
    } catch (Exception ex) {
      log.error("Failed to publish the instance sync collection result to manager for VMSS id {} and perpetual task {}",
          taskParams.getVmssId(), taskId.getId(), ex);
    }
    return getPerpetualTaskResponse(azureVMSSTaskExecutionResponse);
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }

  private PerpetualTaskResponse getPerpetualTaskResponse(AzureVMSSTaskExecutionResponse executionResponse) {
    String message = "success";
    if (CommandExecutionStatus.FAILURE == executionResponse.getCommandExecutionStatus()) {
      message = executionResponse.getErrorMessage();
    }
    return PerpetualTaskResponse.builder().responseCode(Response.SC_OK).responseMessage(message).build();
  }

  private AzureVMSSTaskExecutionResponse executeSyncTask(
      AzureVmssInstanceSyncPerpetualTaskParams taskParams, software.wings.beans.AzureConfig azureConfig) {
    List<EncryptedDataDetail> encryptedDataDetails =
        (List<EncryptedDataDetail>) kryoSerializer.asObject(taskParams.getAzureEncryptedData().toByteArray());
    encryptionService.decrypt(azureConfig, encryptedDataDetails, true);
    AzureVMSSListVMDataParameters parameters = AzureVMSSListVMDataParameters.builder()
                                                   .subscriptionId(taskParams.getSubscriptionId())
                                                   .resourceGroupName(taskParams.getResourceGroupName())
                                                   .vmssId(taskParams.getVmssId())
                                                   .build();
    try {
      return azureVMSSSyncTaskHandler.executeTask(parameters, createAzureConfigForDelegateTask(azureConfig));
    } catch (Exception ex) {
      log.error("Failed to execute instance sync task for VMSS id {}", taskParams.getVmssId(), ex);
      return AzureVMSSTaskExecutionResponse.builder()
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
}
