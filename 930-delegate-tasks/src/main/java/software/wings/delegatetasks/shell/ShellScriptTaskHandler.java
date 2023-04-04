/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.govern.Switch.unhandled;
import static io.harness.logging.CommandExecutionStatus.FAILURE;

import static java.lang.String.format;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.delegate.command.CommandExecutionResultMapper;
import io.harness.delegate.service.ExecutionConfigOverrideFromFileOnDelegate;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.exception.CommandExecutionException;
import io.harness.shell.BaseScriptExecutor;
import io.harness.shell.JSchLogAdapter;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.SshSessionManager;

import software.wings.beans.delegation.ShellScriptParameters;
import software.wings.core.local.executors.ShellExecutorFactory;
import software.wings.core.ssh.executors.SshExecutorFactory;
import software.wings.core.winrm.executors.WinRmExecutor;
import software.wings.core.winrm.executors.WinRmExecutorFactory;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManagementDelegateService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

@Singleton
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@BreakDependencyOn("software.wings.service.intfc.security.EncryptionService")
public class ShellScriptTaskHandler {
  @Inject private SshExecutorFactory sshExecutorFactory;
  @Inject private WinRmExecutorFactory winrmExecutorFactory;
  @Inject private ShellExecutorFactory shellExecutorFactory;
  @Inject private EncryptionService encryptionService;
  @Inject private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;
  @Inject private ExecutionConfigOverrideFromFileOnDelegate delegateLocalConfigService;
  @Inject private SecretManagementDelegateService secretManagementDelegateService;

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
      ScriptProcessExecutor executor = shellExecutorFactory.getExecutor(
          parameters.processExecutorConfig(containerDeploymentDelegateHelper, encryptionService),
          parameters.isSaveExecutionLogs());
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
          BaseScriptExecutor executor =
              sshExecutorFactory.getExecutor(expectedSshConfig, parameters.isSaveExecutionLogs());
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
          applyTimeoutOnSessionConfig(timeoutInMillis, winRmSessionConfig);
          WinRmExecutor executor =
              winrmExecutorFactory.getExecutor(winRmSessionConfig, parameters.isDisableWinRMCommandEncodingFFSet(),
                  parameters.isSaveExecutionLogs(), parameters.isWinrmScriptCommandSplit());
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

  @VisibleForTesting
  void applyTimeoutOnSessionConfig(Long timeoutInMillis, WinRmSessionConfig config) {
    if (timeoutInMillis != null) {
      config.setTimeout((int) Long.min(timeoutInMillis, Integer.MAX_VALUE));
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
