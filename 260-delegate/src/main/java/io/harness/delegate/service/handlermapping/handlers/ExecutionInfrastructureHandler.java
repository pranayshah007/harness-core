/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.handlermapping.handlers;

import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.core.beans.ExecutionInfraInfo;
import io.harness.delegate.core.beans.ResponseCode;
import io.harness.delegate.core.beans.SetupInfraResponse;
import io.harness.delegate.core.beans.TaskPayload;
import io.harness.delegate.service.common.ManagerCallHelper;
import io.harness.delegate.service.handlermapping.context.Context;
import io.harness.delegate.service.runners.RunnersFactory;
import io.harness.delegate.service.runners.itfc.Runner;
import io.harness.managerclient.DelegateAgentManagerClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ExecutionInfrastructureHandler implements Handler {
  private final RunnersFactory runnersFactory;
  private final DelegateAgentManagerClient managerClient;
  private final DelegateConfiguration delegateConfiguration;

  @Override
  public void handle(String runnerType, TaskPayload taskPayload, Context context) {
    Runner runner = runnersFactory.get(runnerType);
    SetupInfraResponse response;
    try {
      runner.init(taskPayload.getId(), taskPayload.getInfraData(), context);
      response = SetupInfraResponse.newBuilder()
                     .setResponseCode(ResponseCode.RESPONSE_OK)
                     .setLocation(ExecutionInfraInfo.newBuilder()
                                      .setDelegateName(delegateConfiguration.getDelegateName())
                                      .setRunnerType(runnerType)
                                      .build())
                     .build();

    } catch (Exception e) {
      log.error("init infra by runner {} failed with exception ", runner, e);
      response = SetupInfraResponse.newBuilder().setResponseCode(ResponseCode.RESPONSE_FAILED).build();
    }
    var call = managerClient.sendSetupInfraResponse(taskPayload.getId(), context.get(Context.DELEGATE_ID),
        context.get(delegateConfiguration.getAccountId()), response);
    String failureMessage = String.format("Failed to send init infra response by runner %s", runnerType);
    ManagerCallHelper.executeCallWithBackOffRetry(call, 5, failureMessage);
  }
}
