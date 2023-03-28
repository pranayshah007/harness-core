/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.network.SafeHttpCall.execute;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.grpc.utils.AnyUtils;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.PdcInstanceSyncPerpetualTaskParams;

import software.wings.beans.HostReachabilityInfo;
import software.wings.beans.dto.SettingAttribute;
import software.wings.service.impl.aws.model.response.HostReachabilityResponse;
import software.wings.utils.HostValidationService;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Response;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class PdcInstanceSyncExecutor extends PerpetualTaskExecutorBase implements PerpetualTaskExecutor {
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private HostValidationService hostValidationService;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    final PdcInstanceSyncPerpetualTaskParams instanceSyncParams =
        AnyUtils.unpack(params.getCustomizedParams(), PdcInstanceSyncPerpetualTaskParams.class);

    final SettingAttribute settingAttribute =
        (SettingAttribute) getKryoSerializer(params.getReferenceFalseKryoSerializer())
            .asObject(instanceSyncParams.getSettingAttribute().toByteArray());
    HostReachabilityResponse response;
    try {
      List<HostReachabilityInfo> hostReachabilityInfos =
          hostValidationService.validateReachability(instanceSyncParams.getHostNamesList(), settingAttribute);
      response = HostReachabilityResponse.builder()
                     .hostReachabilityInfoList(hostReachabilityInfos)
                     .executionStatus(ExecutionStatus.SUCCESS)
                     .build();
    } catch (Exception ex) {
      String message =
          "Exception while running validateReachability for hosts: " + instanceSyncParams.getHostNamesList() + ex;
      log.error(message);
      response =
          HostReachabilityResponse.builder().executionStatus(ExecutionStatus.FAILED).errorMessage(message).build();
    }
    try {
      execute(delegateAgentManagerClient.publishInstanceSyncResultV2(
          taskId.getId(), settingAttribute.getAccountId(), response));
    } catch (Exception e) {
      log.error(String.format(
                    "Failed to publish the instance collection result to manager for PDC taskId [%s]", taskId.getId()),
          e);
    }

    return getPerpetualTaskResponse(response);
  }

  private PerpetualTaskResponse getPerpetualTaskResponse(HostReachabilityResponse response) {
    String message = "success";
    if (response.getExecutionStatus() == ExecutionStatus.FAILED) {
      message = response.getErrorMessage();
    }

    return PerpetualTaskResponse.builder().responseCode(Response.SC_OK).responseMessage(message).build();
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }
}
