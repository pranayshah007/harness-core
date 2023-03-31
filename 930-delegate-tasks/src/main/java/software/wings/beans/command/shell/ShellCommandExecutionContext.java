/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.annotations.dev.HarnessModule._950_DELEGATE_TASKS_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.task.shell.ConfigFileMetaData;
import io.harness.logging.CommandExecutionStatus;
import io.harness.shell.BaseScriptExecutor;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.core.ssh.executors.FileBasedScriptExecutor;

import java.util.List;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.tuple.Pair;

@EqualsAndHashCode(callSuper = true)
@OwnedBy(CDP)
@TargetModule(_950_DELEGATE_TASKS_BEANS)
public class ShellCommandExecutionContext extends CommandExecutionContext {
  private BaseScriptExecutor executor;
  private FileBasedScriptExecutor fileBasedScriptExecutor;

  public ShellCommandExecutionContext(CommandExecutionContext other) {
    super(other);
  }

  public CommandExecutionStatus copyGridFsFiles(
      String destinationDirectoryPath, FileBucket fileBucket, List<Pair<String, String>> fileNamesIds) {
    return fileBasedScriptExecutor.copyGridFsFiles(
        evaluateVariable(destinationDirectoryPath), fileBucket, fileNamesIds);
  }

  public CommandExecutionStatus copyConfigFiles(ConfigFileMetaData configFileMetaData) {
    configFileMetaData.setDestinationDirectoryPath(evaluateVariable(configFileMetaData.getDestinationDirectoryPath()));
    return fileBasedScriptExecutor.copyConfigFiles(configFileMetaData);
  }

  public CommandExecutionStatus copyFiles(String destinationDirectoryPath, List<String> files) {
    return fileBasedScriptExecutor.copyFiles(evaluateVariable(destinationDirectoryPath), files);
  }

  public CommandExecutionStatus copyFiles(String destinationDirectoryPath,
      ArtifactStreamAttributes artifactStreamAttributes, String accountId, String appId, String activityId,
      String commandUnitName, String hostName) {
    return fileBasedScriptExecutor.copyFiles(evaluateVariable(destinationDirectoryPath), artifactStreamAttributes,
        accountId, appId, activityId, commandUnitName, hostName);
  }

  public CommandExecutionStatus executeCommandString(String commandString) {
    return executor.executeCommandString(commandString, false);
  }

  public CommandExecutionStatus executeCommandString(String commandString, boolean displayCommand) {
    return executor.executeCommandString(commandString, displayCommand, false);
  }

  public CommandExecutionStatus executeCommandString(String commandString, StringBuffer output) {
    return executor.executeCommandString(commandString, output);
  }

  public void setExecutor(BaseScriptExecutor executor) {
    this.executor = executor;
  }

  public void setFileBasedScriptExecutor(FileBasedScriptExecutor fileBasedScriptExecutor) {
    this.fileBasedScriptExecutor = fileBasedScriptExecutor;
  }
}
