/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
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
import io.harness.delegate.beans.instancesync.TasInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.task.pcf.TasDeploymentReleaseData;
import io.harness.delegate.task.pcf.TasTaskHelperBase;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.TasDeploymentRelease;
import io.harness.perpetualtask.instancesync.TasInstanceSyncPerpetualTaskParams;

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
public class TasInstanceSyncPerpetualTaskExecuter extends PerpetualTaskExecutorBase implements PerpetualTaskExecutor {
  private static final String SUCCESS_RESPONSE_MSG = "success";
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private TasTaskHelperBase tasTaskHelperBase;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Running the TAS InstanceSync perpetual task executor for task id: {}", taskId);
    TasInstanceSyncPerpetualTaskParams taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), TasInstanceSyncPerpetualTaskParams.class);
    return executeTasInstanceSyncTask(taskId, taskParams, params.getReferenceFalseKryoSerializer());
  }

  public PerpetualTaskResponse executeTasInstanceSyncTask(
      PerpetualTaskId taskId, TasInstanceSyncPerpetualTaskParams taskParams, boolean referenceFalseKryoSerializer) {
    List<TasDeploymentReleaseData> deploymentReleaseDataList =
        getTasDeploymentReleaseData(taskParams, referenceFalseKryoSerializer);

    List<ServerInstanceInfo> serverInstanceInfos = deploymentReleaseDataList.stream()
                                                       .map(this::getServerInstanceInfoList)
                                                       .flatMap(Collection::stream)
                                                       .collect(Collectors.toList());

    log.info(
        "TAS sync nInstances: {}, task id: {}", isEmpty(serverInstanceInfos) ? 0 : serverInstanceInfos.size(), taskId);

    String instanceSyncResponseMsg = publishInstanceSyncResult(taskId, taskParams.getAccountId(), serverInstanceInfos);
    return PerpetualTaskResponse.builder().responseCode(SC_OK).responseMessage(instanceSyncResponseMsg).build();
  }

  private List<ServerInstanceInfo> getServerInstanceInfoList(TasDeploymentReleaseData deploymentReleaseData) {
    try {
      return tasTaskHelperBase.getTasServerInstanceInfos(deploymentReleaseData);
    } catch (Exception ex) {
      log.warn("Unable to get list of server instances", ex);
      return Collections.emptyList();
    }
  }

  private List<TasDeploymentReleaseData> getTasDeploymentReleaseData(
      TasInstanceSyncPerpetualTaskParams taskParams, boolean referenceFalseKryoSerializer) {
    return taskParams.getTasDeploymentReleaseListList()
        .stream()
        .map(data -> toTasDeploymentReleaseData(data, referenceFalseKryoSerializer))
        .collect(Collectors.toList());
  }

  private TasDeploymentReleaseData toTasDeploymentReleaseData(
      TasDeploymentRelease tasDeploymentRelease, boolean referenceFalseKryoSerializer) {
    return TasDeploymentReleaseData.builder()
        .applicationName(tasDeploymentRelease.getApplicationName())
        .tasInfraConfig((TasInfraConfig) getKryoSerializer(referenceFalseKryoSerializer)
                            .asObject(tasDeploymentRelease.getTasInfraConfig().toByteArray()))
        .build();
  }

  private String publishInstanceSyncResult(
      PerpetualTaskId taskId, String accountId, List<ServerInstanceInfo> serverInstanceInfos) {
    TasInstanceSyncPerpetualTaskResponse instanceSyncResponse =
        TasInstanceSyncPerpetualTaskResponse.builder()
            .serverInstanceDetails(serverInstanceInfos)
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    try {
      execute(delegateAgentManagerClient.processInstanceSyncNGResult(taskId.getId(), accountId, instanceSyncResponse));
    } catch (Exception e) {
      String errorMsg = format(
          "Failed to publish TAS instance sync result PerpetualTaskId [%s], accountId [%s]", taskId.getId(), accountId);
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
