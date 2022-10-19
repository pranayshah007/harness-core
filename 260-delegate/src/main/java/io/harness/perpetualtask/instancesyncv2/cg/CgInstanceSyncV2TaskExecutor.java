/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesyncv2.cg;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.utils.AnyUtils;
import io.harness.perpetualtask.PerpetualTaskExecutionParams;
import io.harness.perpetualtask.PerpetualTaskExecutor;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncTaskParams;

import com.google.inject.Inject;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class CgInstanceSyncV2TaskExecutor implements PerpetualTaskExecutor {
  @Inject private InstanceDetailsFetcher instanceDetailsFetcher;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Came here. Add more details for task executor");
    CgInstanceSyncTaskParams taskParams = AnyUtils.unpack(params.getCustomizedParams(), CgInstanceSyncTaskParams.class);
    String cloudProviderType = taskParams.getCloudProviderType();

    PerpetualTaskResponse responseData = null;

    switch (cloudProviderType) {
      case "KUBERNETES":
        ContainerInstancesDetailsFetcher containerInstancesDetailsFetcher =
            (ContainerInstancesDetailsFetcher) instanceDetailsFetcher;
        responseData = containerInstancesDetailsFetcher.fetchRunningInstanceDetails(taskId, taskParams);
      default:
        throw new InvalidRequestException(
            format("Cloud Provider of given type : %s isn't supported", cloudProviderType));
    }

    /* boolean isFailureResponse = FAILURE == responseData.getCommandExecutionStatus();
     return PerpetualTaskResponse.builder()
         .responseCode(Response.SC_OK)
         .responseMessage(isFailureResponse ? responseData.getErrorMessage() : "success")
         .build();*/
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }
}
