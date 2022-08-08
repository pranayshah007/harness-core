/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.core.ssh.executors;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.task.shell.ConfigFileMetaData;
import io.harness.delegate.task.winrm.FileBasedAbstractWinRmExecutor;
import io.harness.delegate.task.winrm.WinRmSessionConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.delegatetasks.DelegateFileManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class FileBasedWinRmExecutor extends FileBasedAbstractWinRmExecutor implements FileBasedScriptExecutor {
  protected DelegateFileManager delegateFileManager;

  public FileBasedWinRmExecutor(LogCallback logCallback, DelegateFileManager delegateFileManager,
      boolean shouldSaveExecutionLogs, WinRmSessionConfig config, boolean disableCommandEncoding) {
    super(logCallback, shouldSaveExecutionLogs, config, disableCommandEncoding);
    this.delegateFileManager = delegateFileManager;
  }

  @Override
  public byte[] getConfigFileBytes(ConfigFileMetaData configFileMetaData) throws IOException {
    try (InputStream is = delegateFileManager.downloadByConfigFileId(configFileMetaData.getFileId(),
             config.getAccountId(), config.getAppId(), configFileMetaData.getActivityId())) {
      return IOUtils.toByteArray(is);
    }
  }

  @Override
  public CommandExecutionStatus copyFiles(String destinationDirectoryPath, List<String> files) {
    throw new NotImplementedException(NOT_IMPLEMENTED);
  }

  @Override
  public CommandExecutionStatus copyFiles(String destinationDirectoryPath,
      ArtifactStreamAttributes artifactStreamAttributes, String accountId, String appId, String activityId,
      String commandUnitName, String hostName) {
    throw new NotImplementedException(NOT_IMPLEMENTED);
  }

  @Override
  public CommandExecutionStatus copyGridFsFiles(
      String destinationDirectoryPath, FileBucket fileBucket, List<Pair<String, String>> fileNamesIds) {
    throw new NotImplementedException(NOT_IMPLEMENTED);
  }
}
