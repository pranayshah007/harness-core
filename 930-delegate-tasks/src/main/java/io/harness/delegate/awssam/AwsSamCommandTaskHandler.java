/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.awssam;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.awssam.AwsSamDelegateTaskParams;
import io.harness.delegate.task.awssam.request.AwsSamCommandRequest;
import io.harness.delegate.task.awssam.response.AwsSamCommandResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public abstract class AwsSamCommandTaskHandler {
  public AwsSamCommandResponse executeTask(AwsSamCommandRequest awsSamCommandRequest,
      AwsSamDelegateTaskParams awsSamDelegateTaskParams, ILogStreamingTaskClient iLogStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception {
    return executeTaskInternal(
        awsSamCommandRequest, awsSamDelegateTaskParams, iLogStreamingTaskClient, commandUnitsProgress);
  }

  protected abstract AwsSamCommandResponse executeTaskInternal(AwsSamCommandRequest awsSamCommandRequest,
      AwsSamDelegateTaskParams awsSamDelegateTaskParams, ILogStreamingTaskClient iLogStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) throws Exception;
}
