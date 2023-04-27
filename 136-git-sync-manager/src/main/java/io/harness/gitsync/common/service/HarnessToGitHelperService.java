/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.service;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.gitsync.BranchDetails;
import io.harness.gitsync.CreateFileRequest;
import io.harness.gitsync.CreateFileResponse;
import io.harness.gitsync.CreatePRRequest;
import io.harness.gitsync.CreatePRResponse;
import io.harness.gitsync.FileInfo;
import io.harness.gitsync.GetBatchFilesRequest;
import io.harness.gitsync.GetBatchFilesResponse;
import io.harness.gitsync.GetBranchHeadCommitRequest;
import io.harness.gitsync.GetBranchHeadCommitResponse;
import io.harness.gitsync.GetFileRequest;
import io.harness.gitsync.GetFileResponse;
import io.harness.gitsync.GetRepoUrlRequest;
import io.harness.gitsync.GetRepoUrlResponse;
import io.harness.gitsync.IsGitSimplificationEnabledRequest;
import io.harness.gitsync.ListFilesRequest;
import io.harness.gitsync.ListFilesResponse;
import io.harness.gitsync.PushFileResponse;
import io.harness.gitsync.PushInfo;
import io.harness.gitsync.RepoDetails;
import io.harness.gitsync.UpdateFileRequest;
import io.harness.gitsync.UpdateFileResponse;
import io.harness.gitsync.UserDetailsRequest;
import io.harness.gitsync.UserDetailsResponse;
import io.harness.security.dto.UserPrincipal;

@OwnedBy(DX)
public interface HarnessToGitHelperService {
  void postPushOperation(PushInfo pushInfo);

  Boolean isGitSyncEnabled(EntityScopeInfo entityScopeInfo);

  Boolean isGitSimplificationEnabled(IsGitSimplificationEnabledRequest isGitSimplificationEnabledRequest);

  BranchDetails getBranchDetails(RepoDetails repoDetails);

  PushFileResponse pushFile(FileInfo request);

  UserPrincipal getFullSyncUser(FileInfo request);

  GetFileResponse getFileByBranch(GetFileRequest getFileRequest);

  CreateFileResponse createFile(CreateFileRequest createFileRequest);

  UpdateFileResponse updateFile(UpdateFileRequest updateFileRequest);

  CreatePRResponse createPullRequest(CreatePRRequest createPRRequest);

  GetRepoUrlResponse getRepoUrl(GetRepoUrlRequest getRepoUrlRequest);

  Boolean isOldGitSyncEnabledForModule(EntityScopeInfo entityScopeInfo, boolean isNotForFFModule);

  GetBranchHeadCommitResponse getBranchHeadCommitDetails(GetBranchHeadCommitRequest getBranchHeadCommitRequest);

  ListFilesResponse listFiles(ListFilesRequest listFilesRequest);

  GetBatchFilesResponse getBatchFiles(GetBatchFilesRequest getBatchFilesRequest);

  UserDetailsResponse getUserDetails(UserDetailsRequest request);
}
