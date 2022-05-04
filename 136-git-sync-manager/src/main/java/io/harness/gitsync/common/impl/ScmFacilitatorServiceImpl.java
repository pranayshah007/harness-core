/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequestDTO;
import io.harness.beans.Scope;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.gitsync.beans.GitRepositoryDTO;
import io.harness.gitsync.common.beans.InfoForGitPush;
import io.harness.gitsync.common.beans.ScmApis;
import io.harness.gitsync.common.dtos.GitBranchDetailsDTO;
import io.harness.gitsync.common.dtos.GitBranchesResponseDTO;
import io.harness.gitsync.common.dtos.GitRepositoryResponseDTO;
import io.harness.gitsync.common.dtos.ScmCommitFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmCreateFileRequestDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRRequestDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetFileRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmUpdateFileRequestDTO;
import io.harness.gitsync.common.helper.GitSyncConnectorHelper;
import io.harness.gitsync.common.scmerrorhandling.ScmApiErrorHandlingHelper;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.ng.beans.PageRequest;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.product.ci.scm.proto.CreatePRResponse;
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.product.ci.scm.proto.GetUserReposResponse;
import io.harness.product.ci.scm.proto.UpdateFileResponse;
import io.harness.product.ci.scm.proto.ListBranchesWithDefaultResponse;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.PL)
public class ScmFacilitatorServiceImpl implements ScmFacilitatorService {
  GitSyncConnectorHelper gitSyncConnectorHelper;
  ScmOrchestratorService scmOrchestratorService;

  @Override
  public List<String> listBranchesUsingConnector(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorIdentifierRef, String repoURL, PageRequest pageRequest,
      String searchTerm) {
    return scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
        -> scmClientFacilitatorService.listBranchesForRepoByConnector(accountIdentifier, orgIdentifier,
            projectIdentifier, connectorIdentifierRef, repoURL, pageRequest, searchTerm),
        projectIdentifier, orgIdentifier, accountIdentifier, connectorIdentifierRef, null, null);
  }

  @Override
  public List<GitRepositoryResponseDTO> listReposByRefConnector(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorRef, PageRequest pageRequest, String searchTerm) {
    GetUserReposResponse response = scmOrchestratorService.processScmRequestUsingConnectorSettings(
        scmClientFacilitatorService
        -> scmClientFacilitatorService.listUserRepos(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef,
            PageRequestDTO.builder().pageIndex(pageRequest.getPageIndex()).pageSize(pageRequest.getPageSize()).build()),
        projectIdentifier, orgIdentifier, accountIdentifier, connectorRef);

    return prepareListRepoResponse(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, response);
  }

  @Override
  public GitBranchesResponseDTO listBranchesV2(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorRef, String repoName, PageRequest pageRequest, String searchTerm) {
    ListBranchesWithDefaultResponse listBranchesWithDefaultResponse =
        scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
            -> scmClientFacilitatorService.listBranches(accountIdentifier, orgIdentifier, projectIdentifier,
                connectorRef, repoName,
                PageRequestDTO.builder()
                    .pageIndex(pageRequest.getPageIndex())
                    .pageSize(pageRequest.getPageSize())
                    .build()),
            projectIdentifier, orgIdentifier, accountIdentifier, connectorRef);

    List<GitBranchDetailsDTO> gitBranches =
        emptyIfNull(listBranchesWithDefaultResponse.getBranchesList())
            .stream()
            .map(branchName -> GitBranchDetailsDTO.builder().name(branchName).build())
            .collect(Collectors.toList());
    return GitBranchesResponseDTO.builder()
        .branches(gitBranches)
        .defaultBranch(GitBranchDetailsDTO.builder().name(listBranchesWithDefaultResponse.getDefaultBranch()).build())
        .build();
  }

  private List<GitRepositoryResponseDTO> prepareListRepoResponse(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorRef, GetUserReposResponse response) {
    ScmConnector scmConnector =
        gitSyncConnectorHelper.getScmConnector(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef);
    GitRepositoryDTO gitRepository = scmConnector.getGitRepositoryDetails();
    if (isNotEmpty(gitRepository.getName())) {
      return Collections.singletonList(GitRepositoryResponseDTO.builder().name(gitRepository.getName()).build());
    } else if (isNotEmpty(gitRepository.getOrg())) {
      return emptyIfNull(response.getReposList())
          .stream()
          .filter(repository -> repository.getNamespace().equals(gitRepository.getOrg()))
          .map(repository -> GitRepositoryResponseDTO.builder().name(repository.getName()).build())
          .collect(Collectors.toList());
    } else {
      return emptyIfNull(response.getReposList())
          .stream()
          .map(repository -> GitRepositoryResponseDTO.builder().name(repository.getName()).build())
          .collect(Collectors.toList());
    }
  }

  @Override
  public ScmCommitFileResponseDTO createFile(ScmCreateFileRequestDTO scmCreateFileRequestDTO) {
    Scope scope = scmCreateFileRequestDTO.getScope();
    // TODO Put validations over request here
    ScmConnector scmConnector = gitSyncConnectorHelper.getScmConnectorForGivenRepo(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmCreateFileRequestDTO.getConnectorRef(),
        scmCreateFileRequestDTO.getRepoName());
    CreateFileResponse createFileResponse =
        scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
            -> scmClientFacilitatorService.createFile(InfoForGitPush.builder()
                                                          .accountId(scope.getAccountIdentifier())
                                                          .orgIdentifier(scope.getOrgIdentifier())
                                                          .projectIdentifier(scope.getProjectIdentifier())
                                                          .filePath(scmCreateFileRequestDTO.getFilePath())
                                                          .baseBranch(scmCreateFileRequestDTO.getBaseBranch())
                                                          .branch(scmCreateFileRequestDTO.getBranchName())
                                                          .commitMsg(scmCreateFileRequestDTO.getCommitMessage())
                                                          .completeFilePath(scmCreateFileRequestDTO.getFilePath())
                                                          .isNewBranch(scmCreateFileRequestDTO.isCommitToNewBranch())
                                                          .scmConnector(scmConnector)
                                                          .yaml(scmCreateFileRequestDTO.getFileContent())
                                                          .build()),
            scope.getProjectIdentifier(), scope.getOrgIdentifier(), scope.getAccountIdentifier(),
            scmCreateFileRequestDTO.getConnectorRef());

    // Put Error Handling
    ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.CREATE_FILE,
        gitSyncConnectorHelper.getScmConnectorByRef(scope, scmCreateFileRequestDTO.getConnectorRef())
            .getConnectorType(),
        createFileResponse.getStatus(), createFileResponse.getError());

    return ScmCommitFileResponseDTO.builder()
        .commitId(createFileResponse.getCommitId())
        .blobId(createFileResponse.getBlobId())
        .build();
  }

  @Override
  public ScmCommitFileResponseDTO updateFile(ScmUpdateFileRequestDTO scmUpdateFileRequestDTO) {
    Scope scope = scmUpdateFileRequestDTO.getScope();
    // TODO Put validations over request here
    ScmConnector scmConnector = gitSyncConnectorHelper.getScmConnectorForGivenRepo(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmUpdateFileRequestDTO.getConnectorRef(),
        scmUpdateFileRequestDTO.getRepoName());
    UpdateFileResponse updateFileResponse =
        scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
            -> scmClientFacilitatorService.updateFile(InfoForGitPush.builder()
                                                          .accountId(scope.getAccountIdentifier())
                                                          .orgIdentifier(scope.getOrgIdentifier())
                                                          .projectIdentifier(scope.getProjectIdentifier())
                                                          .filePath(scmUpdateFileRequestDTO.getFilePath())
                                                          .baseBranch(scmUpdateFileRequestDTO.getBaseBranch())
                                                          .branch(scmUpdateFileRequestDTO.getBranchName())
                                                          .commitMsg(scmUpdateFileRequestDTO.getCommitMessage())
                                                          .completeFilePath(scmUpdateFileRequestDTO.getFilePath())
                                                          .isNewBranch(scmUpdateFileRequestDTO.isCommitToNewBranch())
                                                          .scmConnector(scmConnector)
                                                          .oldFileSha(scmUpdateFileRequestDTO.getOldFileSha())
                                                          .yaml(scmUpdateFileRequestDTO.getFileContent())
                                                          .build()),
            scope.getProjectIdentifier(), scope.getOrgIdentifier(), scope.getAccountIdentifier(),
            scmUpdateFileRequestDTO.getConnectorRef());

    // Put Error Handling

    return ScmCommitFileResponseDTO.builder()
        .commitId(updateFileResponse.getCommitId())
        .blobId(updateFileResponse.getBlobId())
        .build();
  }

  @Override
  public ScmCreatePRResponseDTO createPR(ScmCreatePRRequestDTO scmCreatePRRequestDTO) {
    Scope scope = scmCreatePRRequestDTO.getScope();
    CreatePRResponse createPRResponse =
        scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
            -> scmClientFacilitatorService.createPullRequest(scope, scmCreatePRRequestDTO.getConnectorRef(),
                scmCreatePRRequestDTO.getRepoName(), scmCreatePRRequestDTO.getSourceBranch(),
                scmCreatePRRequestDTO.getTargetBranch(), scmCreatePRRequestDTO.getTitle()),
            scope.getProjectIdentifier(), scope.getOrgIdentifier(), scope.getAccountIdentifier(),
            scmCreatePRRequestDTO.getConnectorRef());

    ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.CREATE_PULL_REQUEST,
        gitSyncConnectorHelper.getScmConnectorByRef(scope, scmCreatePRRequestDTO.getConnectorRef()).getConnectorType(),
        createPRResponse.getStatus(), createPRResponse.getError());

    return ScmCreatePRResponseDTO.builder().prNumber(createPRResponse.getNumber()).build();
  }

  @Override
  public ScmGetFileResponseDTO getFile(ScmGetFileRequestDTO scmGetFileRequestDTO) {
    Scope scope = scmGetFileRequestDTO.getScope();
    FileContent fileContent = scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
        -> scmClientFacilitatorService.getFile(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
            scope.getProjectIdentifier(), scmGetFileRequestDTO.getConnectorRef(), scmGetFileRequestDTO.getRepoName(),
            scmGetFileRequestDTO.getBranchName(), scmGetFileRequestDTO.getFilePath(),
            scmGetFileRequestDTO.getCommitId()),
        scope.getProjectIdentifier(), scope.getOrgIdentifier(), scope.getAccountIdentifier(),
        scmGetFileRequestDTO.getConnectorRef());

    ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.GET_FILE,
        gitSyncConnectorHelper.getScmConnectorByRef(scope, scmGetFileRequestDTO.getConnectorRef()).getConnectorType(),
        fileContent.getStatus(), fileContent.getError());

    return ScmGetFileResponseDTO.builder()
        .fileContent(fileContent.getContent())
        .blobId(fileContent.getBlobId())
        .commitId(fileContent.getCommitId())
        .build();
  }
}
