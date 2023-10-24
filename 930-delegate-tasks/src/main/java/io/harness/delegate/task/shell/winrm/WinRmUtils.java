/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.winrm;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.shell.winrm.WinRmCommandConstants.SESSION_TIMEOUT;

import static software.wings.common.Constants.WINDOWS_HOME_DIR;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.shell.WinRmShellScriptTaskParametersNG;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.WinRmInfraDelegateConfig;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.delegate.task.winrm.WinRmSessionConfig.WinRmSessionConfigBuilder;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.dto.secrets.WinRmCommandParameter;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptType;
import io.harness.shell.ShellExecutorConfig;

import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRADITIONAL})
@OwnedBy(CDP)
@UtilityClass
public class WinRmUtils {
  public static WinRmSessionConfig getWinRmSessionConfig(NgCommandUnit commandUnit,
      WinrmTaskParameters winRmCommandTaskParameters, WinRmConfigAuthEnhancer winRmConfigAuthEnhancer) {
    WinRmSessionConfigBuilder configBuilder =
        WinRmSessionConfig.builder()
            .accountId(winRmCommandTaskParameters.getAccountId())
            .executionId(winRmCommandTaskParameters.getExecutionId())
            .workingDirectory(getWorkingDir(commandUnit))
            .commandUnitName(commandUnit.getName())
            .environment(winRmCommandTaskParameters.getEnvironmentVariables())
            .hostname(winRmCommandTaskParameters.getHost())
            .timeout(winRmCommandTaskParameters.getSessionTimeout() != null
                    ? Math.toIntExact(winRmCommandTaskParameters.getSessionTimeout())
                    : SESSION_TIMEOUT)
            .commandParameters(getCommandParameters(winRmCommandTaskParameters))
            .disableWinRmEnvVarEscaping(shouldDisableWinRmEnvVarsEscaping(winRmCommandTaskParameters));

    final WinRmInfraDelegateConfig winRmInfraDelegateConfig = winRmCommandTaskParameters.getWinRmInfraDelegateConfig();
    if (winRmInfraDelegateConfig == null) {
      throw new InvalidRequestException("Task parameters must include WinRm Infra Delegate config.");
    }

    return winRmConfigAuthEnhancer.configureAuthentication(winRmInfraDelegateConfig.getWinRmCredentials(),
        winRmInfraDelegateConfig.getEncryptionDataDetails(), configBuilder,
        winRmCommandTaskParameters.isUseWinRMKerberosUniqueCacheFile());
  }

  /***
   * The reason why we need to control disabling of env vars escaping depends solely on the winrm execution approach:
   * In case of encoded commands, the vars part of the script will be encoded and executed and this won't break any
   * powershell execution or script transfer. In case of un encoded commands, if the split command routing is set, the
   * script will be encoded and transfered as a file on remote and executed there - which won't bring any side effects.
   * Otherwise if the split command routing is disabled, there is no way to escape PowerShell escaped symbols and the
   * script containing variables that leverage special symbols will fail to execute via Powershell.
   * @param winRmCommandTaskParameters
   * @return
   */
  public static boolean shouldDisableWinRmEnvVarsEscaping(WinrmTaskParameters winRmCommandTaskParameters) {
    if (winRmCommandTaskParameters.isDisableWinRMCommandEncodingFFSet()
        && !winRmCommandTaskParameters.isWinrmScriptCommandSplit()) {
      return false;
    }

    return winRmCommandTaskParameters.isDisableWinRmEnvVarEscaping();
  }

  /***
   * The reason why we need to control disabling of env vars escaping depends solely on the winrm execution approach:
   * In case of encoded commands, the vars part of the script will be encoded and executed and this won't break any
   * powershell execution or script transfer. In case of un encoded commands, if the split command routing is set, the
   * script will be encoded and transfered as a file on remote and executed there - which won't bring any side effects.
   * Otherwise if the split command routing is disabled, there is no way to escape PowerShell escaped symbols and the
   * script containing variables that leverage special symbols will fail to execute via Powershell.
   * @param winRmCommandTaskParameters
   * @return
   */
  public static boolean shouldDisableWinRmEnvVarsEscaping(WinRmShellScriptTaskParametersNG winRmCommandTaskParameters) {
    if (winRmCommandTaskParameters.isDisableCommandEncoding()
        && !winRmCommandTaskParameters.isWinrmScriptCommandSplit()) {
      return false;
    }

    return winRmCommandTaskParameters.isDisableWinRmEnvVarEscaping();
  }

  private static List<WinRmCommandParameter> getCommandParameters(WinrmTaskParameters winRmCommandTaskParameters) {
    List<WinRmCommandParameter> commandParameters = winRmCommandTaskParameters.getWinRmInfraDelegateConfig() != null
            && winRmCommandTaskParameters.getWinRmInfraDelegateConfig().getWinRmCredentials() != null
        ? winRmCommandTaskParameters.getWinRmInfraDelegateConfig().getWinRmCredentials().getParameters()
        : Collections.emptyList();
    return commandParameters == null ? Collections.emptyList() : commandParameters;
  }

  public static ShellExecutorConfig getShellExecutorConfig(
      WinrmTaskParameters taskParameters, NgCommandUnit commandUnit) {
    return ShellExecutorConfig.builder()
        .accountId(taskParameters.getAccountId())
        .executionId(taskParameters.getExecutionId())
        .commandUnitName(commandUnit.getName())
        .workingDirectory(commandUnit.getWorkingDirectory())
        .environment(taskParameters.getEnvironmentVariables())
        .scriptType(ScriptType.POWERSHELL)
        .build();
  }

  public static CommandExecutionStatus getStatus(ExecuteCommandResponse executeCommandResponse) {
    if (executeCommandResponse == null) {
      return CommandExecutionStatus.FAILURE;
    }
    if (executeCommandResponse.getStatus() == null) {
      return CommandExecutionStatus.FAILURE;
    }
    return executeCommandResponse.getStatus();
  }

  public static String getWorkingDir(String workingDirectory) {
    return isNotEmpty(workingDirectory) ? workingDirectory : WINDOWS_HOME_DIR;
  }

  public static String getWorkingDir(NgCommandUnit commandUnit) {
    String workingDir = isNotEmpty(commandUnit.getDestinationPath()) ? commandUnit.getDestinationPath()
                                                                     : commandUnit.getWorkingDirectory();
    return getWorkingDir(workingDir);
  }
}
