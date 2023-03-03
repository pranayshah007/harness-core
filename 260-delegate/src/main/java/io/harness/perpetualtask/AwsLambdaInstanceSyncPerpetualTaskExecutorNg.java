/*
 * Copyright 2023 Harness Inc. All rights reserved.
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
import io.harness.delegate.beans.instancesync.AwsLambdaInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.task.aws.lambda.AwsLambdaDeploymentReleaseData;
import io.harness.delegate.task.aws.lambda.AwsLambdaFunctionsInfraConfig;
import io.harness.delegate.task.aws.lambda.AwsLambdaTaskHelperBase;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.AwsLambdaDeploymentRelease;
import io.harness.perpetualtask.instancesync.AwsLambdaInstanceSyncPerpetualTaskParamsNg;
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
@OwnedBy(HarnessTeam.CDP)
public class AwsLambdaInstanceSyncPerpetualTaskExecutorNg implements PerpetualTaskExecutor {
  private static final String SUCCESS_RESPONSE_MSG = "success";
  @Inject private KryoSerializer kryoSerializer;
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private AwsLambdaTaskHelperBase awsLambdaTaskHelperBase;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Running the Aws Lambda InstanceSync perpetual task executor for task id: {}", taskId);
    AwsLambdaInstanceSyncPerpetualTaskParamsNg taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), AwsLambdaInstanceSyncPerpetualTaskParamsNg.class);
    return executeAwsLambdaInstanceSyncTask(taskId, taskParams);
  }

  public PerpetualTaskResponse executeAwsLambdaInstanceSyncTask(
      PerpetualTaskId taskId, AwsLambdaInstanceSyncPerpetualTaskParamsNg taskParams) {
    List<AwsLambdaDeploymentReleaseData> deploymentReleaseDataList = getAwsLambdaDeploymentReleaseData(taskParams);

    List<ServerInstanceInfo> serverInstanceInfos = deploymentReleaseDataList.stream()
                                                       .map(this::getServerInstanceInfoList)
                                                       .flatMap(Collection::stream)
                                                       .collect(Collectors.toList());

    log.info("Aws Lambda Function sync nInstances: {}, task id: {}",
        isEmpty(serverInstanceInfos) ? 0 : serverInstanceInfos.size(), taskId);

    String instanceSyncResponseMsg = publishInstanceSyncResult(taskId, taskParams.getAccountId(), serverInstanceInfos);
    return PerpetualTaskResponse.builder().responseCode(SC_OK).responseMessage(instanceSyncResponseMsg).build();
  }

  private List<ServerInstanceInfo> getServerInstanceInfoList(AwsLambdaDeploymentReleaseData deploymentReleaseData) {
    try {
      return awsLambdaTaskHelperBase.getAwsLambdaServerInstanceInfo(deploymentReleaseData);
    } catch (Exception ex) {
      log.warn("Unable to get aws lambda function server instance", ex);
      return Collections.emptyList();
    }
  }

  private List<AwsLambdaDeploymentReleaseData> getAwsLambdaDeploymentReleaseData(
      AwsLambdaInstanceSyncPerpetualTaskParamsNg taskParams) {
    return taskParams.getAwsLambdaDeploymentReleaseListList()
        .stream()
        .map(this::toAwsLambdaDeploymentReleaseData)
        .collect(Collectors.toList());
  }

  private AwsLambdaDeploymentReleaseData toAwsLambdaDeploymentReleaseData(
      AwsLambdaDeploymentRelease awsLambdaDeploymentRelease) {
    return AwsLambdaDeploymentReleaseData.builder()
        .awsLambdaInfraConfig((AwsLambdaFunctionsInfraConfig) kryoSerializer.asObject(
            awsLambdaDeploymentRelease.getAwsLambdaInfraConfig().toByteArray()))
        .function(awsLambdaDeploymentRelease.getFunction())
        .region(awsLambdaDeploymentRelease.getRegion())
        .build();
  }

  private String publishInstanceSyncResult(
      PerpetualTaskId taskId, String accountId, List<ServerInstanceInfo> serverInstanceInfos) {
    AwsLambdaInstanceSyncPerpetualTaskResponse instanceSyncResponse =
        AwsLambdaInstanceSyncPerpetualTaskResponse.builder()
            .serverInstanceDetails(serverInstanceInfos)
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    try {
      execute(delegateAgentManagerClient.processInstanceSyncNGResult(taskId.getId(), accountId, instanceSyncResponse));
    } catch (Exception e) {
      String errorMsg =
          format("Failed to publish Aws Lambda Functions instance sync result PerpetualTaskId [%s], accountId [%s]",
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
