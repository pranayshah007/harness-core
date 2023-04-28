/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.shell.winrm.WinRmUtils.getWinRmSessionConfig;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.COPY_ARTIFACT_NOT_SUPPORTED_FOR_WINRM;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.COPY_ARTIFACT_NOT_SUPPORTED_FOR_WINRM_HINT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.FAILED_TO_COPY_CONFIG_FILE;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.FAILED_TO_COPY_CONFIG_FILE_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.FAILED_TO_COPY_WINRM_CONFIG_FILE_HINT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.INVALID_STORE_DELEGATE_CONFIG_TYPE_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.INVALID_STORE_DELEGATE_CONFIG_TYPE_FAILED;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.INVALID_STORE_DELEGATE_CONFIG_TYPE_HINT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.NO_DESTINATION_PATH_SPECIFIED;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.NO_DESTINATION_PATH_SPECIFIED_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.NO_DESTINATION_PATH_SPECIFIED_HINT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.UNDECRYPTABLE_CONFIG_FILE_PROVIDED;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.UNDECRYPTABLE_CONFIG_FILE_PROVIDED_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.UNDECRYPTABLE_CONFIG_FILE_PROVIDED_HINT;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.storeconfig.GitFetchedStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HarnessStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfigType;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.shell.ssh.CommandHandler;
import io.harness.delegate.task.ssh.CopyCommandUnit;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.delegate.task.ssh.config.SecretConfigFile;
import io.harness.delegate.task.winrm.FileBasedWinRmExecutorNG;
import io.harness.delegate.task.winrm.WinRmExecutorFactoryNG;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.runtime.SshCommandExecutionException;
import io.harness.exception.runtime.WinRmCommandExecutionException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.ssh.FileSourceType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
@Singleton
public class WinRmCopyCommandHandler extends WinRmDownloadArtifactCommandHandler implements CommandHandler {
  @Inject private WinRmExecutorFactoryNG winRmExecutorFactoryNG;
  @Inject private WinRmConfigAuthEnhancer winRmConfigAuthEnhancer;
  @Inject private SecretDecryptionService secretDecryptionService;

  @Override
  public ExecuteCommandResponse handle(CommandTaskParameters parameters, NgCommandUnit commandUnit,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress,
      Map<String, Object> taskContext) {
    if (!(parameters instanceof WinrmTaskParameters)) {
      throw new InvalidRequestException("Invalid task parameters submitted for command task.");
    }
    WinrmTaskParameters winRmCommandTaskParameters = (WinrmTaskParameters) parameters;
    if (!(commandUnit instanceof CopyCommandUnit)) {
      throw new InvalidRequestException("Invalid command unit specified for command task.");
    }

    CopyCommandUnit copyCommandUnit = (CopyCommandUnit) commandUnit;

    if (EmptyPredicate.isEmpty(copyCommandUnit.getDestinationPath())) {
      log.info("Destination path no provided for copy command unit");
      throw NestedExceptionUtils.hintWithExplanationException(
          format(NO_DESTINATION_PATH_SPECIFIED_HINT, copyCommandUnit.getName()),
          format(NO_DESTINATION_PATH_SPECIFIED_EXPLANATION, copyCommandUnit.getName()),
          new WinRmCommandExecutionException(NO_DESTINATION_PATH_SPECIFIED));
    }

    CommandExecutionStatus commandExecutionStatus = CommandExecutionStatus.SUCCESS;
    if (FileSourceType.ARTIFACT.equals(copyCommandUnit.getSourceType())) {
      throw NestedExceptionUtils.hintWithExplanationException(COPY_ARTIFACT_NOT_SUPPORTED_FOR_WINRM_HINT,
          COPY_ARTIFACT_NOT_SUPPORTED_FOR_WINRM,
          new SshCommandExecutionException(COPY_ARTIFACT_NOT_SUPPORTED_FOR_WINRM));
    } else if (FileSourceType.CONFIG.equals(copyCommandUnit.getSourceType())) {
      commandExecutionStatus =
          copyConfigFiles(winRmCommandTaskParameters, copyCommandUnit, logStreamingTaskClient, commandUnitsProgress);
    }

    return ExecuteCommandResponse.builder().status(commandExecutionStatus).build();
  }

  private CommandExecutionStatus copyConfigFiles(WinrmTaskParameters winRmCommandTaskParameters,
      CopyCommandUnit copyCommandUnit, ILogStreamingTaskClient logStreamingTaskClient,
      CommandUnitsProgress commandUnitsProgress) {
    WinRmSessionConfig config =
        getWinRmSessionConfig(copyCommandUnit, winRmCommandTaskParameters, winRmConfigAuthEnhancer);
    FileBasedWinRmExecutorNG executor = winRmExecutorFactoryNG.getFiledBasedWinRmExecutor(config,
        winRmCommandTaskParameters.isDisableWinRMCommandEncodingFFSet(), logStreamingTaskClient, commandUnitsProgress);
    CommandExecutionStatus result = CommandExecutionStatus.SUCCESS;
    List<ConfigFileParameters> configFiles = getConfigFileParameters(winRmCommandTaskParameters);
    executor.saveExecutionLog(format("Begin execution of command: %s", copyCommandUnit.getName()), INFO, RUNNING);
    for (ConfigFileParameters configFile : configFiles) {
      log.info(format("Copying config file : %s, isEncrypted: %b", configFile.getFileName(), configFile.isEncrypted()));
      if (configFile.isEncrypted()) {
        SecretConfigFile secretConfigFile;
        try {
          secretConfigFile = (SecretConfigFile) secretDecryptionService.decrypt(
              configFile.getSecretConfigFile(), configFile.getEncryptionDataDetails());
        } catch (Exception e) {
          throw NestedExceptionUtils.hintWithExplanationException(
              format(UNDECRYPTABLE_CONFIG_FILE_PROVIDED_HINT, configFile.getFileName()),
              format(UNDECRYPTABLE_CONFIG_FILE_PROVIDED_EXPLANATION, configFile.getFileName()),
              new SshCommandExecutionException(format(UNDECRYPTABLE_CONFIG_FILE_PROVIDED, configFile.getFileName())));
        }

        String fileData = new String(secretConfigFile.getEncryptedConfigFile().getDecryptedValue());
        configFile.setFileContent(fileData);
      }
      configFile.setDestinationPath(copyCommandUnit.getDestinationPath());
      configFile.calculateFileSize();

      result = executor.copyConfigFiles(configFile);
      if (CommandExecutionStatus.FAILURE.equals(result)) {
        log.info("Failed to copy config file: " + configFile.getFileName());
        executor.saveExecutionLog("Command execution finished with status " + result, LogLevel.INFO, result);
        throw NestedExceptionUtils.hintWithExplanationException(FAILED_TO_COPY_WINRM_CONFIG_FILE_HINT,
            format(FAILED_TO_COPY_CONFIG_FILE_EXPLANATION, configFile.getFileName(), configFile.getDestinationPath()),
            new SshCommandExecutionException(format(FAILED_TO_COPY_CONFIG_FILE, configFile.getFileName())));
      }
    }
    executor.saveExecutionLog("Command execution finished with status " + result, LogLevel.INFO, result);
    return result;
  }

  private List<ConfigFileParameters> getConfigFileParameters(WinrmTaskParameters winrmTaskParameters) {
    if (winrmTaskParameters.getFileDelegateConfig() == null) {
      return Collections.emptyList();
    }

    List<ConfigFileParameters> configFiles = new ArrayList<>();
    for (StoreDelegateConfig storeDelegateConfig : winrmTaskParameters.getFileDelegateConfig().getStores()) {
      if (storeDelegateConfig.getType() == null) {
        throw generateExceptionForInvalidStoreDelegateConfig(null);
      }

      switch (storeDelegateConfig.getType()) {
        case HARNESS:
          HarnessStoreDelegateConfig harnessStoreDelegateConfig = (HarnessStoreDelegateConfig) storeDelegateConfig;
          configFiles.addAll(harnessStoreDelegateConfig.getConfigFiles());
          break;
        case GIT_FETCHED:
          GitFetchedStoreDelegateConfig fetchedStoreDelegateConfig =
              (GitFetchedStoreDelegateConfig) storeDelegateConfig;
          configFiles.addAll(fetchedStoreDelegateConfig.getConfigFiles());
          break;
        default:
          throw generateExceptionForInvalidStoreDelegateConfig(storeDelegateConfig.getType());
      }
    }

    return configFiles;
  }

  private WingsException generateExceptionForInvalidStoreDelegateConfig(StoreDelegateConfigType type) {
    return NestedExceptionUtils.hintWithExplanationException(INVALID_STORE_DELEGATE_CONFIG_TYPE_HINT,
        format(INVALID_STORE_DELEGATE_CONFIG_TYPE_EXPLANATION, type),
        new SshCommandExecutionException(INVALID_STORE_DELEGATE_CONFIG_TYPE_FAILED));
  }
}
