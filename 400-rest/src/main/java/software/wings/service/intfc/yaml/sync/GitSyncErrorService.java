/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.yaml.sync;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import software.wings.beans.yaml.Change;
import software.wings.service.impl.yaml.GitToHarnessErrorCommitStats;
import software.wings.yaml.errorhandling.GitProcessingError;
import software.wings.yaml.errorhandling.GitSyncError;
import software.wings.yaml.gitSync.GitFileActivity;

import java.util.List;

public interface GitSyncErrorService {
  PageResponse<GitToHarnessErrorCommitStats> listGitToHarnessErrorsCommits(
      PageRequest<GitToHarnessErrorCommitStats> req, String appId, String branchName, Integer numberOfErrorsInSummary);

  PageResponse<GitSyncError> listAllGitToHarnessErrors(
      PageRequest<GitSyncError> req, String accountId, String appId, String yamlFilePathPattern);

  PageResponse<GitSyncError> fetchErrorsInEachCommits(PageRequest<GitSyncError> req, String gitCommitId,
      String accountId, String appId, List<String> includeDataList, String yamlFilePath);

  <T extends Change> void upsertGitSyncErrors(
      T failedChange, String errorMessage, boolean fullSyncPath, boolean gitToHarness);

  List<GitSyncError> getActiveGitToHarnessSyncErrors(
      String accountId, String gitConnectorId, String branchName, String repositoryName, long fromTimestamp);

  PageResponse<GitSyncError> fetchHarnessToGitErrors(PageRequest<GitSyncError> req, String accountId, String appId);

  PageResponse<GitSyncError> fetchErrors(PageRequest<GitSyncError> req);

  void deleteGitSyncErrorAndLogFileActivity(List<String> errorIds, GitFileActivity.Status status, String accountId);

  long getGitSyncErrorCount(String accountId, boolean followRBAC);

  boolean deleteGitSyncErrors(List<String> errorIds, String accountId);

  boolean deleteGitSyncErrorsBeforeTime(long time, String accountId);

  PageResponse<GitProcessingError> fetchGitConnectivityIssues(PageRequest<GitProcessingError> req, String accountId);

  long getTotalGitErrorsCount(String accountId);

  Integer getTotalGitCommitsWithErrors(String accountId, String appId);
}
