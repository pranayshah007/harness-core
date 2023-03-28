/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.network.SafeHttpCall.execute;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.task.cf.PcfDelegateTaskHelper;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.request.CfInstanceSyncRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfInstanceSyncResponse;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.PcfInstanceSyncPerpetualTaskParams;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;

import software.wings.service.InstanceSyncConstants;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.ByteString;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Response;

@Slf4j
@Singleton
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(HarnessTeam.CDP)
public class PcfInstanceSyncDelegateExecutor implements PerpetualTaskExecutor {
  @Inject PcfDelegateTaskHelper pcfDelegateTaskHelper;
  @Inject DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    if (log.isDebugEnabled()) {
      log.debug("Running the InstanceSync perpetual task executor for task id: {}", taskId);
    }
    PcfInstanceSyncPerpetualTaskParams instanceSyncParams =
        AnyUtils.unpack(params.getCustomizedParams(), PcfInstanceSyncPerpetualTaskParams.class);
    String applicationName = instanceSyncParams.getApplicationName();
    String orgName = instanceSyncParams.getOrgName();
    String space = instanceSyncParams.getSpace();

    CfInternalConfig cfInternalConfig =
        (CfInternalConfig) kryoSerializer.asObject(instanceSyncParams.getPcfConfig().toByteArray());

    ByteString encryptedData = instanceSyncParams.getEncryptedData();

    List<EncryptedDataDetail> encryptedDataDetailList =
        (List<EncryptedDataDetail>) kryoSerializer.asObject(encryptedData.toByteArray());

    CfInstanceSyncRequest cfInstanceSyncRequest = CfInstanceSyncRequest.builder()
                                                      .pcfConfig(cfInternalConfig)
                                                      .pcfApplicationName(applicationName)
                                                      .organization(orgName)
                                                      .space(space)
                                                      .pcfCommandType(CfCommandRequest.PcfCommandType.APP_DETAILS)
                                                      .timeoutIntervalInMin(InstanceSyncConstants.TIMEOUT_SECONDS / 60)
                                                      .build();
    CfCommandExecutionResponse cfCommandExecutionResponse = pcfDelegateTaskHelper.getPcfCommandExecutionResponse(
        cfInstanceSyncRequest, encryptedDataDetailList, true, null);

    CfInstanceSyncResponse cfInstanceSyncResponse =
        (CfInstanceSyncResponse) cfCommandExecutionResponse.getPcfCommandResponse();
    try {
      if (cfInstanceSyncResponse == null) {
        cfInstanceSyncResponse = CfInstanceSyncResponse.builder()
                                     .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                     .name(applicationName)
                                     .organization(orgName)
                                     .space(space)
                                     .output("Null cfInstanceSyncResponse returned")
                                     .build();
        cfCommandExecutionResponse.setPcfCommandResponse(cfInstanceSyncResponse);
      } else {
        int instanceSize = cfInstanceSyncResponse.getInstanceIndices() == null
            ? 0
            : cfInstanceSyncResponse.getInstanceIndices().size();
        log.info("Found {} number of instances pcf deployment", instanceSize);
      }
      execute(delegateAgentManagerClient.publishInstanceSyncResult(
          taskId.getId(), cfInternalConfig.getAccountId(), cfCommandExecutionResponse));
    } catch (Exception ex) {
      log.error(
          "Failed to publish the instance collection result to manager for application name {} and PerpetualTaskId {}",
          applicationName, taskId.getId(), ex);
    }
    CommandExecutionStatus commandExecutionStatus = cfCommandExecutionResponse.getCommandExecutionStatus();
    log.info("Published instanceSync successfully for perp task: {}, state: {}", taskId, commandExecutionStatus);
    return getPerpetualTaskResponse(cfCommandExecutionResponse, commandExecutionStatus);
  }

  private PerpetualTaskResponse getPerpetualTaskResponse(
      CfCommandExecutionResponse cfCommandExecutionResponse, CommandExecutionStatus commandExecutionStatus) {
    String message = "success";
    if (CommandExecutionStatus.FAILURE == commandExecutionStatus) {
      message = cfCommandExecutionResponse.getErrorMessage();
    }

    return PerpetualTaskResponse.builder().responseCode(Response.SC_OK).responseMessage(message).build();
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }
}
