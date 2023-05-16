/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.winrm.WinRmHelperUtils.buildErrorDetailsFromWinRmClientException;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.shell.ConfigFileMetaData;
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.eraro.ResponseMessage;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.utils.ExecutionLogWriter;

import com.google.common.primitives.Bytes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(CDP)
@Slf4j
public class FileBasedWinRmExecutorNG extends FileBasedAbstractWinRmExecutor {
  private static final String NEW_LINE = "\n";
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
      saveExecutionLog(
          format("Executing copy config files command...\nConfig filename: %s", configFileParameters.getFileName()),
          INFO);

      commandExecutionStatus = splitFileAndTransfer(configFileParameters, session, outputWriter, errorWriter);
      saveExecutionLog("Command completed successfully", INFO);

    } catch (Exception e) {
      log.error(ERROR_WHILE_EXECUTING_COMMAND, e);
      ResponseMessage details = buildErrorDetailsFromWinRmClientException(e);
      saveExecutionLog(
          format("Command execution failed. Error: %s", details.getMessage()), ERROR, commandExecutionStatus);
    }

    log.info("Copy Config command execution returned status: {}", commandExecutionStatus);
    return commandExecutionStatus;
  }

  CommandExecutionStatus splitFileAndTransfer(ConfigFileParameters configFileParameters, WinRmSession session,
      ExecutionLogWriter outputWriter, ExecutionLogWriter errorWriter) throws IOException {
    final List<List<Byte>> partitions = new ArrayList<>();
    String[] fileContentByLines = configFileParameters.getFileContent().split(NEW_LINE);
    int chunkSize = 0;
    StringBuilder chunkString = new StringBuilder();
    for (int lineNumber = 0; lineNumber < fileContentByLines.length; lineNumber++) {
      String line = fileContentByLines[lineNumber];
      chunkSize += line.getBytes().length;
      chunkString.append(line).append(NEW_LINE);
      if (chunkSize >= BATCH_SIZE_BYTES || lineNumber == (fileContentByLines.length - 1)) {
        partitions.add(Bytes.asList(chunkString.toString().getBytes()));
        if (lineNumber != (fileContentByLines.length - 1)) {
          chunkSize = 0;
          chunkString = new StringBuilder();
        }
      }
    }

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
}