/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.core.ssh.executors;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.CommandExecutionStatus.FAILURE;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.task.shell.ConfigFileMetaData;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.shell.AbstractScriptExecutor;
import io.harness.shell.SshHelperUtils;

import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.delegatetasks.DelegateFileManager;

import com.google.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public abstract class FileBasedAbstractScriptExecutor implements FileBasedScriptExecutor {
  protected LogCallback logCallback;
  /**
   * The File service.
   */
  protected DelegateFileManager delegateFileManager;

  protected boolean shouldSaveExecutionLogs;

  /**
   * Instantiates a new abstract ssh executor.
   *  @param delegateFileManager the file service
   * @param logCallback          the log service
   */
  @Inject
  public FileBasedAbstractScriptExecutor(
      DelegateFileManager delegateFileManager, LogCallback logCallback, boolean shouldSaveExecutionLogs) {
    this.logCallback = logCallback;
    this.delegateFileManager = delegateFileManager;
    this.shouldSaveExecutionLogs = shouldSaveExecutionLogs;
  }

  @Override
  public CommandExecutionStatus copyGridFsFiles(
      String destinationDirectoryPath, FileBucket fileBucket, List<Pair<String, String>> fileNamesIds) {
    if (isEmpty(fileNamesIds)) {
      saveExecutionLog("There are no artifacts to copy.");
      return CommandExecutionStatus.SUCCESS;
    }

    return fileNamesIds.stream()
        .map(fileNamesId
            -> scpOneFile(destinationDirectoryPath,
                new AbstractScriptExecutor.FileProvider() {
                  @Override
                  public Pair<String, Long> getInfo() throws IOException {
                    DelegateFile delegateFile;
                    try {
                      delegateFile = delegateFileManager.getMetaInfo(fileBucket, fileNamesId.getKey(), getAccountId());
                    } catch (WingsException e) {
                      saveExecutionLogError(e.getMessage());
                      throw e;
                    }
                    return ImmutablePair.of(
                        isBlank(fileNamesId.getRight()) ? delegateFile.getFileName() : fileNamesId.getRight(),
                        delegateFile.getLength());
                  }

                  @Override
                  public void downloadToStream(OutputStream outputStream) throws IOException, ExecutionException {
                    try (InputStream inputStream = delegateFileManager.downloadArtifactByFileId(
                             fileBucket, fileNamesId.getKey(), getAccountId())) {
                      IOUtils.copy(inputStream, outputStream);
                    }
                  }
                }))
        .filter(commandExecutionStatus -> commandExecutionStatus == FAILURE)
        .findFirst()
        .orElse(CommandExecutionStatus.SUCCESS);
  }

  @Override
  public CommandExecutionStatus copyConfigFiles(ConfigFileMetaData configFileMetaData) {
    if (isBlank(configFileMetaData.getFileId()) || isBlank(configFileMetaData.getFilename())) {
      saveExecutionLog("There are no artifacts to copy. " + configFileMetaData.toString());
      return CommandExecutionStatus.SUCCESS;
    }
    return scpOneFile(configFileMetaData.getDestinationDirectoryPath(), new AbstractScriptExecutor.FileProvider() {
      @Override
      public Pair<String, Long> getInfo() {
        return ImmutablePair.of(configFileMetaData.getFilename(), configFileMetaData.getLength());
      }

      @Override
      public void downloadToStream(OutputStream outputStream) throws IOException {
        try (InputStream inputStream = delegateFileManager.downloadByConfigFileId(
                 configFileMetaData.getFileId(), getAccountId(), getAppId(), configFileMetaData.getActivityId())) {
          IOUtils.copy(inputStream, outputStream);
        }
      }
    });
  }

  @Override
  public CommandExecutionStatus copyFiles(String destinationDirectoryPath, List<String> files) {
    return files.stream()
        .map(file
            -> scpOneFile(destinationDirectoryPath,
                new AbstractScriptExecutor.FileProvider() {
                  @Override
                  public Pair<String, Long> getInfo() throws IOException {
                    File file1 = new File(file);
                    return ImmutablePair.of(file1.getName(), file1.length());
                  }

                  @Override
                  public void downloadToStream(OutputStream outputStream) throws IOException {
                    try (FileInputStream fis = new FileInputStream(file)) {
                      IOUtils.copy(fis, outputStream);
                    }
                  }
                }))
        .filter(commandExecutionStatus -> commandExecutionStatus == CommandExecutionStatus.FAILURE)
        .findFirst()
        .orElse(CommandExecutionStatus.SUCCESS);
  }

  @Override
  public CommandExecutionStatus copyFiles(String destinationDirectoryPath,
      ArtifactStreamAttributes artifactStreamAttributes, String accountId, String appId, String activityId,
      String commandUnitName, String hostName) {
    Map<String, String> metadata = artifactStreamAttributes.getMetadata();
    return scpOneFile(destinationDirectoryPath, new AbstractScriptExecutor.FileProvider() {
      @Override
      public Pair<String, Long> getInfo() {
        if (!metadata.containsKey(ArtifactMetadataKeys.artifactFileSize)) {
          Long artifactFileSize = delegateFileManager.getArtifactFileSize(artifactStreamAttributes);
          metadata.put(ArtifactMetadataKeys.artifactFileSize, String.valueOf(artifactFileSize));
        }
        String fileName = metadata.get(ArtifactMetadataKeys.artifactFileName);
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
        try (InputStream inputStream = delegateFileManager.downloadArtifactAtRuntime(
                 artifactStreamAttributes, accountId, appId, activityId, commandUnitName, hostName)) {
          IOUtils.copy(inputStream, outputStream);
        }
      }
    });
  }

  public abstract String getAccountId();

  public abstract String getCommandUnitName();

  public abstract String getAppId();

  public abstract String getExecutionId();

  public abstract String getHost();

  public abstract CommandExecutionStatus scpOneFile(
      String remoteFilePath, AbstractScriptExecutor.FileProvider fileProvider);

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
