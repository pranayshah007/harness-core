/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.ARTIFACT_CONFIGURATION_NOT_FOUND;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.ARTIFACT_CONFIGURATION_NOT_FOUND_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.ARTIFACT_CONFIGURATION_NOT_FOUND_HINT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.COPY_AND_DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_GITHUB_PACKAGE_ARTIFACT_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.COPY_AND_DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_GITHUB_PACKAGE_ARTIFACT_FAILED;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.COPY_AND_DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_GITHUB_PACKAGE_ARTIFACT_HINT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.COPY_ARTIFACT_NOT_SUPPORTED_FOR_AZURE_UNIVERSAL_PACKAGE_ARTIFACT_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.COPY_ARTIFACT_NOT_SUPPORTED_FOR_AZURE_UNIVERSAL_PACKAGE_ARTIFACT_FAILED;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.COPY_ARTIFACT_NOT_SUPPORTED_FOR_AZURE_UNIVERSAL_PACKAGE_ARTIFACT_HINT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.COPY_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.COPY_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.COPY_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_HINT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.FAILED_TO_COPY_ARTIFACT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.FAILED_TO_COPY_ARTIFACT_HINT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.FAILED_TO_COPY_ARTIFACT_HINT_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.FAILED_TO_COPY_CONFIG_FILE;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.FAILED_TO_COPY_CONFIG_FILE_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.FAILED_TO_COPY_SSH_CONFIG_FILE_HINT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.INVALID_STORE_DELEGATE_CONFIG_TYPE_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.INVALID_STORE_DELEGATE_CONFIG_TYPE_FAILED;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.INVALID_STORE_DELEGATE_CONFIG_TYPE_HINT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.NO_DESTINATION_PATH_SPECIFIED;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.NO_DESTINATION_PATH_SPECIFIED_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.NO_DESTINATION_PATH_SPECIFIED_HINT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.UNDECRYPTABLE_CONFIG_FILE_PROVIDED;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.UNDECRYPTABLE_CONFIG_FILE_PROVIDED_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.UNDECRYPTABLE_CONFIG_FILE_PROVIDED_HINT;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.azureartifacts.beans.AzureArtifactsProtocolType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.storeconfig.GitFetchedStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HarnessStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfigType;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.shell.FileBasedAbstractScriptExecutorNG;
import io.harness.delegate.task.shell.SshCommandTaskParameters;
import io.harness.delegate.task.ssh.CopyCommandUnit;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.artifact.AzureArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.CustomArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.GithubPackagesArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SkipCopyArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.delegate.task.ssh.config.SecretConfigFile;
import io.harness.delegate.utils.GithubPackageUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.runtime.SshCommandExecutionException;
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
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class SshCopyCommandHandler implements CommandHandler {
  @Inject private SshScriptExecutorFactory sshScriptExecutorFactory;
  @Inject private SecretDecryptionService secretDecryptionService;

  @Override
  public ExecuteCommandResponse handle(CommandTaskParameters parameters, NgCommandUnit commandUnit,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress,
      Map<String, Object> taskContext) {
    if (!(parameters instanceof SshCommandTaskParameters)) {
      throw new InvalidRequestException("Invalid task parameters submitted for command task.");
    }

    if (!(commandUnit instanceof CopyCommandUnit)) {
      throw new InvalidRequestException("Invalid command unit specified for command task.");
    }
    CopyCommandUnit copyCommandUnit = (CopyCommandUnit) commandUnit;
    SshCommandTaskParameters sshCommandTaskParameters = (SshCommandTaskParameters) parameters;

    SshExecutorFactoryContext context =
        SshExecutorFactoryContext.builder()
            .accountId(sshCommandTaskParameters.getAccountId())
            .executionId(sshCommandTaskParameters.getExecutionId())
            .workingDirectory(commandUnit.getWorkingDirectory())
            .commandUnitName(commandUnit.getName())
            .commandUnitsProgress(commandUnitsProgress)
            .environment(sshCommandTaskParameters.getEnvironmentVariables())
            .encryptedDataDetailList(sshCommandTaskParameters.getSshInfraDelegateConfig().getEncryptionDataDetails())
            .sshKeySpecDTO(sshCommandTaskParameters.getSshInfraDelegateConfig().getSshKeySpecDto())
            .iLogStreamingTaskClient(logStreamingTaskClient)
            .executeOnDelegate(sshCommandTaskParameters.isExecuteOnDelegate())
            .host(sshCommandTaskParameters.getHost())
            .artifactDelegateConfig(sshCommandTaskParameters.getArtifactDelegateConfig())
            .destinationPath(copyCommandUnit.getDestinationPath())
            .build();

    context.getEnvironmentVariables().putAll((Map<String, String>) taskContext.get(RESOLVED_ENV_VARIABLES_KEY));

    if (EmptyPredicate.isEmpty(copyCommandUnit.getDestinationPath())) {
      log.info("Destination path no provided for copy command unit");
      throw NestedExceptionUtils.hintWithExplanationException(
          format(NO_DESTINATION_PATH_SPECIFIED_HINT, copyCommandUnit.getName()),
          format(NO_DESTINATION_PATH_SPECIFIED_EXPLANATION, copyCommandUnit.getName()),
          new SshCommandExecutionException(NO_DESTINATION_PATH_SPECIFIED));
    }

    CommandExecutionStatus result = CommandExecutionStatus.SUCCESS;
    FileBasedAbstractScriptExecutorNG executor =
        (FileBasedAbstractScriptExecutorNG) sshScriptExecutorFactory.getFileBasedExecutor(context);
    if (FileSourceType.ARTIFACT.equals(copyCommandUnit.getSourceType())) {
      log.info("About to copy artifact");

      verifyIfCopyArtifactIsSupported(sshCommandTaskParameters.getArtifactDelegateConfig());

      if (context.getArtifactDelegateConfig() instanceof SkipCopyArtifactDelegateConfig) {
        log.info(
            "Docker {} registry found, skipping copy artifact.", context.getArtifactDelegateConfig().getArtifactType());
        executor.getLogCallback().saveExecutionLog("Command finished with status " + result, LogLevel.INFO, result);
        return ExecuteCommandResponse.builder().status(result).build();
      }

      result = executor.copyFiles(context);
      executor.getLogCallback().saveExecutionLog("Command finished with status " + result, LogLevel.INFO, result);
      if (result == CommandExecutionStatus.FAILURE) {
        String artifactId = sshCommandTaskParameters.getArtifactDelegateConfig().getIdentifier();
        log.error("Failed to copy artifact with id: " + artifactId);
        throw NestedExceptionUtils.hintWithExplanationException(FAILED_TO_COPY_ARTIFACT_HINT,
            format(FAILED_TO_COPY_ARTIFACT_HINT_EXPLANATION, artifactId, copyCommandUnit.getDestinationPath()),
            new SshCommandExecutionException(format(FAILED_TO_COPY_ARTIFACT, artifactId)));
      }
      return ExecuteCommandResponse.builder().status(result).build();
    }

    if (FileSourceType.CONFIG.equals(copyCommandUnit.getSourceType())) {
      List<ConfigFileParameters> configFiles = getConfigFileParameters(sshCommandTaskParameters, copyCommandUnit);
      log.info(format("About to copy config %s files", configFiles.size()));
      for (ConfigFileParameters configFile : configFiles) {
        log.info(format("Copy config file : %s, isEncrypted: %b", configFile.getFileName(), configFile.isEncrypted()));
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

        // Since file content might change after secret manager functor will be applied,
        // there is no way to handle this on manager side and we need to recalculate file
        // size before sending over the wire.
        // This is applicable mostly in use cases when config file has a reference to secret variable in its content.
        configFile.calculateFileSize();

        result = executor.copyConfigFiles(context.getEvaluatedDestinationPath(), configFile);
        if (result == CommandExecutionStatus.FAILURE) {
          log.error("Failed to copy config file: " + configFile.getFileName());
          executor.getLogCallback().saveExecutionLog("Command finished with status " + result, LogLevel.INFO, result);
          throw NestedExceptionUtils.hintWithExplanationException(FAILED_TO_COPY_SSH_CONFIG_FILE_HINT,
              format(FAILED_TO_COPY_CONFIG_FILE_EXPLANATION, configFile.getFileName(), configFile.getDestinationPath()),
              new SshCommandExecutionException(format(FAILED_TO_COPY_CONFIG_FILE, configFile.getFileName())));
        }
      }
      executor.getLogCallback().saveExecutionLog("Command finished with status " + result, LogLevel.INFO, result);
    }

    return ExecuteCommandResponse.builder().status(result).build();
  }

  private void verifyIfCopyArtifactIsSupported(SshWinRmArtifactDelegateConfig artifactDelegateConfig) {
    if (artifactDelegateConfig == null) {
      throw NestedExceptionUtils.hintWithExplanationException(ARTIFACT_CONFIGURATION_NOT_FOUND_HINT,
          ARTIFACT_CONFIGURATION_NOT_FOUND_EXPLANATION,
          new SshCommandExecutionException(ARTIFACT_CONFIGURATION_NOT_FOUND));
    }

    if (artifactDelegateConfig instanceof CustomArtifactDelegateConfig) {
      throw NestedExceptionUtils.hintWithExplanationException(COPY_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_HINT,
          COPY_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_EXPLANATION,
          new SshCommandExecutionException(COPY_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT));
    }

    if (artifactDelegateConfig instanceof GithubPackagesArtifactDelegateConfig) {
      GithubPackagesArtifactDelegateConfig githubPackagesArtifactDelegateConfig =
          (GithubPackagesArtifactDelegateConfig) artifactDelegateConfig;
      if (!GithubPackageUtils.MAVEN_PACKAGE_TYPE.equals(githubPackagesArtifactDelegateConfig.getPackageType())) {
        throw NestedExceptionUtils.hintWithExplanationException(
            COPY_AND_DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_GITHUB_PACKAGE_ARTIFACT_HINT,
            format(COPY_AND_DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_GITHUB_PACKAGE_ARTIFACT_EXPLANATION,
                githubPackagesArtifactDelegateConfig.getPackageType()),
            new SshCommandExecutionException(
                format(COPY_AND_DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_GITHUB_PACKAGE_ARTIFACT_FAILED,
                    githubPackagesArtifactDelegateConfig.getPackageType())));
      }
    }

    if (artifactDelegateConfig instanceof AzureArtifactDelegateConfig) {
      AzureArtifactDelegateConfig azureArtifactDelegateConfig = (AzureArtifactDelegateConfig) artifactDelegateConfig;
      if (AzureArtifactsProtocolType.upack.name().equals(azureArtifactDelegateConfig.getPackageType())) {
        throw NestedExceptionUtils.hintWithExplanationException(
            COPY_ARTIFACT_NOT_SUPPORTED_FOR_AZURE_UNIVERSAL_PACKAGE_ARTIFACT_HINT,
            COPY_ARTIFACT_NOT_SUPPORTED_FOR_AZURE_UNIVERSAL_PACKAGE_ARTIFACT_EXPLANATION,
            new SshCommandExecutionException(COPY_ARTIFACT_NOT_SUPPORTED_FOR_AZURE_UNIVERSAL_PACKAGE_ARTIFACT_FAILED));
      }
    }
  }

  private List<ConfigFileParameters> getConfigFileParameters(
      SshCommandTaskParameters sshCommandTaskParameters, CopyCommandUnit copyCommandUnit) {
    if (sshCommandTaskParameters.getFileDelegateConfig() == null) {
      return Collections.emptyList();
    }

    List<ConfigFileParameters> configFiles = new ArrayList<>();
    for (StoreDelegateConfig storeDelegateConfig : sshCommandTaskParameters.getFileDelegateConfig().getStores()) {
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
