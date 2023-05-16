/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.task.shell.ssh.ArtifactCommandUnitHandler;
import io.harness.logging.DummyLogCallbackImpl;
import io.harness.logging.LogCallback;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ShellExecutorConfig;

import com.google.inject.Singleton;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
@Singleton
public class ShellExecutorFactoryNG {
  public ScriptProcessExecutor getExecutor(ShellExecutorConfig shellExecutorConfig,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) {
    return new ScriptProcessExecutor(
        getExecutionLogCallback(shellExecutorConfig, logStreamingTaskClient, commandUnitsProgress, false), true,
        shellExecutorConfig);
  }

  public FileBasedProcessScriptExecutorNG getFileBasedExecutor(ShellExecutorConfig shellExecutorConfig,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress,
      Map<String, ArtifactCommandUnitHandler> artifactCommandHandlers) {
    return new FileBasedProcessScriptExecutorNG(
        getExecutionLogCallback(shellExecutorConfig, logStreamingTaskClient, commandUnitsProgress, true), true,
        artifactCommandHandlers);
  }

  public ScriptProcessExecutor getExecutor(ShellExecutorConfig shellExecutorConfig,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress,
      boolean shouldOpenStream) {
    return new ScriptProcessExecutor(
        getExecutionLogCallback(shellExecutorConfig, logStreamingTaskClient, commandUnitsProgress, shouldOpenStream),
        true, shellExecutorConfig);
  }

  private static LogCallback getExecutionLogCallback(ShellExecutorConfig shellExecutorConfig,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress,
      boolean shouldOpenStream) {
    return new NGDelegateLogCallback(
        logStreamingTaskClient, shellExecutorConfig.getCommandUnitName(), shouldOpenStream, commandUnitsProgress);
  }

  public ScriptProcessExecutor getExecutor(
      ShellExecutorConfig shellExecutorConfig, CommandUnitsProgress commandUnitsProgress) {
    return new ScriptProcessExecutor(new DummyLogCallbackImpl(), false, shellExecutorConfig);
  }

  public ScriptProcessExecutor getExecutorForCustomArtifactScriptExecution(
      ShellExecutorConfig shellExecutorConfig, LogCallback logCallback) {
    return new ScriptProcessExecutor(logCallback, true, shellExecutorConfig);
  }
}
