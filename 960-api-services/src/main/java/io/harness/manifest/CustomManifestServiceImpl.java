/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.manifest;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ShellExecutionException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ScriptType;
import io.harness.shell.ShellExecutorConfig;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@OwnedBy(HarnessTeam.CDP)
@Singleton
@Slf4j
public class CustomManifestServiceImpl implements CustomManifestService {
  private static final String SHELL_SCRIPT_TEMP_DIRECTORY_PREFIX = "manifestCustomSource";
  private static final String SHELL_SCRIPT_TASK_ID = "custom-source";

  @Override
  public void downloadCustomSource(
      @NotNull CustomManifestSource source, String outputDirectory, LogCallback logCallback) throws IOException {
    String workingDirectory = getWorkingDirectory();
    try {
      downloadCustomSource(source, outputDirectory, workingDirectory, logCallback);
    } finally {
      cleanup(workingDirectory);
    }
  }

  @Override
  public Collection<CustomSourceFile> fetchValues(@NotNull CustomManifestSource source, String workingDirectory,
      String activityId, LogCallback logCallback, boolean closeLogStream) throws IOException {
    if (isNotEmpty(source.getScript())) {
      executeScript(source.getScript(), workingDirectory, activityId, logCallback, closeLogStream);
    }
    return readFilesContent(workingDirectory, source.getFilePaths());
  }

  @Override
  public String getWorkingDirectory() throws IOException {
    return Files.createTempDirectory(SHELL_SCRIPT_TEMP_DIRECTORY_PREFIX).toString();
  }

  @Override
  @NotNull
  public String executeCustomSourceScript(String activityId, LogCallback logCallback,
      @NotNull CustomManifestSource customManifestSource, boolean closeLogStream) throws IOException {
    String defaultSourceWorkingDirectory = getWorkingDirectory();
    executeScript(
        customManifestSource.getScript(), defaultSourceWorkingDirectory, activityId, logCallback, closeLogStream);
    return defaultSourceWorkingDirectory;
  }

  private void downloadCustomSource(CustomManifestSource source, String outputDirectory, String workingDirectory,
      LogCallback logCallback) throws IOException {
    if (isNotEmpty(source.getScript())) {
      executeScript(source.getScript(), workingDirectory, SHELL_SCRIPT_TASK_ID, logCallback, true);
    }
    copyFiles(workingDirectory, outputDirectory, source.getFilePaths());
  }

  private void executeScript(
      String script, String workingDirectory, String executionId, LogCallback logCallback, boolean closeLogStream) {
    final ShellExecutorConfig shellExecutorConfig = ShellExecutorConfig.builder()
                                                        .environment(ImmutableMap.of())
                                                        .workingDirectory(workingDirectory)
                                                        .scriptType(ScriptType.BASH)
                                                        .executionId(executionId)
                                                        .closeLogStream(closeLogStream)
                                                        .build();
    final ScriptProcessExecutor executor = createExecutor(shellExecutorConfig, logCallback);
    final ExecuteCommandResponse executeCommandResponse = executor.executeCommandString(script, emptyList());

    if (CommandExecutionStatus.SUCCESS != executeCommandResponse.getStatus()) {
      throw new ShellExecutionException("Custom shell script failed");
    }
  }

  public void cleanup(String path) {
    if (isNotEmpty(path)) {
      try {
        deleteDirectoryAndItsContentIfExists(path);
      } catch (IOException e) {
        log.error("Failed to delete file " + path, e);
      }
    }
  }

  private void copyFiles(String parentDirectory, String outputDirectory, List<String> filePaths) throws IOException {
    for (String filePath : filePaths) {
      String fileFullPath = getFileFullPath(parentDirectory, filePath);
      File file = new File(fileFullPath);

      if (file.isDirectory()) {
        FileUtils.copyDirectory(file, new File(outputDirectory));
      } else {
        FileUtils.copyFile(file, new File(Paths.get(outputDirectory, filePath).toString()));
      }
    }
  }

  @Override
  public Collection<CustomSourceFile> readFilesContent(String parentDirectory, List<String> filesPath)
      throws IOException {
    List<CustomSourceFile> filesContentList = new ArrayList<>();
    for (String filePath : filesPath) {
      String fileFullPath = getFileFullPath(parentDirectory, filePath);
      String fileContent = FileUtils.readFileToString(new File(fileFullPath), Charset.defaultCharset());
      filesContentList.add(CustomSourceFile.builder().filePath(filePath).fileContent(fileContent).build());
    }

    return filesContentList;
  }

  private String getFileFullPath(String parentDirectory, String filePath) {
    if (filePath.startsWith(File.separator)) {
      return filePath;
    }

    return Paths.get(parentDirectory, filePath).toString();
  }

  ScriptProcessExecutor createExecutor(ShellExecutorConfig config, LogCallback logCallback) {
    boolean saveExecutionLog = logCallback != null;
    return new ScriptProcessExecutor(logCallback, saveExecutionLog, config);
  }
}
