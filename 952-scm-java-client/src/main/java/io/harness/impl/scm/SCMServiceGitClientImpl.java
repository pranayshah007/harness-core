/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.impl.scm;

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
import io.harness.beans.request.GitFileRequestV2;
import io.harness.beans.request.ListFilesInCommitRequest;
import io.harness.beans.response.GitFileBatchResponse;
import io.harness.beans.response.GitFileContentBatchResponse;
import io.harness.beans.response.GitFileContentResponse;
import io.harness.beans.response.GitFileResponse;
import io.harness.beans.response.ListFilesInCommitResponse;
import io.harness.delegate.beans.connector.scm.ScmConnector;
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
import io.harness.service.ScmClient;
import io.harness.service.ScmServiceClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(DX)
public class SCMServiceGitClientImpl implements ScmClient {
  SCMGrpc.SCMBlockingStub scmBlockingStub;
  ScmServiceClient scmServiceClient;

  @Override
  public CreateFileResponse createFile(ScmConnector scmConnector, GitFileDetails gitFileDetails, boolean useGitClient) {
    return scmServiceClient.createFile(scmConnector, gitFileDetails, scmBlockingStub, useGitClient);
  }

  @Override
  public UpdateFileResponse updateFile(ScmConnector scmConnector, GitFileDetails gitFileDetails, boolean useGitClient) {
    return scmServiceClient.updateFile(scmConnector, gitFileDetails, scmBlockingStub, useGitClient);
  }

  @Override
  public DeleteFileResponse deleteFile(ScmConnector scmConnector, GitFileDetails gitFileDetails) {
    return scmServiceClient.deleteFile(scmConnector, gitFileDetails, scmBlockingStub);
  }

  @Override
  public FileContent getFileContent(ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails) {
    return scmServiceClient.getFileContent(scmConnector, gitFilePathDetails, scmBlockingStub);
  }

  @Override
  public FileContent getLatestFile(ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails) {
    return scmServiceClient.getLatestFile(scmConnector, gitFilePathDetails, scmBlockingStub);
  }

  @Override
  public IsLatestFileResponse isLatestFile(
      ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails, FileContent fileContent) {
    return scmServiceClient.isLatestFile(scmConnector, gitFilePathDetails, fileContent, scmBlockingStub);
  }

  @Override
  public FileContent pushFile(ScmConnector scmConnector, GitFileDetails gitFileDetails) {
    return scmServiceClient.pushFile(scmConnector, gitFileDetails, scmBlockingStub);
  }

  @Override
  public FindFilesInBranchResponse findFilesInBranch(ScmConnector scmConnector, String branch) {
    return scmServiceClient.findFilesInBranch(scmConnector, branch, scmBlockingStub);
  }

  @Override
  public FindFilesInCommitResponse listFilesInCommit(ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails) {
    return scmServiceClient.listFilesInCommit(scmConnector, gitFilePathDetails, scmBlockingStub);
  }

  @Override
  public ListFilesInCommitResponse listFilesInCommit(ScmConnector scmConnector, ListFilesInCommitRequest request) {
    return scmServiceClient.listFilesInCommit(scmConnector, request, scmBlockingStub);
  }

  @Override
  public GetLatestCommitResponse getLatestCommit(ScmConnector scmConnector, String branch, String ref) {
    return scmServiceClient.getLatestCommit(scmConnector, branch, ref, scmBlockingStub);
  }

  @Override
  public ListBranchesResponse listBranches(ScmConnector scmConnector) {
    return scmServiceClient.listBranches(scmConnector, scmBlockingStub);
  }

  @Override
  public ListBranchesWithDefaultResponse listBranchesWithDefault(
      ScmConnector scmConnector, PageRequestDTO pageRequest) {
    return scmServiceClient.listBranchesWithDefault(scmConnector, pageRequest, scmBlockingStub);
  }

  @Override
  public ListCommitsResponse listCommits(ScmConnector scmConnector, String branch) {
    return scmServiceClient.listCommits(scmConnector, branch, scmBlockingStub);
  }

  @Override
  public ListCommitsInPRResponse listCommitsInPR(ScmConnector scmConnector, int prNumber) {
    return scmServiceClient.listCommitsInPR(scmConnector, prNumber, scmBlockingStub);
  }

  @Override
  public FileContentBatchResponse listFiles(ScmConnector connector, Set<String> foldersList, String branch) {
    return scmServiceClient.listFiles(connector, foldersList, branch, scmBlockingStub);
  }

  @Override
  public FileContentBatchResponse listFilesByFilePaths(
      ScmConnector connector, List<String> filePathsList, String branchName) {
    return scmServiceClient.listFilesByFilePaths(connector, filePathsList, branchName, scmBlockingStub);
  }

  @Override
  public FileContentBatchResponse listFilesByCommitId(
      ScmConnector connector, List<String> filePathsList, String commitId) {
    return scmServiceClient.listFilesByCommitId(connector, filePathsList, commitId, scmBlockingStub);
  }

  @Override
  public CreateBranchResponse createNewBranch(ScmConnector scmConnector, String branch, String defaultBranchName) {
    return scmServiceClient.createNewBranch(scmConnector, branch, defaultBranchName, scmBlockingStub);
  }

  @Override
  public CreatePRResponse createPullRequest(ScmConnector scmConnector, GitPRCreateRequest gitPRCreateRequest) {
    return scmServiceClient.createPullRequest(scmConnector, gitPRCreateRequest, scmBlockingStub);
  }

  @Override
  public CreateWebhookResponse createWebhook(ScmConnector scmConnector, GitWebhookDetails gitWebhookDetails) {
    return scmServiceClient.createWebhook(scmConnector, gitWebhookDetails, scmBlockingStub);
  }

  @Override
  public DeleteWebhookResponse deleteWebhook(ScmConnector scmConnector, String id) {
    return scmServiceClient.deleteWebhook(scmConnector, id, scmBlockingStub);
  }

  @Override
  public ListWebhooksResponse listWebhook(ScmConnector scmConnector) {
    return scmServiceClient.listWebhook(scmConnector, scmBlockingStub);
  }

  @Override
  public CreateWebhookResponse upsertWebhook(ScmConnector scmConnector, GitWebhookDetails gitWebhookDetails) {
    return scmServiceClient.upsertWebhook(scmConnector, gitWebhookDetails, scmBlockingStub);
  }

  @Override
  public CompareCommitsResponse compareCommits(
      ScmConnector scmConnector, String initialCommitId, String finalCommitId) {
    return scmServiceClient.compareCommits(scmConnector, initialCommitId, finalCommitId, scmBlockingStub);
  }

  @Override
  public FindCommitResponse findCommit(ScmConnector scmConnector, String commitId) {
    return scmServiceClient.findCommit(scmConnector, commitId, scmBlockingStub);
  }

  @Override
  public GetUserReposResponse getUserRepos(ScmConnector scmConnector, PageRequestDTO pageRequest) {
    return scmServiceClient.getUserRepos(scmConnector, pageRequest, scmBlockingStub);
  }

  @Override
  public GetUserRepoResponse getRepoDetails(ScmConnector scmConnector) {
    return scmServiceClient.getRepoDetails(scmConnector, scmBlockingStub);
  }

  @Override
  public GetUserReposResponse getAllUserRepos(ScmConnector scmConnector) {
    return scmServiceClient.getAllUserRepos(scmConnector, scmBlockingStub);
  }

  @Override
  public CreateBranchResponse createNewBranchV2(
      ScmConnector scmConnector, String newBranchName, String baseBranchName) {
    return scmServiceClient.createNewBranchV2(scmConnector, newBranchName, baseBranchName, scmBlockingStub);
  }

  @Override
  public CreatePRResponse createPullRequestV2(
      ScmConnector scmConnector, String sourceBranchName, String targetBranchName, String prTitle) {
    return scmServiceClient.createPullRequestV2(
        scmConnector, sourceBranchName, targetBranchName, prTitle, scmBlockingStub);
  }

  @Override
  public RefreshTokenResponse refreshToken(
      ScmConnector scmConnector, String clientId, String clientSecret, String endpoint, String refreshToken) {
    return scmServiceClient.refreshToken(scmConnector, clientId, clientSecret, endpoint, refreshToken, scmBlockingStub);
  }
  @Override
  public GetLatestCommitOnFileResponse getLatestCommitOnFile(
      ScmConnector scmConnector, String branchName, String filePath) {
    return scmServiceClient.getLatestCommitOnFile(scmConnector, branchName, filePath, scmBlockingStub);
  }

  @Override
  public GitFileResponse getFile(ScmConnector scmConnector, GitFileRequest gitFileContentRequest) {
    return scmServiceClient.getFile(scmConnector, gitFileContentRequest, scmBlockingStub);
  }

  @Override
  public GitFileBatchResponse getBatchFile(GitFileBatchRequest gitFileBatchRequest) {
    return scmServiceClient.getBatchFile(gitFileBatchRequest, scmBlockingStub);
  }

  @Override
  public GitFileContentResponse getFileContent(GitFileRequestV2 gitFileRequestV2) {
    return scmServiceClient.getFileContent(gitFileRequestV2, scmBlockingStub);
  }

  @Override
  public GitFileContentBatchResponse getBatchFileContent(GitFileBatchRequest gitFileBatchRequest) {
    return scmServiceClient.getBatchFileContent(gitFileBatchRequest, scmBlockingStub);
  }
}
