/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.common;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.logging.LogCallback;

@OwnedBy(CDP)
public class NgAzureLogCallbackProvider implements AzureLogCallbackProvider {
  private final ILogStreamingTaskClient streamingTaskClient;
  private final CommandUnitsProgress commandUnitsProgress;

  NgAzureLogCallbackProvider(ILogStreamingTaskClient streamingTaskClient, CommandUnitsProgress commandUnitsProgress) {
    this.streamingTaskClient = streamingTaskClient;
    this.commandUnitsProgress = commandUnitsProgress;
  }

  @Override
  public LogCallback obtainLogCallback(String commandUnitName) {
    // TODO: check if azure flows has any scenario of reusing existing stream/command unit. In this case
    //  shouldOpenStream should be variable
    return new NGDelegateLogCallback(streamingTaskClient, commandUnitName, true, commandUnitsProgress);
  }
}
