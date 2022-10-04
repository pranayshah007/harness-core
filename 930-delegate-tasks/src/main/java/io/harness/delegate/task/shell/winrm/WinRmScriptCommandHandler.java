/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.winrm;

import static io.harness.delegate.task.shell.winrm.WinRmCommandConstants.SESSION_TIMEOUT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.shell.ssh.CommandHandler;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.delegate.task.ssh.WinRmInfraDelegateConfig;
import io.harness.delegate.task.winrm.WinRmExecutorFactoryNG;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.delegate.task.winrm.WinRmSessionConfig.WinRmSessionConfigBuilder;
import io.harness.exception.InvalidRequestException;
import io.harness.shell.ExecuteCommandResponse;

import software.wings.core.winrm.executors.WinRmExecutor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class WinRmScriptCommandHandler implements CommandHandler {
  private final WinRmExecutorFactoryNG winRmExecutorFactoryNG;
  private final WinRmConfigAuthEnhancer winRmConfigAuthEnhancer;

  @Inject
  public WinRmScriptCommandHandler(
      WinRmExecutorFactoryNG winRmExecutorFactoryNG, WinRmConfigAuthEnhancer winRmConfigAuthEnhancer) {
    this.winRmExecutorFactoryNG = winRmExecutorFactoryNG;
    this.winRmConfigAuthEnhancer = winRmConfigAuthEnhancer;
  }

  @Override
  public ExecuteCommandResponse handle(CommandTaskParameters parameters, NgCommandUnit commandUnit,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress,
      Map<String, Object> taskContext) {
    if (!(parameters instanceof WinrmTaskParameters)) {
      throw new InvalidRequestException("Invalid task parameters submitted for command task.");
    }
    WinrmTaskParameters winRmCommandTaskParameters = (WinrmTaskParameters) parameters;
    if (!(commandUnit instanceof ScriptCommandUnit)) {
      throw new InvalidRequestException("Invalid command unit specified for command task.");
    }

    ScriptCommandUnit scriptCommandUnit = (ScriptCommandUnit) commandUnit;
    WinRmSessionConfigBuilder configBuilder = WinRmSessionConfig.builder()
                                                  .accountId(winRmCommandTaskParameters.getAccountId())
                                                  .executionId(winRmCommandTaskParameters.getExecutionId())
                                                  .workingDirectory(scriptCommandUnit.getWorkingDirectory())
                                                  .commandUnitName(scriptCommandUnit.getName())
                                                  .environment(winRmCommandTaskParameters.getEnvironmentVariables())
                                                  .hostname(winRmCommandTaskParameters.getHost())
                                                  .timeout(SESSION_TIMEOUT);

    final WinRmInfraDelegateConfig winRmInfraDelegateConfig = winRmCommandTaskParameters.getWinRmInfraDelegateConfig();
    if (winRmInfraDelegateConfig == null) {
      throw new InvalidRequestException("Task parameters must include WinRm Infra Delegate config.");
    }

    WinRmSessionConfig config = winRmConfigAuthEnhancer.configureAuthentication(
        winRmInfraDelegateConfig.getWinRmCredentials(), winRmInfraDelegateConfig.getEncryptionDataDetails(),
        configBuilder, winRmCommandTaskParameters.isUseWinRMKerberosUniqueCacheFile());
    WinRmExecutor executor = winRmExecutorFactoryNG.getExecutor(config,
        winRmCommandTaskParameters.isDisableWinRMCommandEncodingFFSet(), logStreamingTaskClient, commandUnitsProgress);

    return executor.executeCommandString(
        scriptCommandUnit.getCommand(), winRmCommandTaskParameters.getOutputVariables());
  }
}
