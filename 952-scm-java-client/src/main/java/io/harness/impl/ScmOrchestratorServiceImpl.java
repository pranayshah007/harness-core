/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.impl;

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
import io.harness.impl.scm.SCMServiceGitClientImpl;
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
import io.harness.service.ScmOrchestratorService;

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
public class ScmOrchestratorServiceImpl implements ScmOrchestratorService {
  private SCMServiceGitClientImpl scmServiceGitClient;

  @Override
  public CreateFileResponse createFile(ScmConnector scmConnector, GitFileDetails gitFileDetails, boolean useGitClient) {
    return scmServiceGitClient.createFile(scmConnector, gitFileDetails, useGitClient);
  }

  @Override
  public UpdateFileResponse updateFile(ScmConnector scmConnector, GitFileDetails gitFileDetails, boolean useGitClient) {
    return scmServiceGitClient.updateFile(scmConnector, gitFileDetails, useGitClient);
  }

  @Override
  public DeleteFileResponse deleteFile(ScmConnector scmConnector, GitFileDetails gitFileDetails) {
    return scmServiceGitClient.deleteFile(scmConnector, gitFileDetails);
  }

  @Override
  public FileContent getFileContent(ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails) {
    return scmServiceGitClient.getFileContent(scmConnector, gitFilePathDetails);
  }

  @Override
  public FileContent getLatestFile(ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails) {
    return scmServiceGitClient.getLatestFile(scmConnector, gitFilePathDetails);
  }

  @Override
  public IsLatestFileResponse isLatestFile(
      ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails, FileContent fileContent) {
    return scmServiceGitClient.isLatestFile(scmConnector, gitFilePathDetails, fileContent);
  }

  @Override
  public FileContent pushFile(ScmConnector scmConnector, GitFileDetails gitFileDetails) {
    return scmServiceGitClient.pushFile(scmConnector, gitFileDetails);
  }

  @Override
  public FindFilesInBranchResponse findFilesInBranch(ScmConnector scmConnector, String branchName) {
    return scmServiceGitClient.findFilesInBranch(scmConnector, branchName);
  }

  @Override
  public FindFilesInCommitResponse listFilesInCommit(ScmConnector scmConnector, GitFilePathDetails gitFilePathDetails) {
    return scmServiceGitClient.listFilesInCommit(scmConnector, gitFilePathDetails);
  }

  @Override
  public ListFilesInCommitResponse listFilesInCommit(ScmConnector scmConnector, ListFilesInCommitRequest request) {
    return scmServiceGitClient.listFilesInCommit(scmConnector, request);
  }

  @Override
  public GetLatestCommitResponse getLatestCommit(ScmConnector scmConnector, String branchName, String ref) {
    return scmServiceGitClient.getLatestCommit(scmConnector, branchName, ref);
  }

  @Override
  public ListBranchesResponse listBranches(ScmConnector scmConnector) {
    return scmServiceGitClient.listBranches(scmConnector);
  }

  @Override
  public ListBranchesWithDefaultResponse listBranchesWithDefault(
      ScmConnector scmConnector, PageRequestDTO pageRequest) {
    return scmServiceGitClient.listBranchesWithDefault(scmConnector, pageRequest);
  }

  @Override
  public ListCommitsResponse listCommits(ScmConnector scmConnector, String branchName) {
    return scmServiceGitClient.listCommits(scmConnector, branchName);
  }

  @Override
  public ListCommitsInPRResponse listCommitsInPR(ScmConnector scmConnector, int prNumber) {
    return scmServiceGitClient.listCommitsInPR(scmConnector, prNumber);
  }

  @Override
  public FileContentBatchResponse listFiles(ScmConnector connector, Set<String> foldersList, String branchName) {
    return scmServiceGitClient.listFiles(connector, foldersList, branchName);
  }

  @Override
  public FileContentBatchResponse listFilesByFilePaths(
      ScmConnector connector, List<String> filePathsList, String branchName) {
    return scmServiceGitClient.listFilesByFilePaths(connector, filePathsList, branchName);
  }

  @Override
  public FileContentBatchResponse listFilesByCommitId(
      ScmConnector connector, List<String> filePathsList, String commitId) {
    return scmServiceGitClient.listFilesByCommitId(connector, filePathsList, commitId);
  }

  @Override
  public CreateBranchResponse createNewBranch(ScmConnector scmConnector, String branch, String defaultBranchName) {
    return scmServiceGitClient.createNewBranch(scmConnector, branch, defaultBranchName);
  }

  @Override
  public CreateWebhookResponse createWebhook(ScmConnector scmConnector, GitWebhookDetails gitWebhookDetails) {
    return scmServiceGitClient.createWebhook(scmConnector, gitWebhookDetails);
  }

  @Override
  public DeleteWebhookResponse deleteWebhook(ScmConnector scmConnector, String id) {
    return scmServiceGitClient.deleteWebhook(scmConnector, id);
  }

  @Override
  public ListWebhooksResponse listWebhook(ScmConnector scmConnector) {
    return scmServiceGitClient.listWebhook(scmConnector);
  }

  @Override
  public CreateWebhookResponse upsertWebhook(ScmConnector scmConnector, GitWebhookDetails gitWebhookDetails) {
    return scmServiceGitClient.upsertWebhook(scmConnector, gitWebhookDetails);
  }

  @Override
  public CompareCommitsResponse compareCommits(
      ScmConnector scmConnector, String initialCommitId, String finalCommitId) {
    return scmServiceGitClient.compareCommits(scmConnector, initialCommitId, finalCommitId);
  }

  @Override
  public CreatePRResponse createPullRequest(ScmConnector scmConnector, GitPRCreateRequest gitPRCreateRequest) {
    return scmServiceGitClient.createPullRequest(scmConnector, gitPRCreateRequest);
  }

  @Override
  public FindCommitResponse findCommit(ScmConnector scmConnector, String commitId) {
    return scmServiceGitClient.findCommit(scmConnector, commitId);
  }

  @Override
  public GetUserReposResponse getUserRepos(ScmConnector scmConnector, PageRequestDTO pageRequest) {
    return scmServiceGitClient.getUserRepos(scmConnector, pageRequest);
  }

  @Override
  public GetUserRepoResponse getRepoDetails(ScmConnector scmConnector) {
    return scmServiceGitClient.getRepoDetails(scmConnector);
  }

  @Override
  public GetUserReposResponse getAllUserRepos(ScmConnector scmConnector) {
    return scmServiceGitClient.getAllUserRepos(scmConnector);
  }

  @Override
  public CreateBranchResponse createNewBranchV2(
      ScmConnector scmConnector, String newBranchName, String baseBranchName) {
    return scmServiceGitClient.createNewBranchV2(scmConnector, newBranchName, baseBranchName);
  }

  @Override
  public CreatePRResponse createPullRequestV2(
      ScmConnector scmConnector, String sourceBranchName, String targetBranchName, String prTitle) {
    return scmServiceGitClient.createPullRequestV2(scmConnector, sourceBranchName, targetBranchName, prTitle);
  }

  @Override
  public RefreshTokenResponse refreshToken(
      ScmConnector scmConnector, String clientId, String clientSecret, String endpoint, String refreshToken) {
    return scmServiceGitClient.refreshToken(scmConnector, clientId, clientSecret, endpoint, refreshToken);
  }

  @Override
  public GenerateYamlResponse autogenerateStageYamlForCI(String cloneUrl) {
    return scmServiceGitClient.autogenerateStageYamlForCI(cloneUrl);
  }

  @Override
  public GetLatestCommitOnFileResponse getLatestCommitOnFile(
      ScmConnector scmConnector, String branchName, String filepath) {
    return scmServiceGitClient.getLatestCommitOnFile(scmConnector, branchName, filepath);
  }

  @Override
  public GitFileResponse getFile(ScmConnector scmConnector, GitFileRequest gitFileContentRequest) {
    return scmServiceGitClient.getFile(scmConnector, gitFileContentRequest);
  }

  @Override
  public GitFileBatchResponse getBatchFile(GitFileBatchRequest gitFileBatchRequest) {
    return scmServiceGitClient.getBatchFile(gitFileBatchRequest);
  }
}
