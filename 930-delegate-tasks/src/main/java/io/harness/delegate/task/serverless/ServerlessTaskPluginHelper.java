/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.logging.LogCallback;
import io.harness.serverless.PluginCommand;
import io.harness.serverless.ServerlessCliResponse;
import io.harness.serverless.ServerlessClient;
import io.harness.serverless.ServerlessCommandTaskHelper;
import io.harness.serverless.model.ServerlessDelegateTaskParams;

import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
@Singleton
public class ServerlessTaskPluginHelper {
  public ServerlessCliResponse installServerlessPlugin(ServerlessDelegateTaskParams serverlessDelegateTaskParams,
      ServerlessClient serverlessClient, String pluginName, LogCallback executionLogCallback, long timeoutInMillis,
      String ConfigOverridePath) throws InterruptedException, IOException, TimeoutException {
    executionLogCallback.saveExecutionLog("\nInstalling the serverless plugin: " + pluginName);
    PluginCommand command = serverlessClient.plugin().pluginName(pluginName);
    if (EmptyPredicate.isNotEmpty(ConfigOverridePath)) {
      command.config(ConfigOverridePath);
    }
    return ServerlessCommandTaskHelper.executeCommand(command, serverlessDelegateTaskParams.getWorkingDirectory(),
        executionLogCallback, true, timeoutInMillis, Collections.emptyMap());
  }
}
