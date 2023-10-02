/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.cf;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.cf.PcfCommandTaskHandler;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class PcfDelegateTaskHelper {
  @Inject private Map<String, PcfCommandTaskHandler> commandTaskTypeToTaskHandlerMap;

  public CfCommandExecutionResponse getPcfCommandExecutionResponse(CfCommandRequest cfCommandRequest,
      List<EncryptedDataDetail> encryptedDataDetails, boolean isInstanceSync,
      ILogStreamingTaskClient logStreamingTaskClient) {
    try {
      return commandTaskTypeToTaskHandlerMap.get(cfCommandRequest.getPcfCommandType().name())
          .executeTask(cfCommandRequest, encryptedDataDetails, isInstanceSync, logStreamingTaskClient);
    } catch (Exception ex) {
      log.error("Exception in processing PCF task [{}]", cfCommandRequest.toString(), ex);
      return CfCommandExecutionResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(ExceptionUtils.getMessage(ex))
          .build();
    }
  }
}
