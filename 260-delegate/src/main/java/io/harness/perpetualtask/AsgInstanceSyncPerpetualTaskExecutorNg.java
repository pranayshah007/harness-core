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
import io.harness.delegate.beans.instancesync.AsgInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.task.aws.asg.AsgDeploymentReleaseData;
import io.harness.delegate.task.aws.asg.AsgInfraConfig;
import io.harness.delegate.task.aws.asg.AsgTaskHelper;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.AsgDeploymentRelease;
import io.harness.perpetualtask.instancesync.AsgInstanceSyncPerpetualTaskParamsNg;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AsgInstanceSyncPerpetualTaskExecutorNg implements PerpetualTaskExecutor {
  private static final String SUCCESS_RESPONSE_MSG = "success";
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private AsgTaskHelper asgTaskHelper;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Running the Asg InstanceSync perpetual task executor for task id: {}", taskId);
    AsgInstanceSyncPerpetualTaskParamsNg taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), AsgInstanceSyncPerpetualTaskParamsNg.class);
    return executeAsgInstanceSyncTask(taskId, taskParams);
  }

  public PerpetualTaskResponse executeAsgInstanceSyncTask(
      PerpetualTaskId taskId, AsgInstanceSyncPerpetualTaskParamsNg taskParams) {
    List<AsgDeploymentReleaseData> deploymentReleaseDataList = getAsgDeploymentReleaseData(taskParams);

    List<ServerInstanceInfo> serverInstanceInfos = deploymentReleaseDataList.stream()
                                                       .map(this::getServerInstanceInfoList)
                                                       .flatMap(Collection::stream)
                                                       .collect(Collectors.toList());

    log.info(
        "Asg sync nInstances: {}, task id: {}", isEmpty(serverInstanceInfos) ? 0 : serverInstanceInfos.size(), taskId);

    String instanceSyncResponseMsg = publishInstanceSyncResult(taskId, taskParams.getAccountId(), serverInstanceInfos);
    return PerpetualTaskResponse.builder().responseCode(SC_OK).responseMessage(instanceSyncResponseMsg).build();
  }

  private List<ServerInstanceInfo> getServerInstanceInfoList(AsgDeploymentReleaseData deploymentReleaseData) {
    try {
      return asgTaskHelper.getAsgServerInstanceInfos(deploymentReleaseData);
    } catch (Exception ex) {
      log.warn("Unable to get list of server instances", ex);
      return Collections.emptyList();
    }
  }

  private List<AsgDeploymentReleaseData> getAsgDeploymentReleaseData(AsgInstanceSyncPerpetualTaskParamsNg taskParams) {
    return taskParams.getAsgDeploymentReleaseListList()
        .stream()
        .map(this::toAsgDeploymentReleaseData)
        .collect(Collectors.toList());
  }

  private AsgDeploymentReleaseData toAsgDeploymentReleaseData(AsgDeploymentRelease asgDeploymentRelease) {
    return AsgDeploymentReleaseData.builder()
        .asgInfraConfig((AsgInfraConfig) referenceFalseKryoSerializer.asObject(
            asgDeploymentRelease.getAsgInfraConfig().toByteArray()))
        .asgNameWithoutSuffix(asgDeploymentRelease.getAsgNameWithoutSuffix())
        .executionStrategy(asgDeploymentRelease.getExecutionStrategy())
        .build();
  }

  private String publishInstanceSyncResult(
      PerpetualTaskId taskId, String accountId, List<ServerInstanceInfo> serverInstanceInfos) {
    AsgInstanceSyncPerpetualTaskResponse instanceSyncResponse =
        AsgInstanceSyncPerpetualTaskResponse.builder()
            .serverInstanceDetails(serverInstanceInfos)
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    try {
      execute(delegateAgentManagerClient.processInstanceSyncNGResult(taskId.getId(), accountId, instanceSyncResponse));
    } catch (Exception e) {
      String errorMsg = format(
          "Failed to publish Asg instance sync result PerpetualTaskId [%s], accountId [%s]", taskId.getId(), accountId);
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
