/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.delegate.task.shell.ArtifactoryUtils.getArtifactConfigRequest;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.winrm.WinRmHelperUtils.buildErrorDetailsFromWinRmClientException;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.shell.ConfigFileMetaData;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.ssh.CopyCommandUnit;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.utils.ExecutionLogWriter;

import com.google.common.collect.Lists;
import com.google.common.primitives.Bytes;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(CDP)
@Slf4j
public class FileBasedWinRmExecutorNG extends FileBasedAbstractWinRmExecutor {
  private final SecretDecryptionService secretDecryptionService;
  private final ArtifactoryRequestMapper artifactoryRequestMapper;

  public FileBasedWinRmExecutorNG(LogCallback logCallback, boolean shouldSaveExecutionLogs, WinRmSessionConfig config,
      boolean disableCommandEncoding, SecretDecryptionService secretDecryptionService,
      ArtifactoryRequestMapper artifactoryRequestMapper) {
    super(logCallback, shouldSaveExecutionLogs, config, disableCommandEncoding);
    this.secretDecryptionService = secretDecryptionService;
    this.artifactoryRequestMapper = artifactoryRequestMapper;
  }

  @Override
  public byte[] getConfigFileBytes(ConfigFileMetaData configFileMetaData) {
    // no need for the implementation for NG
    throw new NotImplementedException("Not implemented");
  }

  public CommandExecutionStatus copyArtifacts(WinrmTaskParameters taskParameters, CopyCommandUnit copyCommandUnit) {
    SshWinRmArtifactDelegateConfig artifactDelegateConfig = taskParameters.getArtifactDelegateConfig();
    if (artifactDelegateConfig == null) {
      throw new InvalidRequestException("Artifact delegate config not found.");
    }

    if (!(artifactDelegateConfig instanceof ArtifactoryArtifactDelegateConfig)) {
      log.warn("Wrong artifact delegate config submitted");
      throw new InvalidRequestException("Expecting artifactory delegate config");
    }

    ArtifactoryArtifactDelegateConfig artifactoryArtifactDelegateConfig =
        (ArtifactoryArtifactDelegateConfig) artifactDelegateConfig;

    ArtifactoryConfigRequest artifactoryConfigRequest = getArtifactConfigRequest(
        artifactoryArtifactDelegateConfig, logCallback, secretDecryptionService, artifactoryRequestMapper);

    CommandExecutionStatus commandExecutionStatus = FAILURE;

    try (WinRmSession session = new WinRmSession(config, this.logCallback);
         ExecutionLogWriter outputWriter = getExecutionLogWriter(INFO);
         ExecutionLogWriter errorWriter = getExecutionLogWriter(ERROR)) {
      saveExecutionLog(format("Connected to %s", config.getHostname()), INFO);
      saveExecutionLog(format("Executing command ...%n"), INFO);

      String artifactPath = Paths
                                .get(artifactoryArtifactDelegateConfig.getRepositoryName(),
                                    artifactoryArtifactDelegateConfig.getArtifactPath())
                                .toString();
      clearTargetArtifact(copyCommandUnit.getDestinationPath(), artifactPath, session, outputWriter, errorWriter);

      String command =
          getDownloadArtifactCommand(artifactoryConfigRequest, copyCommandUnit.getDestinationPath(), artifactPath);
      commandExecutionStatus = executeRemoteCommand(session, outputWriter, errorWriter, command, true);
      saveExecutionLog("Command completed successfully", INFO, commandExecutionStatus);
      if (FAILURE == commandExecutionStatus) {
        saveExecutionLog("Failed to copy artifact.", ERROR, RUNNING);
        return commandExecutionStatus;
      }
    } catch (Exception e) {
      log.error(ERROR_WHILE_EXECUTING_COMMAND, e);
      ResponseMessage details = buildErrorDetailsFromWinRmClientException(e);
      saveExecutionLog(
          format("Command execution failed. Error: %s", details.getMessage()), ERROR, commandExecutionStatus);
    }

    log.info("Copy Config command execution returned status: {}", commandExecutionStatus);
    return commandExecutionStatus;
  }

  public CommandExecutionStatus copyConfigFiles(ConfigFileParameters configFileParameters) {
    if (isBlank(configFileParameters.getFileName())) {
      saveExecutionLog("There is no config file to copy. " + configFileParameters, INFO);
      return CommandExecutionStatus.SUCCESS;
    }

    CommandExecutionStatus commandExecutionStatus = FAILURE;

    try (WinRmSession session = new WinRmSession(config, this.logCallback);
         ExecutionLogWriter outputWriter = getExecutionLogWriter(INFO);
         ExecutionLogWriter errorWriter = getExecutionLogWriter(ERROR)) {
      saveExecutionLog(format("Connected to %s", config.getHostname()), INFO);
      saveExecutionLog(format("Executing command ...%n"), INFO);

      commandExecutionStatus = splitFileAndTransfer(configFileParameters, session, outputWriter, errorWriter);
      saveExecutionLog("Command completed successfully", INFO, commandExecutionStatus);

    } catch (Exception e) {
      log.error(ERROR_WHILE_EXECUTING_COMMAND, e);
      ResponseMessage details = buildErrorDetailsFromWinRmClientException(e);
      saveExecutionLog(
          format("Command execution failed. Error: %s", details.getMessage()), ERROR, commandExecutionStatus);
    }

    log.info("Copy Config command execution returned status: {}", commandExecutionStatus);
    return commandExecutionStatus;
  }

  private CommandExecutionStatus splitFileAndTransfer(ConfigFileParameters configFileParameters, WinRmSession session,
      ExecutionLogWriter outputWriter, ExecutionLogWriter errorWriter) throws IOException {
    final List<List<Byte>> partitions =
        Lists.partition(Bytes.asList(configFileParameters.getFileContent().getBytes()), BATCH_SIZE_BYTES);
    clearTargetFile(session, outputWriter, errorWriter, configFileParameters.getDestinationPath(),
        configFileParameters.getFileName());
    logFileSizeAndOtherMetadata(
        configFileParameters.getFileSize(), partitions.size(), configFileParameters.getFileName());

    int chunkNumber = 1;
    for (List<Byte> partition : partitions) {
      final byte[] bytesToCopy = Bytes.toArray(partition);
      String command = getCopyConfigCommand(
          bytesToCopy, configFileParameters.getDestinationPath(), configFileParameters.getFileName());
      CommandExecutionStatus commandExecutionStatus =
          executeRemoteCommand(session, outputWriter, errorWriter, command, true);
      if (FAILURE == commandExecutionStatus) {
        saveExecutionLog(format("Failed to copy chunk #%d. Discontinuing", chunkNumber), ERROR, RUNNING);
        return commandExecutionStatus;
      }
      saveExecutionLog(format("Transferred %s data for config file...\n",
                           calcPercentage(chunkNumber * BATCH_SIZE_BYTES, configFileParameters.getFileSize())),
          INFO, RUNNING);
      chunkNumber++;
    }
    return SUCCESS;
  }

  protected void clearTargetArtifact(String destinationPath, String artifactPath, WinRmSession session,
      ExecutionLogWriter outputWriter, ExecutionLogWriter errorWriter) throws IOException {
    String command = getDeleteArtifactCommandStr(destinationPath, artifactPath);
    final CommandExecutionStatus status = executeRemoteCommand(session, outputWriter, errorWriter, command, false);
    if (status != SUCCESS) {
      final String message =
          format("File %s could not cleared before writing", Paths.get(destinationPath, artifactPath));
      saveExecutionLog(message, ERROR, FAILURE);
      throw new InvalidRequestException(message, USER);
    }
  }

  private String getDownloadArtifactCommand(
      ArtifactoryConfigRequest artifactoryConfigRequest, String destinationPath, String artifactPath) {
    if (artifactoryConfigRequest.isHasCredentials()) {
      return "$Headers = @{\n"
          + "    Authorization = \"" + getAuthHeader(artifactoryConfigRequest) + "\"\n"
          + "}\n [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12"
          + "\n $ProgressPreference = 'SilentlyContinue'"
          + "\n Invoke-WebRequest -Uri \"" + getArtifactoryUrl(artifactoryConfigRequest, artifactPath)
          + "\" -Headers $Headers -OutFile \"" + destinationPath + "\\" + artifactPath + "\"";
    } else {
      return "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12\n "
          + "$ProgressPreference = 'SilentlyContinue'\n"
          + "Invoke-WebRequest -Uri \"" + getArtifactoryUrl(artifactoryConfigRequest, artifactPath) + "\" -OutFile \""
          + destinationPath + "\\" + artifactPath + "\"";
    }
  }

  private String getAuthHeader(ArtifactoryConfigRequest artifactoryConfigRequest) {
    String authHeader = null;
    if (artifactoryConfigRequest.isHasCredentials()) {
      String pair = artifactoryConfigRequest.getUsername() + ":" + new String(artifactoryConfigRequest.getPassword());
      authHeader = "Basic " + encodeBase64(pair);
    }
    return authHeader;
  }

  private String getArtifactoryUrl(ArtifactoryConfigRequest artifactoryConfigRequest, String artifactPath) {
    String url = artifactoryConfigRequest.getArtifactoryUrl().trim();
    if (!url.endsWith("/")) {
      url += "/";
    }
    return url + artifactPath;
  }

  private String getDeleteArtifactCommandStr(String destinationPath, String artifactPath) {
    return "$artifact = '" + destinationPath + "\\" + artifactPath + "'\n"
        + "Write-Host \"Clearing target artifact $artifact on the host.\"\n"
        + "[IO.File]::Delete($artifact)";
  }
}