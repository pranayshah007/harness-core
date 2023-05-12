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
import io.harness.product.ci.scm.proto.FindFilesInPRResponse;
import io.harness.product.ci.scm.proto.FindPRResponse;
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
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.product.ci.scm.proto.UpdateFileResponse;

import java.util.List;
import java.util.Set;

@OwnedBy(DX)
public interface ScmServiceClient {
  CreateFileResponse createFile(ScmConnector scmConnector, GitFileDetails gitFileDetails,
      SCMGrpc.SCMBlockingStub scmBlockingStub, boolean useGitClient);

  UpdateFileResponse updateFile(ScmConnector scmConnector, GitFileDetails gitFileDetails,
      SCMGrpc.SCMBlockingStub scmBlockingStub, boolean useGitClient);

  DeleteFileResponse deleteFile(
      ScmConnector scmConnector, GitFileDetails gitFileDetails, SCMGrpc.SCMBlockingStub scmBlockingStub);

  FileContent getFileContent(
      ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails, SCMGrpc.SCMBlockingStub scmBlockingStub);

  FileContent getLatestFile(
      ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails, SCMGrpc.SCMBlockingStub scmBlockingStub);

  IsLatestFileResponse isLatestFile(ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails,
      FileContent fileContent, SCMGrpc.SCMBlockingStub scmBlockingStub);

  FileContent pushFile(
      ScmConnector scmConnector, GitFileDetails gitFileDetails, SCMGrpc.SCMBlockingStub scmBlockingStub);

  FindFilesInBranchResponse findFilesInBranch(
      ScmConnector scmConnector, String branch, SCMGrpc.SCMBlockingStub scmBlockingStub);

  FindFilesInCommitResponse listFilesInCommit(
      ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails, SCMGrpc.SCMBlockingStub scmBlockingStub);

  ListFilesInCommitResponse listFilesInCommit(
      ScmConnector scmConnector, ListFilesInCommitRequest request, SCMGrpc.SCMBlockingStub scmBlockingStub);

  FindFilesInPRResponse findFilesInPR(ScmConnector scmConnector, int prNumber, SCMGrpc.SCMBlockingStub scmBlockingStub);

  GetLatestCommitResponse getLatestCommit(
      ScmConnector scmConnector, String branch, String ref, SCMGrpc.SCMBlockingStub scmBlockingStub);

  FindFilesInCommitResponse listFilesInCommit(
      ScmConnector scmConnector, String commitHash, SCMGrpc.SCMBlockingStub scmBlockingStub);

  ListBranchesResponse listBranches(ScmConnector scmConnector, SCMGrpc.SCMBlockingStub scmBlockingStub);

  ListBranchesWithDefaultResponse listBranchesWithDefault(
      ScmConnector scmConnector, PageRequestDTO pageRequest, SCMGrpc.SCMBlockingStub scmBlockingStub);

  ListCommitsResponse listCommits(ScmConnector scmConnector, String branch, SCMGrpc.SCMBlockingStub scmBlockingStub);

  ListCommitsInPRResponse listCommitsInPR(
      ScmConnector scmConnector, long prNumber, SCMGrpc.SCMBlockingStub scmBlockingStub);

  FileContentBatchResponse listFiles(
      ScmConnector connector, Set<String> foldersList, String branch, SCMGrpc.SCMBlockingStub scmBlockingStub);

  FileContentBatchResponse listFilesV2(
      ScmConnector connector, Set<String> foldersList, String branch, SCMGrpc.SCMBlockingStub scmBlockingStub);

  FileContentBatchResponse listFoldersFilesByCommitId(
      ScmConnector connector, Set<String> foldersList, String commitId, SCMGrpc.SCMBlockingStub scmBlockingStub);

  FileContentBatchResponse listFilesByFilePaths(
      ScmConnector connector, List<String> filePaths, String branch, SCMGrpc.SCMBlockingStub scmBlockingStub);

  FileContentBatchResponse listFilesByCommitId(
      ScmConnector connector, List<String> filePaths, String commitId, SCMGrpc.SCMBlockingStub scmBlockingStub);

  CreateBranchResponse createNewBranch(
      ScmConnector scmConnector, String branch, String baseBranchName, SCMGrpc.SCMBlockingStub scmBlockingStub);

  CreatePRResponse createPullRequest(
      ScmConnector scmConnector, GitPRCreateRequest gitPRCreateRequest, SCMGrpc.SCMBlockingStub scmBlockingStub);

  CreateWebhookResponse createWebhook(
      ScmConnector scmConnector, GitWebhookDetails gitWebhookDetails, SCMGrpc.SCMBlockingStub scmBlockingStub);

  DeleteWebhookResponse deleteWebhook(ScmConnector scmConnector, String id, SCMGrpc.SCMBlockingStub scmBlockingStub);

  ListWebhooksResponse listWebhook(ScmConnector scmConnector, SCMGrpc.SCMBlockingStub scmBlockingStub);

  CreateWebhookResponse upsertWebhook(
      ScmConnector scmConnector, GitWebhookDetails gitWebhookDetails, SCMGrpc.SCMBlockingStub scmBlockingStub);

  CompareCommitsResponse compareCommits(
      ScmConnector scmConnector, String initialCommitId, String finalCommitId, SCMGrpc.SCMBlockingStub scmBlockingStub);

  UserDetailsResponseDTO getUserDetails(
      UserDetailsRequestDTO userDetailsRequestDTO, SCMGrpc.SCMBlockingStub scmBlockingStub);

  GetUserReposResponse getUserRepos(
      ScmConnector scmConnector, PageRequestDTO pageRequest, SCMGrpc.SCMBlockingStub scmBlockingStub);

  GetUserReposResponse getAllUserRepos(ScmConnector scmConnector, SCMGrpc.SCMBlockingStub scmBlockingStub);

  FindPRResponse findPR(ScmConnector scmConnector, long number, SCMGrpc.SCMBlockingStub scmBlockingStub);

  FindCommitResponse findCommit(ScmConnector scmConnector, String commitId, SCMGrpc.SCMBlockingStub scmBlockingStub);

  GetUserRepoResponse getRepoDetails(ScmConnector scmConnector, SCMGrpc.SCMBlockingStub scmBlockingStub);

  CreateBranchResponse createNewBranchV2(
      ScmConnector scmConnector, String newBranchName, String baseBranchName, SCMGrpc.SCMBlockingStub scmBlockingStub);

  CreatePRResponse createPullRequestV2(ScmConnector scmConnector, String sourceBranchName, String targetBranchName,
      String prTitle, SCMGrpc.SCMBlockingStub scmBlockingStub);

  RefreshTokenResponse refreshToken(ScmConnector scmConnector, String clientId, String clientSecret, String endpoint,
      String refreshToken, SCMGrpc.SCMBlockingStub scmBlockingStub);

  GenerateYamlResponse autogenerateStageYamlForCI(
      String cloneUrl, String yamlVersion, SCMGrpc.SCMBlockingStub scmBlockingStub);
  GetLatestCommitOnFileResponse getLatestCommitOnFile(
      ScmConnector scmConnector, String branchName, String filepath, SCMGrpc.SCMBlockingStub scmBlockingStub);

  GitFileResponse getFile(
      ScmConnector scmConnector, GitFileRequest gitFileContentRequest, SCMGrpc.SCMBlockingStub scmBlockingStub);

  GitFileBatchResponse getBatchFile(GitFileBatchRequest gitFileBatchRequest, SCMGrpc.SCMBlockingStub scmBlockingStub);
}
