/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.shell.winrm.WinRmUtils.getWinRmSessionConfig;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.shell.AbstractDownloadArtifactCommandHandler;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.shell.ssh.CommandHandler;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.winrm.WinRmExecutorFactoryNG;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.shell.BaseScriptExecutor;
import io.harness.shell.ScriptType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
@Singleton
public class WinRmDownloadArtifactCommandHandler
    extends AbstractDownloadArtifactCommandHandler implements CommandHandler {
  @Inject private WinRmExecutorFactoryNG winRmExecutorFactoryNG;
  @Inject private WinRmConfigAuthEnhancer winRmConfigAuthEnhancer;

  @Override
  public BaseScriptExecutor getExecutor(CommandTaskParameters commandTaskParameters, NgCommandUnit commandUnit,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress,
      Map<String, Object> taskContext) {
    WinrmTaskParameters winRmCommandTaskParameters = (WinrmTaskParameters) commandTaskParameters;

    WinRmSessionConfig config = getWinRmSessionConfig(commandUnit, winRmCommandTaskParameters, winRmConfigAuthEnhancer);
    return winRmExecutorFactoryNG.getExecutor(config, winRmCommandTaskParameters.isDisableWinRMCommandEncodingFFSet(),
        winRmCommandTaskParameters.isWinrmScriptCommandSplit(), logStreamingTaskClient, commandUnitsProgress);
  }

  @Override
  public ScriptType getScriptType() {
    return ScriptType.POWERSHELL;
  }
}
