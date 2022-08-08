/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.logging.LogLevel;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.helpers.ext.ecs.request.EcsCommandRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import java.util.List;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public abstract class EcsCommandTaskHandler {
  @Inject protected DelegateFileManager delegateFileManager;
  @Inject protected EncryptionService encryptionService;
  @Inject protected DelegateLogService delegateLogService;

  public EcsCommandExecutionResponse executeTask(
      EcsCommandRequest ecsCommandRequest, List<EncryptedDataDetail> encryptedDataDetails) {
    ExecutionLogCallback executionLogCallback =
        new ExecutionLogCallback(delegateLogService, ecsCommandRequest.getAccountId(), ecsCommandRequest.getAppId(),
            ecsCommandRequest.getActivityId(), ecsCommandRequest.getCommandName());

    try {
      return executeTaskInternal(ecsCommandRequest, encryptedDataDetails, executionLogCallback);
    } catch (Exception e) {
      executionLogCallback.saveExecutionLog(
          "Failed while executing EcsCommandTask: " + ecsCommandRequest.getEcsCommandType(), LogLevel.ERROR);
      executionLogCallback.saveExecutionLog(e.getMessage(), LogLevel.ERROR);
      throw new WingsException(ErrorCode.GENERAL_ERROR, e.getMessage(), WingsException.USER)
          .addParam("message", e.getMessage());
    }
  }

  protected abstract EcsCommandExecutionResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      List<EncryptedDataDetail> encryptedDataDetails, ExecutionLogCallback executionLogCallback);
}
