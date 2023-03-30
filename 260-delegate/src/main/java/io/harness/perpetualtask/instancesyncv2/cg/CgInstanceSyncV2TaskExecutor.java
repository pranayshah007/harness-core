/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesyncv2.cg;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskExecutor;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncResponse;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncResponse.Builder;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncData;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncTrackedDeploymentDetails;
import io.harness.util.DelegateRestUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.eclipse.jetty.server.Response;

@Slf4j
@OwnedBy(CDP)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class CgInstanceSyncV2TaskExecutor implements PerpetualTaskExecutor {
  private final DelegateAgentManagerClient delegateAgentManagerClient;
  private final InstanceDetailsFetcherFactory instanceDetailsFetcherFactory;

  @SneakyThrows
  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Running the InstanceSyncV2 perpetual task executor for task id: {}", taskId);
    CgInstanceSyncTaskParams taskParams = AnyUtils.unpack(params.getCustomizedParams(), CgInstanceSyncTaskParams.class);
    InstanceSyncTrackedDeploymentDetails trackedDeploymentDetails = DelegateRestUtils.executeRestCall(
        delegateAgentManagerClient.fetchTrackedReleaseDetails(taskId.getId(), taskParams.getAccountId()));

    Builder responseBuilder =
        CgInstanceSyncResponse.newBuilder().setPerpetualTaskId(taskId.getId()).setAccountId(taskParams.getAccountId());

    if (Objects.isNull(trackedDeploymentDetails)
        || CollectionUtils.isEmpty(trackedDeploymentDetails.getDeploymentDetailsList())) {
      log.error("No deployments to track for perpetualTaskId: [{}]. Nothing to do here.", taskId.getId());
      publishInstanceSyncResult(taskParams.getAccountId(), taskId.getId(),
          responseBuilder.setExecutionStatus(CommandExecutionStatus.SKIPPED.name()).build());
      return PerpetualTaskResponse.builder()
          .responseCode(Response.SC_OK)
          .responseMessage("No tracked deployments for Instance Sync task")
          .build();
    }

    AtomicInteger batchInstanceCount = new AtomicInteger(0);
    AtomicInteger batchReleaseDetailsCount = new AtomicInteger(0);

    trackedDeploymentDetails.getDeploymentDetailsList().forEach(trackedDeployment -> {
      InstanceDetailsFetcher instanceFetcher =
          instanceDetailsFetcherFactory.getFetcher(trackedDeployment.getInfraMappingType());
      if (Objects.isNull(instanceFetcher)) {
        log.error(
            "Instance Sync Task for infraMappingId: [{}], with infra mapping type: [{}] is not supported. Doing nothing for tracked deployment Id: [{}]",
            trackedDeployment.getInfraMappingId(), trackedDeployment.getInfraMappingId(),
            trackedDeployment.getTaskDetailsId());
        responseBuilder.addInstanceData(InstanceSyncData.newBuilder()
                                            .setTaskDetailsId(trackedDeployment.getTaskDetailsId())
                                            .setExecutionStatus(CommandExecutionStatus.FAILURE.name())
                                            .setErrorMessage("Instance Sync V2 not supported for infra Mapping type: "
                                                + trackedDeployment.getInfraMappingType())
                                            .build());
        return;
      }

      InstanceSyncData instanceData = instanceFetcher.fetchRunningInstanceDetails(taskId.getId(), trackedDeployment);
      responseBuilder.addInstanceData(instanceData);
      batchInstanceCount.addAndGet(instanceData.getInstanceCount());
      batchReleaseDetailsCount.incrementAndGet();

      if (batchInstanceCount.get() > trackedDeploymentDetails.getResponseBatchConfig().getInstanceCount()
          || batchReleaseDetailsCount.get() > trackedDeploymentDetails.getResponseBatchConfig().getReleaseCount()) {
        publishInstanceSyncResult(trackedDeploymentDetails.getAccountId(), taskId.getId(),
            responseBuilder.setExecutionStatus(CommandExecutionStatus.SUCCESS.name()).build());
        responseBuilder.clearInstanceData();
        batchInstanceCount.set(0);
        batchReleaseDetailsCount.set(0);
      }
    });

    publishInstanceSyncResult(trackedDeploymentDetails.getAccountId(), taskId.getId(),
        responseBuilder.setExecutionStatus(CommandExecutionStatus.SUCCESS.name()).build());
    return PerpetualTaskResponse.builder().responseCode(Response.SC_OK).responseMessage("success").build();
  }

  private void publishInstanceSyncResult(
      String accountId, String perpetualTaskId, CgInstanceSyncResponse syncTaskResponse) {
    try {
      DelegateRestUtils.executeRestCall(
          delegateAgentManagerClient.publishInstanceSyncV2ResultV2(perpetualTaskId, accountId, syncTaskResponse));
    } catch (IOException e) {
      log.error("Exception while publishing instance sync response data for perpetual task Id: [{}], for account: [{}]",
          perpetualTaskId, accountId, e);
    }
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }
}
