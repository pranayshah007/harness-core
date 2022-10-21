/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesyncv2.cg;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.network.SafeHttpCall.execute;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.utils.AnyUtils;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskExecutor;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;

import software.wings.beans.infrastructure.instance.info.InstanceInfo;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class CgInstanceSyncV2TaskExecutor implements PerpetualTaskExecutor {
  @Inject private InstanceDetailsFetcher instanceDetailsFetcher;
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;

  private static final int INSTANCE_COUNT_LIMIT = 150;

  private static final int RELEASE_COUNT_LIMIT = 15;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Came here. Add more details for task executor");
    CgInstanceSyncTaskParams taskParams = AnyUtils.unpack(params.getCustomizedParams(), CgInstanceSyncTaskParams.class);
    String cloudProviderType = taskParams.getCloudProviderType();

    List<InstanceInfo> responseData = null;

    switch (cloudProviderType) {
      case "KUBERNETES":

        break;
      default:
        throw new InvalidRequestException(
            format("Cloud Provider of given type : %s isn't supported", cloudProviderType));
    }

    return null;
    boolean isFailureResponse = FAILURE == responseData.getCommandExecutionStatus();
    return PerpetualTaskResponse.builder()
        .responseCode(Response.SC_OK)
        .responseMessage(isFailureResponse ? responseData.getErrorMessage() : "success")
        .build();
  }

  private void batchingAndPublishInstanceSyncResult(
      PerpetualTaskId taskId, String accountId, List<InstanceInfo> responseData) {
    ContainerInstancesDetailsFetcher containerInstancesDetailsFetcher =
        (ContainerInstancesDetailsFetcher) instanceDetailsFetcher;
    int batchInstanceCount = 0;
    int batchReleaseDetailsCount = 0;
    List<String> releaseDetailsList = new ArrayList<>(); // call release detail API
    Map<String, List<InstanceInfo> > buffer = new HashMap<>();
    for (String releaseDetails : releaseDetailsList) {
      List<InstanceInfo> instanceInfos =
          containerInstancesDetailsFetcher.fetchRunningInstanceDetails(taskId, taskParams, releaseDetails);
      buffer.put(releaseDetails, instanceInfos);
      batchInstanceCount += instanceInfos.size();
      batchReleaseDetailsCount++;
      if (batchInstanceCount >= INSTANCE_COUNT_LIMIT || batchReleaseDetailsCount >= RELEASE_COUNT_LIMIT) {
        // publish logic

        buffer = new HashMap<>();
        batchInstanceCount = 0;
        batchReleaseDetailsCount = 0;
      }
    }
  }

  private void publishInstanceSyncResult(
      PerpetualTaskId taskId, String accountId, String namespace, DelegateResponseData responseData) {
    try {
      execute(delegateAgentManagerClient.publishInstanceSyncResult(taskId.getId(), accountId, responseData));
    } catch (Exception e) {
      log.error(
          String.format("Failed to publish container instance sync result. namespace [%s] and PerpetualTaskId [%s]",
              namespace, taskId.getId()),
          e);
    }
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }
}
