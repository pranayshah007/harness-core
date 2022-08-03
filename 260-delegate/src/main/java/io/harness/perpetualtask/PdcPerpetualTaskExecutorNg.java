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
import io.harness.delegate.beans.instancesync.InstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.PdcInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.PdcServerInstanceInfo;
import io.harness.exception.InvalidArgumentsException;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.perpetualtask.instancesync.PdcPerpetualTaskParamsNg;

import software.wings.beans.HostReachabilityInfo;
import software.wings.utils.HostValidationService;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class PdcPerpetualTaskExecutorNg implements PerpetualTaskExecutor {
  private static final String SUCCESS_RESPONSE_MSG = "success";

  private static final Set<String> VALID_SERVICE_TYPES =
      Collections.unmodifiableSet(new HashSet(Arrays.asList(ServiceSpecType.SSH, ServiceSpecType.WINRM)));

  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private HostValidationService hostValidationService;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Running the Pdc InstanceSync perpetual task executor for task id: {}", taskId);
    PdcPerpetualTaskParamsNg taskParams = AnyUtils.unpack(params.getCustomizedParams(), PdcPerpetualTaskParamsNg.class);

    if (!VALID_SERVICE_TYPES.contains(taskParams.getServiceType())) {
      throw new InvalidArgumentsException(
          format("Invalid serviceType provided %s. Expected: %s", taskParams.getServiceType(), VALID_SERVICE_TYPES));
    }

    return executeTask(taskId, taskParams);
  }

  private PerpetualTaskResponse executeTask(PerpetualTaskId taskId, PdcPerpetualTaskParamsNg taskParams) {
    List<ServerInstanceInfo> serverInstanceInfos = getServerInstanceInfoList(taskParams.getHostsList(),
        taskParams.getPort(), taskParams.getServiceType(), taskParams.getInfrastructureKey());

    log.info("Pdc Instance sync nInstances: {}, task id: {}", serverInstanceInfos.size(), taskId);

    String instanceSyncResponseMsg =
        publishInstanceSyncResult(taskId, taskParams.getAccountId(), serverInstanceInfos, taskParams.getServiceType());
    return PerpetualTaskResponse.builder().responseCode(SC_OK).responseMessage(instanceSyncResponseMsg).build();
  }

  private List<ServerInstanceInfo> getServerInstanceInfoList(
      List<String> hosts, int port, String serviceType, String infrastructureKey) {
    try {
      List<HostReachabilityInfo> hostReachabilityInfos = hostValidationService.validateReachability(hosts, port);
      return hostReachabilityInfos.stream()
          .filter(hr -> Boolean.TRUE.equals(hr.getReachable()))
          .map(o -> mapToPdcServerInstanceInfo(serviceType, o, infrastructureKey))
          .collect(Collectors.toList());
    } catch (Exception e) {
      log.warn("Unable to get list of server instances, hosts: {}, port: {}", hosts, port, e);
      return Collections.emptyList();
    }
  }

  private String publishInstanceSyncResult(
      PerpetualTaskId taskId, String accountId, List<ServerInstanceInfo> serverInstanceInfos, String serviceType) {
    InstanceSyncPerpetualTaskResponse instanceSyncResponse = PdcInstanceSyncPerpetualTaskResponse.builder()
                                                                 .serviceType(serviceType)
                                                                 .serverInstanceDetails(serverInstanceInfos)
                                                                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                                 .build();

    try {
      execute(delegateAgentManagerClient.processInstanceSyncNGResult(taskId.getId(), accountId, instanceSyncResponse));
    } catch (Exception e) {
      String errorMsg = format(
          "Failed to publish Pdc instance sync result PerpetualTaskId [%s], accountId [%s]", taskId.getId(), accountId);
      log.error(errorMsg + ", serverInstanceInfos: {}", serverInstanceInfos, e);
      return errorMsg;
    }
    return SUCCESS_RESPONSE_MSG;
  }

  private ServerInstanceInfo mapToPdcServerInstanceInfo(
      String serviceType, HostReachabilityInfo hostReachabilityInfo, String infrastructureKey) {
    return PdcServerInstanceInfo.builder()
        .serviceType(serviceType)
        .host(hostReachabilityInfo.getHostName())
        .infrastructureKey(infrastructureKey)
        .build();
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }
}
