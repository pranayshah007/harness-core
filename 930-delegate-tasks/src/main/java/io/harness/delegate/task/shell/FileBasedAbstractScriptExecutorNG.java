/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.delegate.task.shell.ssh.ArtifactCommandUnitHandler;
import io.harness.delegate.task.shell.ssh.SshExecutorFactoryContext;
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.shell.AbstractScriptExecutor;
import io.harness.shell.SshHelperUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@OwnedBy(CDP)
public abstract class FileBasedAbstractScriptExecutorNG implements FileBasedScriptExecutorNG {
  protected LogCallback logCallback;
  protected boolean shouldSaveExecutionLogs;
  protected Map<String, ArtifactCommandUnitHandler> artifactCommandHandlers;

  public FileBasedAbstractScriptExecutorNG(LogCallback logCallback, boolean shouldSaveExecutionLogs,
      Map<String, ArtifactCommandUnitHandler> artifactCommandHandlers) {
    this.logCallback = logCallback;
    this.shouldSaveExecutionLogs = shouldSaveExecutionLogs;
    this.artifactCommandHandlers = artifactCommandHandlers;
  }

  public abstract CommandExecutionStatus scpOneFile(
      String remoteFilePath, AbstractScriptExecutor.FileProvider fileProvider);

  @Override
  public CommandExecutionStatus copyFiles(SshExecutorFactoryContext context) {
    ArtifactCommandUnitHandler artifactCommandUnitHandler =
        artifactCommandHandlers.get(context.getArtifactDelegateConfig().getArtifactType().name());
    if (artifactCommandUnitHandler == null) {
      throw new InvalidRequestException(format(
          "Unsupported artifact type provided: %s", context.getArtifactDelegateConfig().getArtifactType().name()));
    }
    Map<String, String> metadata = context.getArtifactMetadata();
    return scpOneFile(context.getDestinationPath(), new AbstractScriptExecutor.FileProvider() {
      @Override
      public Pair<String, Long> getInfo() {
        if (!metadata.containsKey(ArtifactMetadataKeys.artifactFileSize)) {
          Long artifactFileSize = artifactCommandUnitHandler.getArtifactSize(context, logCallback);
          metadata.put(ArtifactMetadataKeys.artifactFileSize, String.valueOf(artifactFileSize));
        }
        String fileName = metadata.get(ArtifactMetadataKeys.artifactName);
        int lastIndexOfSlash = fileName.lastIndexOf('/');
        if (lastIndexOfSlash > 0) {
          saveExecutionLogWarn("Filename contains slashes. Stripping off the portion before last slash.");
          log.warn("Filename contains slashes. Stripping off the portion before last slash.");
          fileName = fileName.substring(lastIndexOfSlash + 1);
          saveExecutionLogWarn("Got filename: " + fileName);
          log.warn("Got filename: " + fileName);
        }

        return ImmutablePair.of(fileName, Long.parseLong(metadata.get(ArtifactMetadataKeys.artifactFileSize)));
      }

      @Override
      public void downloadToStream(OutputStream outputStream) throws IOException, ExecutionException {
        try (InputStream inputStream = artifactCommandUnitHandler.downloadToLocal(context, logCallback)) {
          IOUtils.copy(inputStream, outputStream);
        }
      }
    });
  }

  @Override
  public CommandExecutionStatus copyConfigFiles(
      String destinationDirectoryPath, ConfigFileParameters configFileParameters) {
    if (isBlank(configFileParameters.getFileName())) {
      saveExecutionLog("Config file name no provided, aborting. " + configFileParameters);
      return CommandExecutionStatus.SUCCESS;
    }
    return scpOneFile(destinationDirectoryPath, new AbstractScriptExecutor.FileProvider() {
      @Override
      public Pair<String, Long> getInfo() {
        return ImmutablePair.of(configFileParameters.getFileName(), configFileParameters.getFileSize());
      }

      @Override
      public void downloadToStream(OutputStream outputStream) throws IOException {
        try (ByteArrayInputStream bis =
                 new ByteArrayInputStream(configFileParameters.getFileContent().getBytes(StandardCharsets.UTF_8));) {
          IOUtils.copy(bis, outputStream);
        }
      }
    });
  }

  protected void saveExecutionLog(String line) {
    SshHelperUtils.checkAndSaveExecutionLog(line, logCallback, shouldSaveExecutionLogs);
  }

  protected void saveExecutionLogWarn(String line) {
    SshHelperUtils.checkAndSaveExecutionLogWarn(line, logCallback, shouldSaveExecutionLogs);
  }

  protected void saveExecutionLogError(String line) {
    SshHelperUtils.checkAndSaveExecutionLogError(line, logCallback, shouldSaveExecutionLogs);
  }
}
