/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.handlermapping.handlers;

import io.harness.delegate.core.beans.CleanupInfraResponse;
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
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CleanupHandler implements Handler {
  private final RunnersFactory runnersFactory;
  private final DelegateAgentManagerClient managerClient;
  @Override
  public void handle(final String runnerType, final TaskPayload taskPayload, final Map<String, char[]> decryptedSecrets,
      final Context context) {
    final Runner runner = runnersFactory.get(runnerType);
    runner.cleanup(taskPayload.getExecutionInfraId(), context);

    CleanupInfraResponse response;
    try {
      runner.cleanup(taskPayload.getExecutionInfraId(), context);
      response = CleanupInfraResponse.newBuilder().setResponseCode(ResponseCode.RESPONSE_OK).build();
    } catch (Exception e) {
      log.error("Cleanup infra by runner {} failed with exception ", runner, e);
      response = CleanupInfraResponse.newBuilder()
                     .setResponseCode(ResponseCode.RESPONSE_FAILED)
                     .setErrorMessage("Failed to cleanup the infra")
                     .build();
    }
    final var call = managerClient.sendCleanupInfraResponse(
        taskPayload.getId(), context.get(Context.DELEGATE_ID), context.get(Context.ACCOUNT_ID), response);
    final String failureMessage = String.format("Failed to send cleanup infra response by runner %s", runnerType);
    ManagerCallHelper.executeCallWithBackOffRetry(call, 5, failureMessage);
  }
}
