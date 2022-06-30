/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.intfc;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.logging.LogCallback;

import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.GitOperationContext;
import software.wings.beans.yaml.GitCommitAndPushResult;
import software.wings.beans.yaml.GitFetchFilesResult;

import java.util.List;

@OwnedBy(HarnessTeam.DX)
@TargetModule(HarnessModule._970_API_SERVICES_BEANS)
public interface GitService {
  String validate(GitConfig gitConfig);

  void ensureRepoLocallyClonedAndUpdated(GitOperationContext gitOperationContext);

  GitFetchFilesResult fetchFilesByPath(GitConfig gitConfig, String connectorId, String commitId, String branch,
      List<String> filePaths, boolean useBranch, boolean shouldExportCommitSha);

  GitFetchFilesResult fetchFilesByPath(GitConfig gitConfig, String connectorId, String commitId, String branch,
      List<String> filePaths, boolean useBranch, boolean shouldExportCommitSha, LogCallback logCallback);

  GitFetchFilesResult fetchFilesBetweenCommits(
      GitConfig gitConfig, String newCommitId, String oldCommitId, String connectorId);

  GitFetchFilesResult fetchFilesByPath(GitConfig gitConfig, String connectorId, String commitId, String branch,
      List<String> filePaths, boolean useBranch, List<String> fileExtensions, boolean isRecursive);

  String downloadFiles(
      GitConfig gitConfig, GitFileConfig gitFileConfig, String destinationDirectory, boolean shouldExportCommitSha);

  String downloadFiles(GitConfig gitConfig, GitFileConfig gitFileConfig, String destinationDirectory,
      boolean shouldExportCommitSha, LogCallback executionLogCallback);

  GitCommitAndPushResult commitAndPush(GitOperationContext gitOperationContext);
}
