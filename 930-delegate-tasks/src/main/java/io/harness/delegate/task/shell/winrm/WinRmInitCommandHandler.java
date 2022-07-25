/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.shell.winrm.WinRmCommandConstants.SESSION_TIMEOUT;

import static software.wings.common.Constants.WINDOWS_HOME_DIR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.shell.ssh.CommandHandler;
import io.harness.delegate.task.ssh.NGCommandUnitType;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.NgInitCommandUnit;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.delegate.task.winrm.WinRmExecutorFactoryNG;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.delegate.task.winrm.WinRmSessionConfig.WinRmSessionConfigBuilder;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;

import software.wings.core.winrm.executors.WinRmExecutor;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(CDP)
@Singleton
public class WinRmInitCommandHandler implements CommandHandler {
  @Inject private WinRmExecutorFactoryNG winRmExecutorFactoryNG;
  @Inject private WinRmConfigAuthEnhancer winRmConfigAuthEnhancer;

  @Override
  public CommandExecutionStatus handle(CommandTaskParameters parameters, NgCommandUnit commandUnit,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) {
    if (!(parameters instanceof WinrmTaskParameters)) {
      throw new InvalidRequestException("Invalid task parameters submitted for command task.");
    }

    if (!(commandUnit instanceof NgInitCommandUnit)) {
      throw new InvalidRequestException("Invalid command unit specified for command task.");
    }

    WinrmTaskParameters winRmCommandTaskParameters = (WinrmTaskParameters) parameters;

    NgInitCommandUnit initCommandUnit = (NgInitCommandUnit) commandUnit;
    WinRmSessionConfigBuilder configBuilder = WinRmSessionConfig.builder()
                                                  .accountId(winRmCommandTaskParameters.getAccountId())
                                                  .executionId(winRmCommandTaskParameters.getExecutionId())
                                                  .workingDirectory(WINDOWS_HOME_DIR)
                                                  .commandUnitName(initCommandUnit.getName())
                                                  .environment(winRmCommandTaskParameters.getEnvironmentVariables())
                                                  .hostname(winRmCommandTaskParameters.getHost())
                                                  .timeout(SESSION_TIMEOUT);

    WinRmSessionConfig config =
        winRmConfigAuthEnhancer.configureAuthentication(winRmCommandTaskParameters, configBuilder);
    WinRmExecutor executor = winRmExecutorFactoryNG.getExecutor(config,
        winRmCommandTaskParameters.isDisableWinRMCommandEncodingFFSet(), logStreamingTaskClient, commandUnitsProgress);

    for (NgCommandUnit cu : parameters.getCommandUnits()) {
      if (NGCommandUnitType.SCRIPT.equals(cu.getCommandUnitType())) {
        ScriptCommandUnit scriptCommandUnit = (ScriptCommandUnit) cu;
        scriptCommandUnit.setCommand(scriptCommandUnit.getScript());
      }
    }
    return executor.executeCommandString(getInitCommand(), true);
  }

  private String getInitCommand() {
    String script = "$RUNTIME_PATH=[System.Environment]::ExpandEnvironmentVariables(\"%s\")%n"
        + "if(!(Test-Path \"$RUNTIME_PATH\"))%n"
        + "{%n"
        + "    New-Item -ItemType Directory -Path \"$RUNTIME_PATH\"%n"
        + "    Write-Host \"$RUNTIME_PATH Folder Created Successfully.\"%n"
        + "}%n"
        + "else%n"
        + "{%n"
        + "    Write-Host \"${RUNTIME_PATH} Folder already exists.\"%n"
        + "}";

    return String.format(script, WINDOWS_HOME_DIR);
  }
}
