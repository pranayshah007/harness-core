/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.network.SafeHttpCall.execute;

import static java.lang.String.format;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.instancesync.InstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.SshWinrmInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.info.AwsSshWinrmServerInstanceInfo;
import io.harness.delegate.task.aws.AwsASGDelegateTaskHelper;
import io.harness.delegate.task.aws.AwsListEC2InstancesDelegateTaskHelper;
import io.harness.delegate.task.ssh.AwsInfraDelegateConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.perpetualtask.instancesync.AwsSshInstanceSyncPerpetualTaskParamsNg;
import io.harness.yaml.infra.HostConnectionTypeKind;

import software.wings.service.impl.aws.model.AwsEC2Instance;

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
public class AwsSshWinrmPerpetualTaskExecutorNg extends PerpetualTaskExecutorBase implements PerpetualTaskExecutor {
  private static final Set<String> VALID_SERVICE_TYPES = ImmutableSet.of(ServiceSpecType.SSH, ServiceSpecType.WINRM);

  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private AwsListEC2InstancesDelegateTaskHelper awsListEC2InstancesDelegateTaskHelper;
  @Inject private AwsASGDelegateTaskHelper awsASGDelegateTaskHelper;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Running the Aws InstanceSync perpetual task executor for task id: {}", taskId);
    AwsSshInstanceSyncPerpetualTaskParamsNg taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), AwsSshInstanceSyncPerpetualTaskParamsNg.class);

    if (!VALID_SERVICE_TYPES.contains(taskParams.getServiceType())) {
      throw new InvalidArgumentsException(
          format("Invalid serviceType provided %s. Expected: %s", taskParams.getServiceType(), VALID_SERVICE_TYPES));
    }

    return executeTask(taskId, taskParams, params.getReferenceFalseKryoSerializer());
  }

  private PerpetualTaskResponse executeTask(
      PerpetualTaskId taskId, AwsSshInstanceSyncPerpetualTaskParamsNg taskParams, boolean referenceFalseSerializer) {
    List<AwsEC2Instance> awsEC2Instances = getAwsEC2Instance(taskParams, referenceFalseSerializer);
    Set<String> awsHosts = awsEC2Instances.stream()
                               .map(instance -> mapToAddress(instance, taskParams.getHostConnectionType()))
                               .collect(Collectors.toSet());
    Set<String> instanceHosts =
        taskParams.getHostsList().stream().filter(awsHosts::contains).collect(Collectors.toSet());

    List<ServerInstanceInfo> serverInstanceInfos =
        getServerInstanceInfoList(instanceHosts, taskParams.getServiceType(), taskParams.getInfrastructureKey());

    log.info("Aws Instance sync Instances: {}, task id: {}",
        serverInstanceInfos == null ? 0 : serverInstanceInfos.size(), taskId);

    String instanceSyncResponseMsg =
        publishInstanceSyncResult(taskId, taskParams.getAccountId(), serverInstanceInfos, taskParams.getServiceType());
    return PerpetualTaskResponse.builder().responseCode(SC_OK).responseMessage(instanceSyncResponseMsg).build();
  }

  private String mapToAddress(AwsEC2Instance awsEC2Instance, String hostConnectionType) {
    if (EmptyPredicate.isEmpty(hostConnectionType)) {
      return awsEC2Instance.getHostname(); // default
    }

    if (HostConnectionTypeKind.PUBLIC_IP.equals(hostConnectionType)
        && EmptyPredicate.isNotEmpty(awsEC2Instance.getPublicIp())) {
      return awsEC2Instance.getPublicIp();
    } else if (HostConnectionTypeKind.PRIVATE_IP.equals(hostConnectionType)
        && EmptyPredicate.isNotEmpty(awsEC2Instance.getPrivateIp())) {
      return awsEC2Instance.getPrivateIp();
    } else {
      return awsEC2Instance.getHostname(); // default
    }
  }

  private List<AwsEC2Instance> getAwsEC2Instance(
      AwsSshInstanceSyncPerpetualTaskParamsNg taskParams, boolean referenceFalseSerializer) {
    AwsInfraDelegateConfig infraConfig = (AwsInfraDelegateConfig) getKryoSerializer(referenceFalseSerializer)
                                             .asObject(taskParams.getInfraDelegateConfig().toByteArray());

    if (EmptyPredicate.isNotEmpty(infraConfig.getAutoScalingGroupName())) { // ASG
      return awsASGDelegateTaskHelper.getInstances(infraConfig.getAwsConnectorDTO(),
          infraConfig.getConnectorEncryptionDataDetails(), infraConfig.getRegion(),
          infraConfig.getAutoScalingGroupName());
    } else {
      boolean isWinRm = ServiceSpecType.WINRM.equals(taskParams.getServiceType());
      return awsListEC2InstancesDelegateTaskHelper.getInstances(infraConfig.getAwsConnectorDTO(),
          infraConfig.getConnectorEncryptionDataDetails(), infraConfig.getRegion(), infraConfig.getVpcIds(),
          infraConfig.getTags(), isWinRm);
    }
  }

  private List<ServerInstanceInfo> getServerInstanceInfoList(
      Set<String> hosts, String serviceType, String infrastructureKey) {
    return hosts.stream()
        .map(host -> mapToAwsServerInstanceInfo(serviceType, host, infrastructureKey))
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
      String errorMsg = format(
          "Failed to publish Aws instance sync result PerpetualTaskId [%s], accountId [%s]", taskId.getId(), accountId);
      log.error(errorMsg + ", serverInstanceInfos: {}", serverInstanceInfos, e);
      return errorMsg;
    }
    return SUCCESS_RESPONSE_MSG;
  }

  private ServerInstanceInfo mapToAwsServerInstanceInfo(String serviceType, String host, String infrastructureKey) {
    return AwsSshWinrmServerInstanceInfo.builder()
        .serviceType(serviceType)
        .infrastructureKey(infrastructureKey)
        .host(host)
        .build();
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }
}
