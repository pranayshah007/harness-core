/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.network.SafeHttpCall.execute;

import static java.lang.String.format;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.ServerlessAwsLambdaInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaDeploymentReleaseData;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaInfraConfig;
import io.harness.delegate.task.serverless.ServerlessTaskHelperBase;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.ServerlessAwsLambdaDeploymentRelease;
import io.harness.perpetualtask.instancesync.ServerlessAwsLambdaInstanceSyncPerpetualTaskParams;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(HarnessTeam.CDP)
public class ServerlessAwsLambdaInstanceSyncPerpetualTaskExecutor
    extends PerpetualTaskExecutorBase implements PerpetualTaskExecutor {
  private static final String SUCCESS_RESPONSE_MSG = "success";
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private ServerlessTaskHelperBase serverlessTaskHelperBase;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Running the ServerlessAwsLambda InstanceSync perpetual task executor for task id: {}", taskId);
    ServerlessAwsLambdaInstanceSyncPerpetualTaskParams taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), ServerlessAwsLambdaInstanceSyncPerpetualTaskParams.class);
    return executeServerlessAwsLambdaInstanceSyncTask(taskId, taskParams, params.getReferenceFalseKryoSerializer());
  }

  private PerpetualTaskResponse executeServerlessAwsLambdaInstanceSyncTask(PerpetualTaskId taskId,
      ServerlessAwsLambdaInstanceSyncPerpetualTaskParams taskParams, boolean referenceFalseKryoSerializer) {
    List<ServerlessAwsLambdaDeploymentReleaseData> deploymentReleaseDataList =
        getServerlessAwsLambdaDeploymentReleaseData(taskParams, referenceFalseKryoSerializer);

    List<ServerInstanceInfo> serverInstanceInfos =
        deploymentReleaseDataList.stream()
            .map(deploymentReleaseData -> getServerInstanceInfoList(deploymentReleaseData))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    log.info("Serverless Lambda Instance sync nInstances: {}, task id: {}",
        isEmpty(serverInstanceInfos) ? 0 : serverInstanceInfos.size(), taskId);

    String instanceSyncResponseMsg = publishInstanceSyncResult(taskId, taskParams.getAccountId(), serverInstanceInfos);
    return PerpetualTaskResponse.builder().responseCode(SC_OK).responseMessage(instanceSyncResponseMsg).build();
  }

  private List<ServerInstanceInfo> getServerInstanceInfoList(
      ServerlessAwsLambdaDeploymentReleaseData deploymentReleaseData) {
    try {
      return serverlessTaskHelperBase.getServerlessAwsLambdaServerInstanceInfos(deploymentReleaseData);
    } catch (Exception ex) {
      log.warn("Unable to get list of server instances", ex);
      return Collections.emptyList();
    }
  }

  private List<ServerlessAwsLambdaDeploymentReleaseData> getServerlessAwsLambdaDeploymentReleaseData(
      ServerlessAwsLambdaInstanceSyncPerpetualTaskParams taskParams, boolean referenceFalseKryoSerializer) {
    return taskParams.getServerlessAwsLambdaDeploymentReleaseListList()
        .stream()
        .map(data -> toServerlessAwsLambdaDeploymentReleaseData(data, referenceFalseKryoSerializer))
        .collect(Collectors.toList());
  }

  private ServerlessAwsLambdaDeploymentReleaseData toServerlessAwsLambdaDeploymentReleaseData(
      ServerlessAwsLambdaDeploymentRelease serverlessAwsLambdaDeploymentRelease, boolean referenceFalseKryoSerializer) {
    return ServerlessAwsLambdaDeploymentReleaseData.builder()
        .serverlessInfraConfig(
            (ServerlessAwsLambdaInfraConfig) getKryoSerializer(referenceFalseKryoSerializer)
                .asObject(serverlessAwsLambdaDeploymentRelease.getServerlessInfraConfig().toByteArray()))
        .functions(serverlessAwsLambdaDeploymentRelease.getFunctionsList())
        .region(serverlessAwsLambdaDeploymentRelease.getRegion())
        .serviceName(serverlessAwsLambdaDeploymentRelease.getServiceName())
        .build();
  }

  private String publishInstanceSyncResult(
      PerpetualTaskId taskId, String accountId, List<ServerInstanceInfo> serverInstanceInfos) {
    ServerlessAwsLambdaInstanceSyncPerpetualTaskResponse instanceSyncResponse =
        ServerlessAwsLambdaInstanceSyncPerpetualTaskResponse.builder()
            .serverInstanceDetails(serverInstanceInfos)
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    try {
      execute(delegateAgentManagerClient.processInstanceSyncNGResult(taskId.getId(), accountId, instanceSyncResponse));
    } catch (Exception e) {
      String errorMsg =
          format("Failed to publish Serverless Lambda instance sync result PerpetualTaskId [%s], accountId [%s]",
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
