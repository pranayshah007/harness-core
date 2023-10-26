/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.yaml;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.logging.LogCallback;

import software.wings.beans.GitConfig;
import software.wings.beans.GitOperationContext;
import software.wings.beans.yaml.GitDiffResult;
import software.wings.beans.yaml.GitFetchFilesRequest;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.beans.yaml.GitFilesBetweenCommitsRequest;

/**
 * Created by anubhaw on 10/16/17.
 */

/**
 * The interface Git client.
 */
// Use git client V2 instead of this.
@OwnedBy(HarnessTeam.DX)
@TargetModule(HarnessModule._970_API_SERVICES_BEANS)
@BreakDependencyOn("software.wings.beans.GitConfig")
public interface GitClient {
  void ensureRepoLocallyClonedAndUpdated(GitOperationContext gitOperationContext, boolean hardResetForGitRef);

  void cloneRepoAndCopyToDestDir(GitOperationContext gitOperationContext, String destinationDir,
      LogCallback logCallback, boolean hardResetForGitRef);

  GitDiffResult diff(GitOperationContext gitOperationContext, boolean excludeFilesOutsideSetupFolder);

  @Deprecated String validate(GitConfig gitConfig);

  GitFetchFilesResult fetchFilesByPath(
      GitConfig gitConfig, GitFetchFilesRequest gitRequest, boolean shouldExportCommitSha);

  GitFetchFilesResult fetchFilesByPath(
      GitConfig gitConfig, GitFetchFilesRequest gitRequest, boolean shouldExportCommitSha, LogCallback logCallback);

  GitFetchFilesResult fetchFilesBetweenCommits(GitConfig gitConfig, GitFilesBetweenCommitsRequest gitRequest);

  String downloadFiles(
      GitConfig gitConfig, GitFetchFilesRequest gitRequest, String destinationDirectory, boolean shouldExportCommitSha);

  String downloadFiles(GitConfig gitConfig, GitFetchFilesRequest gitRequest, String destinationDirectory,
      boolean shouldExportCommitSha, LogCallback logCallback);
}
