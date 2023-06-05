/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_ARTIFACT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_ARTIFACT_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_HINT;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.NO_DESTINATION_DOWNLOAD_ARTIFACT_PATH_SPECIFIED;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.NO_DESTINATION_DOWNLOAD_ARTIFACT_PATH_SPECIFIED_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.NO_DESTINATION_DOWNLOAD_PATH_SPECIFIED_HINT;
import static io.harness.delegate.utils.AzureArtifactsUtils.getAzureArtifactDelegateConfig;
import static io.harness.delegate.utils.NexusUtils.getNexusArtifactFileName;
import static io.harness.delegate.utils.NexusUtils.getNexusVersion;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.azure.artifact.AzureArtifactsHelper;
import io.harness.delegate.task.shell.ssh.CommandHandler;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.NgDownloadArtifactCommandUnit;
import io.harness.delegate.task.ssh.artifact.AzureArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.CustomArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.NexusArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SkipCopyArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactType;
import io.harness.delegate.task.winrm.ArtifactDownloadHandler;
import io.harness.delegate.utils.ArtifactoryUtils;
import io.harness.delegate.utils.NexusVersion;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.WinRmCommandExecutionException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.shell.BaseScriptExecutor;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
@Singleton
public abstract class AbstractDownloadArtifactCommandHandler implements CommandHandler {
  @Inject private Map<SshWinRmArtifactType, ArtifactDownloadHandler> artifactHandlers;
  @Inject private AzureArtifactsHelper azureArtifactsHelper;

  @Override
  public ExecuteCommandResponse handle(CommandTaskParameters parameters, NgCommandUnit commandUnit,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress,
      Map<String, Object> taskContext) {
    if (!(commandUnit instanceof NgDownloadArtifactCommandUnit)) {
      throw new InvalidRequestException("Invalid command unit specified for command task.");
    }

    BaseScriptExecutor executor =
        getExecutor(parameters, commandUnit, logStreamingTaskClient, commandUnitsProgress, taskContext);
    LogCallback logCallback = executor.getLogCallback();
    CommandExecutionStatus commandExecutionStatus = downloadArtifact(parameters, logCallback, commandUnit, executor);

    if (FAILURE == commandExecutionStatus) {
      logCallback.saveExecutionLog("Failed to download artifact.", ERROR, commandExecutionStatus);
    }
    logCallback.saveExecutionLog(
        "Command execution finished with status " + commandExecutionStatus, INFO, commandExecutionStatus);

    log.info("Download artifact command execution returned status: {}", commandExecutionStatus);
    return ExecuteCommandResponse.builder().status(commandExecutionStatus).build();
  }

  private CommandExecutionStatus downloadArtifact(CommandTaskParameters commandTaskParameters, LogCallback logCallback,
      NgCommandUnit commandUnit, BaseScriptExecutor executor) {
    log.info("About to download artifact");
    SshWinRmArtifactDelegateConfig artifactDelegateConfig = commandTaskParameters.getArtifactDelegateConfig();
    if (artifactDelegateConfig instanceof SkipCopyArtifactDelegateConfig) {
      throw NestedExceptionUtils.hintWithExplanationException(DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_HINT,
          format(DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_ARTIFACT_EXPLANATION, artifactDelegateConfig.getArtifactType()),
          new WinRmCommandExecutionException(
              format(DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_ARTIFACT, artifactDelegateConfig.getArtifactType())));
    }

    if (artifactDelegateConfig instanceof CustomArtifactDelegateConfig) {
      throw NestedExceptionUtils.hintWithExplanationException(DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_HINT,
          DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_EXPLANATION,
          new WinRmCommandExecutionException(DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT));
    }

    if (artifactDelegateConfig == null) {
      throw new InvalidRequestException("Artifact delegate config not found.");
    }

    logCallback.saveExecutionLog(format("Begin execution of command: %s", commandUnit.getName()), INFO);
    logCallback.saveExecutionLog("Downloading artifact from " + getArtifactType(artifactDelegateConfig) + " to "
            + commandUnit.getDestinationPath() + ((ScriptType.BASH == getScriptType()) ? "/" : "\\")
            + getArtifactFileName(artifactDelegateConfig),
        INFO);

    if (isEmpty(commandUnit.getDestinationPath())) {
      log.info("Destination path not provided for download command unit");
      throw NestedExceptionUtils.hintWithExplanationException(
          format(NO_DESTINATION_DOWNLOAD_PATH_SPECIFIED_HINT, commandUnit.getName()),
          format(NO_DESTINATION_DOWNLOAD_ARTIFACT_PATH_SPECIFIED_EXPLANATION, commandUnit.getName()),
          new WinRmCommandExecutionException(NO_DESTINATION_DOWNLOAD_ARTIFACT_PATH_SPECIFIED));
    }

    if (isEmpty(artifactDelegateConfig.getArtifactPath())) {
      logCallback.saveExecutionLog("artifactPath or artifactPathFilter is blank", ERROR, FAILURE);
    }

    try {
      String command = getCommandString(commandUnit, artifactDelegateConfig);
      return executor.executeCommandString(command);
    } catch (Exception e) {
      return FAILURE;
    }
  }

  private String getCommandString(NgCommandUnit commandUnit, SshWinRmArtifactDelegateConfig artifactDelegateConfig) {
    String command;
    try {
      ArtifactDownloadHandler artifactHandler = artifactHandlers.get(artifactDelegateConfig.getArtifactType());
      if (artifactHandler == null) {
        log.warn("Wrong artifact delegate config submitted: {}", artifactDelegateConfig.getArtifactType());
        throw new InvalidRequestException(
            format("%s artifact type not supported.", artifactDelegateConfig.getArtifactType()));
      }
      command =
          artifactHandler.getCommandString(artifactDelegateConfig, commandUnit.getDestinationPath(), getScriptType());
    } catch (Exception e) {
      log.error("Cannot get command string for download artifact.", e);
      throw new RuntimeException("Cannot get command string for download artifact");
    }
    return command;
  }

  public String getArtifactType(SshWinRmArtifactDelegateConfig artifactDelegateConfig) {
    SshWinRmArtifactType artifactType = artifactDelegateConfig.getArtifactType();
    if (SshWinRmArtifactType.NEXUS_PACKAGE == artifactType) {
      return getNexusVersion((NexusArtifactDelegateConfig) artifactDelegateConfig).name();
    }

    return artifactType.name();
  }

  public String getArtifactFileName(SshWinRmArtifactDelegateConfig artifactDelegateConfig) {
    if (artifactDelegateConfig instanceof NexusArtifactDelegateConfig) {
      NexusArtifactDelegateConfig nexusArtifactDelegateConfig = (NexusArtifactDelegateConfig) artifactDelegateConfig;
      NexusVersion nexusVersion = getNexusVersion(nexusArtifactDelegateConfig);
      return getNexusArtifactFileName(
          nexusVersion, nexusArtifactDelegateConfig.getRepositoryFormat(), nexusArtifactDelegateConfig.getMetadata());
    } else if (artifactDelegateConfig instanceof AzureArtifactDelegateConfig) {
      return azureArtifactsHelper.getArtifactFileName(getAzureArtifactDelegateConfig(artifactDelegateConfig));
    }

    return ArtifactoryUtils.getArtifactFileName(artifactDelegateConfig.getArtifactPath());
  }

  public abstract BaseScriptExecutor getExecutor(CommandTaskParameters commandTaskParameters, NgCommandUnit commandUnit,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress,
      Map<String, Object> taskContext);
  public abstract ScriptType getScriptType();
}
