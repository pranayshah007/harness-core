/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.govern.Switch.unhandled;
import static io.harness.logging.CommandExecutionStatus.FAILURE;

import static java.lang.String.format;

import io.harness.delegate.command.CommandExecutionResult;
import io.harness.delegate.command.CommandExecutionResultMapper;
import io.harness.delegate.service.ExecutionConfigOverrideFromFileOnDelegate;
import io.harness.delegate.task.k8s.ContainerDeploymentDelegateBaseHelper;
import io.harness.delegate.task.shell.ShellExecutorFactoryNG;
import io.harness.delegate.task.shell.SshExecutorFactoryNG;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.exception.CommandExecutionException;
import io.harness.shell.*;

import software.wings.beans.delegation.ShellScriptParameters;
import software.wings.core.winrm.executors.WinRmExecutor;
import software.wings.core.winrm.executors.WinRmExecutorFactory;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManagementDelegateService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class ShellScriptTaskHandlerNG {
  @Inject private SshExecutorFactoryNG sshExecutorFactoryNG;
  @Inject private ShellExecutorFactoryNG shellExecutorFactory;
  // Need slight code handling in it
  @Inject private ContainerDeploymentDelegateBaseHelper containerDeploymentDelegateBaseHelper;
  // Same as CG
  @Inject private EncryptionService encryptionService;
  @Inject private ExecutionConfigOverrideFromFileOnDelegate delegateLocalConfigService;
  @Inject private SecretManagementDelegateService secretManagementDelegateService;

  // CONFIRM IF WE NEED IT
  @Inject private WinRmExecutorFactory winrmExecutorFactory;

  public CommandExecutionResult handle(ShellScriptParameters parameters) {
    // Define output variables and secret output variables together
    List<String> items = new ArrayList<>();
    List<String> secretItems = new ArrayList<>();
    Long timeoutInMillis = parameters.getSshTimeOut() != null ? (long) parameters.getSshTimeOut() : null;
    if (parameters.getOutputVars() != null && StringUtils.isNotEmpty(parameters.getOutputVars().trim())) {
      items = Arrays.asList(parameters.getOutputVars().split("\\s*,\\s*"));
      items.replaceAll(String::trim);
    }
    if (parameters.getSecretOutputVars() != null && StringUtils.isNotEmpty(parameters.getSecretOutputVars().trim())) {
      secretItems = Arrays.asList(parameters.getSecretOutputVars().split("\\s*,\\s*"));
      secretItems.replaceAll(String::trim);
    }
    if (parameters.isExecuteOnDelegate()) {
      ScriptProcessExecutor executor =
          shellExecutorFactory.getExecutor(parameters.processExecutorConfig(null, encryptionService), null, null);
      if (parameters.isLocalOverrideFeatureFlag()) {
        parameters.setScript(delegateLocalConfigService.replacePlaceholdersWithLocalConfig(parameters.getScript()));
      }
      return CommandExecutionResultMapper.from(
          executor.executeCommandString(parameters.getScript(), items, secretItems, timeoutInMillis));
    }

    switch (parameters.getConnectionType()) {
      case SSH: {
        try {
          SshSessionConfig expectedSshConfig =
              parameters.sshSessionConfig(encryptionService, secretManagementDelegateService);
          BaseScriptExecutor executor = sshExecutorFactoryNG.getExecutor(expectedSshConfig, null, null);
          enableJSchLogsPerSSHTaskExecution(parameters.isEnableJSchLogs());
          return CommandExecutionResultMapper.from(
              executor.executeCommandString(parameters.getScript(), items, secretItems, timeoutInMillis));
        } catch (Exception e) {
          throw new CommandExecutionException("Bash Script Failed to execute", e);
        } finally {
          SshSessionManager.evictAndDisconnectCachedSession(parameters.getActivityId(), parameters.getHost());
          disableJSchLogsPerSSHTaskExecution();
        }
      }
      case WINRM: {
        try {
          WinRmSessionConfig winRmSessionConfig = parameters.winrmSessionConfig(encryptionService);
          WinRmExecutor executor = winrmExecutorFactory.getExecutor(
              winRmSessionConfig, parameters.isDisableWinRMCommandEncodingFFSet(), parameters.isSaveExecutionLogs());
          return CommandExecutionResultMapper.from(
              executor.executeCommandString(parameters.getScript(), items, secretItems, timeoutInMillis));
        } catch (Exception e) {
          throw new CommandExecutionException("Powershell script Failed to execute", e);
        }
      }
      default:
        unhandled(parameters.getConnectionType());
        return CommandExecutionResult.builder()
            .status(FAILURE)
            .errorMessage(format("Unsupported ConnectionType %s", parameters.getConnectionType()))
            .build();
    }
  }

  private void enableJSchLogsPerSSHTaskExecution(boolean enableJSchLogs) {
    if (enableJSchLogs) {
      JSchLogAdapter.attachLogger().enableDebugLogLevel();
    }
  }

  private void disableJSchLogsPerSSHTaskExecution() {
    JSchLogAdapter.detachLogger();
  }
}
