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
import io.harness.delegate.beans.instancesync.GoogleFunctionInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.task.googlefunction.GoogleFunctionDeploymentReleaseData;
import io.harness.delegate.task.googlefunction.GoogleFunctionTaskHelperBase;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionInfraConfig;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.GoogleFunctionDeploymentRelease;
import io.harness.perpetualtask.instancesync.GoogleFunctionInstanceSyncPerpetualTaskParams;
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
@OwnedBy(HarnessTeam.CDP)
public class GoogleFunctionInstanceSyncPerpetualTaskExecutor implements PerpetualTaskExecutor {
  private static final String SUCCESS_RESPONSE_MSG = "success";
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private GoogleFunctionTaskHelperBase googleFunctionTaskHelperBase;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Running the Google Cloud Function InstanceSync perpetual task executor for task id: {}", taskId);
    GoogleFunctionInstanceSyncPerpetualTaskParams taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), GoogleFunctionInstanceSyncPerpetualTaskParams.class);
    return executeGoogleFunctionInstanceSyncTask(taskId, taskParams);
  }

  public PerpetualTaskResponse executeGoogleFunctionInstanceSyncTask(
      PerpetualTaskId taskId, GoogleFunctionInstanceSyncPerpetualTaskParams taskParams) {
    List<GoogleFunctionDeploymentReleaseData> deploymentReleaseDataList =
        getGoogleFunctionDeploymentReleaseData(taskParams);

    List<ServerInstanceInfo> serverInstanceInfos = deploymentReleaseDataList.stream()
                                                       .map(this::getServerInstanceInfoList)
                                                       .flatMap(Collection::stream)
                                                       .collect(Collectors.toList());

    log.info("Google Cloud Function sync nInstances: {}, task id: {}",
        isEmpty(serverInstanceInfos) ? 0 : serverInstanceInfos.size(), taskId);

    String instanceSyncResponseMsg = publishInstanceSyncResult(taskId, taskParams.getAccountId(), serverInstanceInfos);
    return PerpetualTaskResponse.builder().responseCode(SC_OK).responseMessage(instanceSyncResponseMsg).build();
  }

  private List<ServerInstanceInfo> getServerInstanceInfoList(
      GoogleFunctionDeploymentReleaseData deploymentReleaseData) {
    try {
      return googleFunctionTaskHelperBase.getGoogleFunctionServerInstanceInfo(deploymentReleaseData);
    } catch (Exception ex) {
      log.warn("Unable to get google cloud function server instance", ex);
      return Collections.emptyList();
    }
  }

  private List<GoogleFunctionDeploymentReleaseData> getGoogleFunctionDeploymentReleaseData(
      GoogleFunctionInstanceSyncPerpetualTaskParams taskParams) {
    return taskParams.getGoogleFunctionsDeploymentReleaseListList()
        .stream()
        .map(this::toGoogleFunctionDeploymentReleaseData)
        .collect(Collectors.toList());
  }

  private GoogleFunctionDeploymentReleaseData toGoogleFunctionDeploymentReleaseData(
      GoogleFunctionDeploymentRelease googleFunctionDeploymentRelease) {
    return GoogleFunctionDeploymentReleaseData.builder()
        .googleFunctionInfraConfig((GoogleFunctionInfraConfig) referenceFalseKryoSerializer.asObject(
            googleFunctionDeploymentRelease.getGoogleFunctionsInfraConfig().toByteArray()))
        .function(googleFunctionDeploymentRelease.getFunction())
        .region(googleFunctionDeploymentRelease.getRegion())
        .build();
  }

  private String publishInstanceSyncResult(
      PerpetualTaskId taskId, String accountId, List<ServerInstanceInfo> serverInstanceInfos) {
    GoogleFunctionInstanceSyncPerpetualTaskResponse instanceSyncResponse =
        GoogleFunctionInstanceSyncPerpetualTaskResponse.builder()
            .serverInstanceDetails(serverInstanceInfos)
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    try {
      execute(delegateAgentManagerClient.processInstanceSyncNGResult(taskId.getId(), accountId, instanceSyncResponse));
    } catch (Exception e) {
      String errorMsg =
          format("Failed to publish Google Cloud Functions instance sync result PerpetualTaskId [%s], accountId [%s]",
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
