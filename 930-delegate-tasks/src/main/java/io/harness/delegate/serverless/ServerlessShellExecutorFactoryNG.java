/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.serverless;

import com.google.inject.Singleton;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.logging.LogCallback;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ScriptSshExecutor;
import io.harness.shell.ShellExecutorConfig;
import io.harness.shell.SshSessionConfig;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class ServerlessShellExecutorFactoryNG {

  public static ScriptProcessExecutor getExecutor(ShellExecutorConfig shellExecutorConfig,
                                                  LogCallback logCallback, CommandUnitsProgress commandUnitsProgress) {
    return new ScriptProcessExecutor(logCallback, true, shellExecutorConfig);
  }

}
