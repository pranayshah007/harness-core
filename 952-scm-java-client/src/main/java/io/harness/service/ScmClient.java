/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileContentBatchResponse;
import io.harness.beans.PageRequestDTO;
import io.harness.beans.RepoFilterParamsDTO;
import io.harness.beans.gitsync.GitFileDetails;
import io.harness.beans.gitsync.GitFilePathDetails;
import io.harness.beans.gitsync.GitPRCreateRequest;
import io.harness.beans.gitsync.GitWebhookDetails;
import io.harness.beans.request.GitFileBatchRequest;
import io.harness.beans.request.GitFileRequest;
import io.harness.beans.request.ListFilesInCommitRequest;
import io.harness.beans.response.GitFileBatchResponse;
import io.harness.beans.response.GitFileResponse;
import io.harness.beans.response.ListFilesInCommitResponse;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.gitsync.common.dtos.UserDetailsRequestDTO;
import io.harness.gitsync.common.dtos.UserDetailsResponseDTO;
import io.harness.product.ci.scm.proto.CompareCommitsResponse;
import io.harness.product.ci.scm.proto.CreateBranchResponse;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.product.ci.scm.proto.CreatePRResponse;
import io.harness.product.ci.scm.proto.CreateWebhookResponse;
import io.harness.product.ci.scm.proto.DeleteFileResponse;
import io.harness.product.ci.scm.proto.DeleteWebhookResponse;
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.product.ci.scm.proto.FindCommitResponse;
import io.harness.product.ci.scm.proto.FindFilesInBranchResponse;
import io.harness.product.ci.scm.proto.FindFilesInCommitResponse;
import io.harness.product.ci.scm.proto.GenerateYamlResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitOnFileResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitResponse;
import io.harness.product.ci.scm.proto.GetUserRepoResponse;
import io.harness.product.ci.scm.proto.GetUserReposResponse;
import io.harness.product.ci.scm.proto.IsLatestFileResponse;
import io.harness.product.ci.scm.proto.ListBranchesResponse;
import io.harness.product.ci.scm.proto.ListBranchesWithDefaultResponse;
import io.harness.product.ci.scm.proto.ListCommitsInPRResponse;
import io.harness.product.ci.scm.proto.ListCommitsResponse;
import io.harness.product.ci.scm.proto.ListWebhooksResponse;
import io.harness.product.ci.scm.proto.RefreshTokenResponse;
import io.harness.product.ci.scm.proto.UpdateFileResponse;

import java.util.List;
import java.util.Set;

@OwnedBy(DX)
public interface ScmClient {
  // It is assumed that ScmConnector is a decrypted connector.
  CreateFileResponse createFile(ScmConnector scmConnector, GitFileDetails gitFileDetails, boolean useGitClient);

  UpdateFileResponse updateFile(ScmConnector scmConnector, GitFileDetails gitFileDetails, boolean useGitClient);

  DeleteFileResponse deleteFile(ScmConnector scmConnector, GitFileDetails gitFileDetails);

  FileContent getFileContent(ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails);

  FileContent getLatestFile(ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails);

  IsLatestFileResponse isLatestFile(
      ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails, FileContent fileContent);

  FileContent pushFile(ScmConnector scmConnector, GitFileDetails gitFileDetails);

  FindFilesInBranchResponse findFilesInBranch(ScmConnector scmConnector, String branchName);

  FindFilesInCommitResponse listFilesInCommit(ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails);

  ListFilesInCommitResponse listFilesInCommit(ScmConnector scmConnector, ListFilesInCommitRequest request);

  GetLatestCommitResponse getLatestCommit(ScmConnector scmConnector, String branchName, String ref);

  ListBranchesResponse listBranches(ScmConnector scmConnector);

  ListBranchesWithDefaultResponse listBranchesWithDefault(ScmConnector scmConnector, PageRequestDTO pageRequest);

  ListCommitsResponse listCommits(ScmConnector scmConnector, String branchName);

  ListCommitsInPRResponse listCommitsInPR(ScmConnector scmConnector, int prNumber);

  FileContentBatchResponse listFiles(ScmConnector connector, Set<String> foldersList, String branchName);

  FileContentBatchResponse listFilesByFilePaths(ScmConnector connector, List<String> filePathsList, String branchName);

  FileContentBatchResponse listFilesByCommitId(ScmConnector connector, List<String> filePathsList, String commitId);

  CreateBranchResponse createNewBranch(ScmConnector scmConnector, String branch, String defaultBranchName);

  CreatePRResponse createPullRequest(ScmConnector scmConnector, GitPRCreateRequest gitPRCreateRequest);

  CreateWebhookResponse createWebhook(ScmConnector scmConnector, GitWebhookDetails gitWebhookDetails);

  DeleteWebhookResponse deleteWebhook(ScmConnector scmConnector, String id);

  ListWebhooksResponse listWebhook(ScmConnector scmConnector);

  CreateWebhookResponse upsertWebhook(ScmConnector scmConnector, GitWebhookDetails gitWebhookDetails);

  CompareCommitsResponse compareCommits(ScmConnector scmConnector, String initialCommitId, String finalCommitId);

  FindCommitResponse findCommit(ScmConnector scmConnector, String commitId);

  GetUserReposResponse getUserRepos(ScmConnector scmConnector, PageRequestDTO pageRequest);
  GetUserReposResponse getUserRepos(
      ScmConnector scmConnector, PageRequestDTO pageRequest, RepoFilterParamsDTO repoFilterParamsDTO);

  UserDetailsResponseDTO getUserDetails(UserDetailsRequestDTO userDetailsRequestDTO);

  GetUserRepoResponse getRepoDetails(ScmConnector scmConnector);

  GetUserReposResponse getAllUserRepos(ScmConnector scmConnector);

  CreateBranchResponse createNewBranchV2(ScmConnector scmConnector, String newBranchName, String baseBranchName);

  CreatePRResponse createPullRequestV2(
      ScmConnector scmConnector, String sourceBranchName, String targetBranchName, String prTitle);

  RefreshTokenResponse refreshToken(
      ScmConnector scmConnector, String clientId, String clientSecret, String endpoint, String refreshToken);

  GenerateYamlResponse autogenerateStageYamlForCI(String cloneUrl, String yamlVersion);

  GetLatestCommitOnFileResponse getLatestCommitOnFile(ScmConnector scmConnector, String branchName, String filepath);

  GitFileResponse getFile(ScmConnector scmConnector, GitFileRequest gitFileContentRequest);

  GitFileBatchResponse getBatchFile(GitFileBatchRequest gitFileBatchRequest);
}
