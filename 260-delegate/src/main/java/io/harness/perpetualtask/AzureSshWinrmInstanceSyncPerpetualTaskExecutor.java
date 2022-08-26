/*
 * Copyright 2021 Harness Inc. All rights reserved.
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
import io.harness.azure.model.AzureOSType;
import io.harness.delegate.beans.azure.response.AzureHostResponse;
import io.harness.delegate.beans.azure.response.AzureHostsResponse;
import io.harness.delegate.beans.instancesync.InstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.SshWinrmInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.info.AzureSshWinrmServerInstanceInfo;
import io.harness.delegate.task.ssh.AzureInfraDelegateConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.perpetualtask.instancesync.AzureSshInstanceSyncPerpetualTaskParamsNg;
import io.harness.serializer.KryoSerializer;

import software.wings.delegatetasks.azure.AzureAsyncTaskHelper;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AzureSshWinrmInstanceSyncPerpetualTaskExecutor implements PerpetualTaskExecutor {
  private static final Set<String> VALID_SERVICE_TYPES = ImmutableSet.of(ServiceSpecType.SSH, ServiceSpecType.WINRM);

  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private AzureAsyncTaskHelper azureAsyncTaskHelper;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Running the Azure InstanceSync perpetual task executor for task id: {}", taskId);
    AzureSshInstanceSyncPerpetualTaskParamsNg taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), AzureSshInstanceSyncPerpetualTaskParamsNg.class);

    if (!VALID_SERVICE_TYPES.contains(taskParams.getServiceType())) {
      throw new InvalidArgumentsException(
          format("Invalid serviceType provided %s . Expected: %s", taskParams.getServiceType(), VALID_SERVICE_TYPES));
    }

    return executeTask(taskId, taskParams);
  }

  private PerpetualTaskResponse executeTask(
      PerpetualTaskId taskId, AzureSshInstanceSyncPerpetualTaskParamsNg taskParams) {
    Set<String> azureHosts = getAzureHosts(taskParams);
    List<String> instanceHosts =
        taskParams.getHostsList().stream().filter(azureHosts::contains).collect(Collectors.toList());

    List<ServerInstanceInfo> serverInstanceInfos =
        getServerInstanceInfoList(instanceHosts, taskParams.getServiceType(), taskParams.getInfrastructureKey());

    log.info("Azure Instance sync Instances: {}, task id: {}",
        isEmpty(serverInstanceInfos) ? 0 : serverInstanceInfos.size(), taskId);

    String instanceSyncResponseMsg =
        publishInstanceSyncResult(taskId, taskParams.getAccountId(), serverInstanceInfos, taskParams.getServiceType());
    return PerpetualTaskResponse.builder().responseCode(SC_OK).responseMessage(instanceSyncResponseMsg).build();
  }

  private Set<String> getAzureHosts(AzureSshInstanceSyncPerpetualTaskParamsNg taskParams) {
    AzureInfraDelegateConfig infraConfig = (AzureInfraDelegateConfig) kryoSerializer.asObject(
        taskParams.getAzureSshWinrmInfraDelegateConfig().toByteArray());

    AzureOSType azureOSType =
        ServiceSpecType.SSH.equals(taskParams.getServiceType()) ? AzureOSType.LINUX : AzureOSType.WINDOWS;

    AzureHostsResponse azureHostsResponse = azureAsyncTaskHelper.listHosts(
        infraConfig.getConnectorEncryptionDataDetails(), infraConfig.getAzureConnectorDTO(),
        infraConfig.getSubscriptionId(), infraConfig.getResourceGroup(), azureOSType, infraConfig.getTags());
    return azureHostsResponse.getHosts().stream().map(AzureHostResponse::getHostName).collect(Collectors.toSet());
  }

  private List<ServerInstanceInfo> getServerInstanceInfoList(
      List<String> hosts, String serviceType, String infrastructureKey) {
    return hosts.stream()
        .map(host -> mapToAzureServerInstanceInfo(serviceType, host, infrastructureKey))
        .collect(Collectors.toList());
  }

  private String publishInstanceSyncResult(
      PerpetualTaskId taskId, String accountId, List<ServerInstanceInfo> serverInstanceInfos, String serviceType) {
    InstanceSyncPerpetualTaskResponse instanceSyncResponse = SshWinrmInstanceSyncPerpetualTaskResponse.builder()
                                                                 .serviceType(serviceType)
                                                                 .serverInstanceDetails(serverInstanceInfos)
                                                                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                                 .build();

    try {
      execute(delegateAgentManagerClient.processInstanceSyncNGResult(taskId.getId(), accountId, instanceSyncResponse));
    } catch (Exception e) {
      String errorMsg = format("Failed to publish Azure instance sync result PerpetualTaskId [%s], accountId [%s]",
          taskId.getId(), accountId);
      log.error(errorMsg + ", serverInstanceInfos: {}", serverInstanceInfos, e);
      return errorMsg;
    }
    return SUCCESS_RESPONSE_MSG;
  }

  private ServerInstanceInfo mapToAzureServerInstanceInfo(String serviceType, String host, String infrastructureKey) {
    return AzureSshWinrmServerInstanceInfo.builder()
        .serviceType(serviceType)
        .host(host)
        .infrastructureKey(infrastructureKey)
        .build();
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }
}
