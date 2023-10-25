/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

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
import software.wings.service.intfc.yaml.GitClient;

@OwnedBy(HarnessTeam.DX)
@TargetModule(HarnessModule._970_API_SERVICES_BEANS)
public class GitClientUnsupported implements GitClient {
  @Override
  public void ensureRepoLocallyClonedAndUpdated(GitOperationContext gitOperationContext, boolean hardResetForGitRef) {
    throw new UnsupportedOperationException("Git operations not supported.");
  }

  public void cloneRepoAndCopyToDestDir(GitOperationContext gitOperationContext, String destinationDir,
      LogCallback logCallback, boolean hardResetForGitRef) {
    throw new UnsupportedOperationException("Git operations not supported.");
  }

  @Override
  public GitDiffResult diff(GitOperationContext gitOperationContext, boolean excludeFilesOutsideSetupFolder) {
    throw new UnsupportedOperationException("Git operations not supported.");
  }

  @Override
  public String validate(GitConfig gitConfig) {
    throw new UnsupportedOperationException("Git operations not supported.");
  }

  @Override
  public GitFetchFilesResult fetchFilesByPath(
      GitConfig gitConfig, GitFetchFilesRequest gitRequest, boolean shouldExportCommitSha) {
    throw new UnsupportedOperationException("Git operations not supported.");
  }

  @Override
  public GitFetchFilesResult fetchFilesByPath(
      GitConfig gitConfig, GitFetchFilesRequest gitRequest, boolean shouldExportCommitSha, LogCallback logCallback) {
    throw new UnsupportedOperationException("Git operations not supported.");
  }

  @Override
  public GitFetchFilesResult fetchFilesBetweenCommits(GitConfig gitConfig, GitFilesBetweenCommitsRequest gitRequest) {
    throw new UnsupportedOperationException("Git operations not supported.");
  }

  @Override
  public String downloadFiles(GitConfig gitConfig, GitFetchFilesRequest gitRequest, String destinationDirectory,
      boolean shouldExportCommitSha) {
    throw new UnsupportedOperationException("Git operations not supported.");
  }

  @Override
  public String downloadFiles(GitConfig gitConfig, GitFetchFilesRequest gitRequest, String destinationDirectory,
      boolean shouldExportCommitSha, LogCallback logCallback) {
    throw new UnsupportedOperationException("Git operations not supported.");
  }
}
