/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.serverless;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.task.shell.FileBasedSshScriptExecutorNG;
import io.harness.delegate.task.shell.ssh.ArtifactCommandUnitHandler;
import io.harness.logging.LogCallback;
import io.harness.shell.ScriptSshExecutor;
import io.harness.shell.SshSessionConfig;

import java.util.Map;

@OwnedBy(HarnessTeam.CDP)
public class ServerlessSshExecutorFactoryNG {
  public static ScriptSshExecutor getExecutor(SshSessionConfig sshSessionConfig,
                                              LogCallback logCallback, CommandUnitsProgress commandUnitsProgress) {
    return new ScriptSshExecutor(
            logCallback, true,
        sshSessionConfig);
  }
}
