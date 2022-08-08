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
import io.harness.logging.CommandExecutionStatus;

import software.wings.beans.artifact.ArtifactStreamAttributes;

import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public interface FileBasedScriptExecutor {
  CommandExecutionStatus copyConfigFiles(ConfigFileMetaData configFileMetaData);

  CommandExecutionStatus copyFiles(String destinationDirectoryPath, List<String> files);

  CommandExecutionStatus copyFiles(String destinationDirectoryPath, ArtifactStreamAttributes artifactStreamAttributes,
      String accountId, String appId, String activityId, String commandUnitName, String hostName);

  CommandExecutionStatus copyGridFsFiles(
      String destinationDirectoryPath, FileBucket fileBucket, List<Pair<String, String>> fileNamesIds);
}
