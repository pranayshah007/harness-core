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
import io.harness.shell.ScriptSshExecutor;
import io.harness.shell.SshSessionConfig;

import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class SshExecutorFactoryNG {
  public ScriptSshExecutor getExecutor(SshSessionConfig sshSessionConfig,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) {
    return new ScriptSshExecutor(
        getExecutionLogCallback(sshSessionConfig, logStreamingTaskClient, commandUnitsProgress), true,
        sshSessionConfig);
  }

  public FileBasedSshScriptExecutorNG getFileBasedExecutor(SshSessionConfig sshSessionConfig,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress,
      Map<String, ArtifactCommandUnitHandler> artifactCommandHandlers) {
    return new FileBasedSshScriptExecutorNG(
        getExecutionLogCallback(sshSessionConfig, logStreamingTaskClient, commandUnitsProgress), true, sshSessionConfig,
        artifactCommandHandlers);
  }

  private static LogCallback getExecutionLogCallback(SshSessionConfig sshSessionConfig,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) {
    return new NGDelegateLogCallback(
        logStreamingTaskClient, sshSessionConfig.getCommandUnitName(), true, commandUnitsProgress);
  }

  public ScriptSshExecutor getExecutor(SshSessionConfig sshSessionConfig, CommandUnitsProgress commandUnitsProgress) {
    return new ScriptSshExecutor(new DummyLogCallbackImpl(), true, sshSessionConfig);
  }
}
