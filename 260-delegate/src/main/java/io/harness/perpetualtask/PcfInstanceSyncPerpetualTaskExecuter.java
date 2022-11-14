package io.harness.perpetualtask;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.network.SafeHttpCall.execute;

import static java.lang.String.format;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.instancesync.PcfInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.task.pcf.PcfDeploymentReleaseData;
import io.harness.delegate.task.pcf.PcfTaskHelperBase;
import io.harness.delegate.task.pcf.response.PcfInfraConfig;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.PcfDeploymentRelease;
import io.harness.perpetualtask.instancesync.PcfNGInstanceSyncPerpetualTaskParams;
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
public class PcfInstanceSyncPerpetualTaskExecuter implements PerpetualTaskExecutor {
  private static final String SUCCESS_RESPONSE_MSG = "success";
  @Inject private KryoSerializer kryoSerializer;
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private PcfTaskHelperBase pcfTaskHelperBase;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Running the PCF InstanceSync perpetual task executor for task id: {}", taskId);
    PcfNGInstanceSyncPerpetualTaskParams taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), PcfNGInstanceSyncPerpetualTaskParams.class);
    return executePcfInstanceSyncTask(taskId, taskParams);
  }

  public PerpetualTaskResponse executePcfInstanceSyncTask(
      PerpetualTaskId taskId, PcfNGInstanceSyncPerpetualTaskParams taskParams) {
    List<PcfDeploymentReleaseData> deploymentReleaseDataList = getPcfDeploymentReleaseData(taskParams);

    List<ServerInstanceInfo> serverInstanceInfos = deploymentReleaseDataList.stream()
                                                       .map(this::getServerInstanceInfoList)
                                                       .flatMap(Collection::stream)
                                                       .collect(Collectors.toList());

    log.info(
        "Pcf sync nInstances: {}, task id: {}", isEmpty(serverInstanceInfos) ? 0 : serverInstanceInfos.size(), taskId);

    String instanceSyncResponseMsg = publishInstanceSyncResult(taskId, taskParams.getAccountId(), serverInstanceInfos);
    return PerpetualTaskResponse.builder().responseCode(SC_OK).responseMessage(instanceSyncResponseMsg).build();
  }

  private List<ServerInstanceInfo> getServerInstanceInfoList(PcfDeploymentReleaseData deploymentReleaseData) {
    try {
      return pcfTaskHelperBase.getPcfServerInstanceInfos(deploymentReleaseData);
    } catch (Exception ex) {
      log.warn("Unable to get list of server instances", ex);
      return Collections.emptyList();
    }
  }

  private List<PcfDeploymentReleaseData> getPcfDeploymentReleaseData(PcfNGInstanceSyncPerpetualTaskParams taskParams) {
    return taskParams.getPcfDeploymentReleaseListList()
        .stream()
        .map(this::toPcfDeploymentReleaseData)
        .collect(Collectors.toList());
  }

  private PcfDeploymentReleaseData toPcfDeploymentReleaseData(PcfDeploymentRelease pcfDeploymentRelease) {
    return PcfDeploymentReleaseData.builder()
        .applicationName(pcfDeploymentRelease.getApplicationName())
        .pcfInfraConfig(
            (PcfInfraConfig) kryoSerializer.asObject(pcfDeploymentRelease.getPcfInfraConfig().toByteArray()))
        .build();
  }

  private String publishInstanceSyncResult(
      PerpetualTaskId taskId, String accountId, List<ServerInstanceInfo> serverInstanceInfos) {
    PcfInstanceSyncPerpetualTaskResponse instanceSyncResponse =
        PcfInstanceSyncPerpetualTaskResponse.builder()
            .serverInstanceDetails(serverInstanceInfos)
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();
    try {
      execute(delegateAgentManagerClient.processInstanceSyncNGResult(taskId.getId(), accountId, instanceSyncResponse));
    } catch (Exception e) {
      String errorMsg = format(
          "Failed to publish PCF instance sync result PerpetualTaskId [%s], accountId [%s]", taskId.getId(), accountId);
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
