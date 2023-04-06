/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.network.SafeHttpCall.execute;

import static java.lang.String.format;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.instancesync.AzureWebAppInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.AzureWebAppToServerInstanceInfoMapper;
import io.harness.delegate.task.azure.appservice.webapp.AzureWebAppDeploymentReleaseData;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.common.AzureAppServiceService;
import io.harness.delegate.task.azure.common.AzureConnectorMapper;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.AzureWebAppDeploymentRelease;
import io.harness.perpetualtask.instancesync.AzureWebAppNGInstanceSyncPerpetualTaskParams;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AzureWebAppInstanceSyncPerpetualTaskExecutor implements PerpetualTaskExecutor {
  private static final String SUCCESS_RESPONSE_MSG = "success";

  @Inject private KryoSerializer kryoSerializer;
  @Inject private AzureAppServiceService azureAppServiceService;
  @Inject private AzureConnectorMapper azureConnectorMapper;
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private SecretDecryptionService secretDecryptionService;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    AzureWebAppNGInstanceSyncPerpetualTaskParams taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), AzureWebAppNGInstanceSyncPerpetualTaskParams.class);

    return executeAzureWebAppInstanceSyncTask(taskId, taskParams);
  }

  private PerpetualTaskResponse executeAzureWebAppInstanceSyncTask(
      PerpetualTaskId taskId, AzureWebAppNGInstanceSyncPerpetualTaskParams taskParams) {
    List<AzureWebAppDeploymentReleaseData> deploymentReleaseDataList = getAzureWebAppDeploymentReleaseData(taskParams);

    List<ServerInstanceInfo> serverInstanceInfoList = deploymentReleaseDataList.stream()
                                                          .map(this::getServerInstanceInfoList)
                                                          .flatMap(Collection::stream)
                                                          .collect(Collectors.toList());

    log.info("Azure WebApp NG Instance sync nInstances: {}, task id: {}",
        isEmpty(serverInstanceInfoList) ? 0 : serverInstanceInfoList.size(), taskId);

    String instanceSyncResponseMsg =
        publishInstanceSyncResult(taskId, taskParams.getAccountId(), serverInstanceInfoList);
    return PerpetualTaskResponse.builder().responseCode(SC_OK).responseMessage(instanceSyncResponseMsg).build();
  }

  private List<AzureWebAppDeploymentReleaseData> getAzureWebAppDeploymentReleaseData(
      AzureWebAppNGInstanceSyncPerpetualTaskParams taskParams) {
    return taskParams.getAzureWebAppDeploymentReleaseListList()
        .stream()
        .map(this::toAzureWebAppDeploymentReleaseData)
        .collect(Collectors.toList());
  }

  private AzureWebAppDeploymentReleaseData toAzureWebAppDeploymentReleaseData(
      AzureWebAppDeploymentRelease azureWebAppDeploymentRelease) {
    return AzureWebAppDeploymentReleaseData.builder()
        .appName(azureWebAppDeploymentRelease.getAppName())
        .subscriptionId(azureWebAppDeploymentRelease.getSubscriptionId())
        .resourceGroupName(azureWebAppDeploymentRelease.getResourceGroupName())
        .slotName(azureWebAppDeploymentRelease.getSlotName())
        .azureWebAppInfraDelegateConfig((AzureWebAppInfraDelegateConfig) kryoSerializer.asObject(
            azureWebAppDeploymentRelease.getAzureWebAppInfraDelegateConfig().toByteArray()))
        .build();
  }

  private List<ServerInstanceInfo> getServerInstanceInfoList(
      AzureWebAppDeploymentReleaseData azureWebAppDeploymentReleaseData) {
    AzureConnectorDTO azureConnectorDTO =
        azureWebAppDeploymentReleaseData.getAzureWebAppInfraDelegateConfig().getAzureConnectorDTO();
    List<EncryptedDataDetail> encryptedDataDetails =
        azureWebAppDeploymentReleaseData.getAzureWebAppInfraDelegateConfig().getEncryptionDataDetails();
    secretDecryptionService.decrypt(azureConnectorDTO.getDecryptableEntities().get(0), encryptedDataDetails);

    List<AzureAppDeploymentData> azureAppDeploymentData = getAzureWebAppDeploymentData(
        azureConnectorMapper.toAzureConfig(azureConnectorDTO), azureWebAppDeploymentReleaseData.getSubscriptionId(),
        azureWebAppDeploymentReleaseData.getResourceGroupName(), azureWebAppDeploymentReleaseData.getAppName(),
        azureWebAppDeploymentReleaseData.getSlotName());

    return AzureWebAppToServerInstanceInfoMapper.toServerInstanceInfoList(azureAppDeploymentData);
  }

  private List<AzureAppDeploymentData> getAzureWebAppDeploymentData(
      AzureConfig azureConfig, String subscriptionId, String resourceGroupName, String webAppName, String slotName) {
    AzureWebClientContext azureWebClientContext = AzureWebClientContext.builder()
                                                      .azureConfig(azureConfig)
                                                      .subscriptionId(subscriptionId)
                                                      .resourceGroupName(resourceGroupName)
                                                      .appName(webAppName)
                                                      .build();

    try {
      return azureAppServiceService.fetchDeploymentData(azureWebClientContext, slotName);
    } catch (Exception ex) {
      log.warn("Unable to fetch Azure deploymentData", ex);
      return Collections.emptyList();
    }
  }

  private String publishInstanceSyncResult(
      PerpetualTaskId taskId, String accountId, List<ServerInstanceInfo> serverInstanceInfos) {
    AzureWebAppInstanceSyncPerpetualTaskResponse instanceSyncResponse =
        AzureWebAppInstanceSyncPerpetualTaskResponse.builder()
            .serverInstanceDetails(serverInstanceInfos)
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    try {
      execute(delegateAgentManagerClient.processInstanceSyncNGResult(taskId.getId(), accountId, instanceSyncResponse));
    } catch (Exception e) {
      String errorMsg =
          format("Failed to publish Azure Web App NG instance sync result PerpetualTaskId [%s], accountId [%s]",
              taskId.getId(), accountId);
      log.error(errorMsg + ", serverInstanceInfos: {}", serverInstanceInfos, e);
      return errorMsg;
    }
    return SUCCESS_RESPONSE_MSG;
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }
}
