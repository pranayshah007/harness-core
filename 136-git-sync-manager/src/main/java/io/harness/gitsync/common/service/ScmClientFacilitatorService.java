/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.service;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequestDTO;
import io.harness.beans.Scope;
import io.harness.beans.gitsync.GitPRCreateRequest;
import io.harness.beans.request.GitFileBatchRequest;
import io.harness.beans.request.GitFileRequest;
import io.harness.beans.request.ListFilesInCommitRequest;
import io.harness.beans.response.GitFileBatchResponse;
import io.harness.beans.response.GitFileResponse;
import io.harness.beans.response.ListFilesInCommitResponse;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.delegate.task.scm.GitWebhookTaskType;
import io.harness.gitsync.common.beans.InfoForGitPush;
import io.harness.gitsync.common.dtos.CreateGitFileRequestDTO;
import io.harness.gitsync.common.dtos.CreatePRDTO;
import io.harness.gitsync.common.dtos.GetLatestCommitOnFileRequestDTO;
import io.harness.gitsync.common.dtos.GitDiffResultFileListDTO;
import io.harness.gitsync.common.dtos.GitFileChangeDTO;
import io.harness.gitsync.common.dtos.GitFileContent;
import io.harness.gitsync.common.dtos.UpdateGitFileRequestDTO;
import io.harness.gitsync.common.dtos.UserDetailsRequestDTO;
import io.harness.gitsync.common.dtos.UserDetailsResponseDTO;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.webhook.UpsertWebhookRequestDTO;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.CreateBranchResponse;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.product.ci.scm.proto.CreatePRResponse;
import io.harness.product.ci.scm.proto.CreateWebhookResponse;
import io.harness.product.ci.scm.proto.DeleteFileResponse;
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.product.ci.scm.proto.GetLatestCommitOnFileResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitResponse;
import io.harness.product.ci.scm.proto.GetUserRepoResponse;
import io.harness.product.ci.scm.proto.GetUserReposResponse;
import io.harness.product.ci.scm.proto.ListBranchesWithDefaultResponse;
import io.harness.product.ci.scm.proto.UpdateFileResponse;

import java.util.List;
import java.util.Set;

// Don't inject this directly go through ScmClientOrchestrator.
@OwnedBy(DX)
public interface ScmClientFacilitatorService {
  List<String> listBranchesForRepoByConnector(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorIdentifierRef, String repoURL, PageRequest pageRequest, String searchTerm);

  List<String> listBranchesForRepoByGitSyncConfig(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String yamlGitConfigIdentifier, PageRequest pageRequest, String searchTerm);

  GitFileContent getFileContent(String yamlGitConfigIdentifier, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String filePath, String branch, String commitId);

  FileContent getFile(String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef,
      String repoName, String branchName, String filePath, String commitId);

  CreatePRResponse createPullRequest(
      Scope scope, String connectorRef, String repoName, String sourceBranch, String targetBranch, String title);

  CreatePRDTO createPullRequest(GitPRCreateRequest gitCreatePRRequest);

  List<GitFileChangeDTO> listFilesOfBranches(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String yamlGitConfigIdentifier, Set<String> foldersList, String branchName);

  // Find content of the files in branchName at the latest commit id of the branch
  List<GitFileChangeDTO> listFilesByFilePaths(
      YamlGitConfigDTO yamlGitConfigDTO, List<String> filePaths, String branchName);

  // Find content of the files at given commitId
  List<GitFileChangeDTO> listFilesByCommitId(
      YamlGitConfigDTO yamlGitConfigDTO, List<String> filePaths, String commitId);

  GitDiffResultFileListDTO listCommitsDiffFiles(
      YamlGitConfigDTO yamlGitConfigDTO, String initialCommitId, String finalCommitId);

  List<String> listCommits(YamlGitConfigDTO yamlGitConfigDTO, String branch);

  Commit getLatestCommit(YamlGitConfigDTO yamlGitConfigDTO, String branch);

  CreateFileResponse createFile(InfoForGitPush infoForPush);

  UpdateFileResponse updateFile(InfoForGitPush infoForPush);

  DeleteFileResponse deleteFile(InfoForGitPush infoForPush);

  Commit findCommitById(YamlGitConfigDTO yamlGitConfigDTO, String commitId);

  CreateWebhookResponse upsertWebhook(
      UpsertWebhookRequestDTO upsertWebhookRequestDTO, String target, GitWebhookTaskType gitWebhookTaskType);

  CreateBranchResponse createBranch(InfoForGitPush infoForGitPush, String yamlGitConfigIdentifier);

  GetUserReposResponse listUserRepos(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ScmConnector scmConnector, PageRequestDTO pageRequest);

  ListBranchesWithDefaultResponse listBranches(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ScmConnector scmConnector, PageRequestDTO pageRequest);

  GetUserRepoResponse getRepoDetails(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, ScmConnector scmConnector);

  CreateBranchResponse createNewBranch(
      Scope scope, ScmConnector scmConnector, String newBranchName, String baseBranchName);

  CreateFileResponse createFile(CreateGitFileRequestDTO createGitFileRequestDTO);

  UpdateFileResponse updateFile(UpdateGitFileRequestDTO updateGitFileRequestDTO);

  GetLatestCommitOnFileResponse getLatestCommitOnFile(GetLatestCommitOnFileRequestDTO getLatestCommitOnFileRequestDTO);

  GitFileResponse getFile(Scope scope, ScmConnector scmConnector, GitFileRequest gitFileContentRequest);

  GetLatestCommitResponse getBranchHeadCommitDetails(Scope scope, ScmConnector scmConnector, String branch);

  ListFilesInCommitResponse listFiles(Scope scope, ScmConnector scmConnector, ListFilesInCommitRequest request);

  GitFileBatchResponse getFileBatch(GitFileBatchRequest gitFileBatchRequest);

  UserDetailsResponseDTO getUserDetails(UserDetailsRequestDTO userDetailsRequestDTO);
}
