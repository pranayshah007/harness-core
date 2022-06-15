/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequestDTO;
import io.harness.beans.Scope;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.gitsync.beans.GitRepositoryDTO;
import io.harness.gitsync.common.beans.ScmApis;
import io.harness.gitsync.common.dtos.CreateGitFileRequestDTO;
import io.harness.gitsync.common.dtos.GitBranchDetailsDTO;
import io.harness.gitsync.common.dtos.GitBranchesResponseDTO;
import io.harness.gitsync.common.dtos.GitRepositoryResponseDTO;
import io.harness.gitsync.common.dtos.ScmCommitFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmCreateFileRequestDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRRequestDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByBranchRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByCommitIdRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmUpdateFileRequestDTO;
import io.harness.gitsync.common.dtos.UpdateGitFileRequestDTO;
import io.harness.gitsync.common.dtos.UserRepoResponse;
import io.harness.gitsync.common.helper.GitSyncConnectorHelper;
import io.harness.gitsync.common.scmerrorhandling.ScmApiErrorHandlingHelper;
import io.harness.gitsync.common.scmerrorhandling.dtos.ErrorMetadata;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.ng.beans.PageRequest;
import io.harness.product.ci.scm.proto.CreateBranchResponse;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.product.ci.scm.proto.CreatePRResponse;
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.product.ci.scm.proto.GetUserRepoResponse;
import io.harness.product.ci.scm.proto.GetUserReposResponse;
import io.harness.product.ci.scm.proto.ListBranchesWithDefaultResponse;
import io.harness.product.ci.scm.proto.Repository;
import io.harness.product.ci.scm.proto.UpdateFileResponse;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
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
    ScmConnector scmConnector =
        gitSyncConnectorHelper.getScmConnector(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef);
    GetUserReposResponse response = scmOrchestratorService.processScmRequestUsingConnectorSettings(
        scmClientFacilitatorService
        -> scmClientFacilitatorService.listUserRepos(accountIdentifier, orgIdentifier, projectIdentifier, scmConnector,
            PageRequestDTO.builder().pageIndex(pageRequest.getPageIndex()).pageSize(pageRequest.getPageSize()).build()),
        scmConnector);
    if (isFailureResponse(response.getStatus())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.LIST_REPOSITORIES, scmConnector.getConnectorType(),
          scmConnector.getUrl(), response.getStatus(), response.getError(),
          ErrorMetadata.builder().connectorRef(connectorRef).build());
    }

    return prepareListRepoResponse(scmConnector, response);
  }

  @Override
  public List<UserRepoResponse> listAllReposByRefConnector(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef) {
    ScmConnector scmConnector =
        gitSyncConnectorHelper.getScmConnector(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef);
    GetUserReposResponse response =
        scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
            -> scmClientFacilitatorService.listUserRepos(accountIdentifier, orgIdentifier, projectIdentifier,
                scmConnector, PageRequestDTO.builder().fetchAll(true).build()),
            scmConnector);
    if (isFailureResponse(response.getStatus())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.LIST_REPOSITORIES, scmConnector.getConnectorType(),
          scmConnector.getUrl(), response.getStatus(), response.getError(),
          ErrorMetadata.builder().connectorRef(connectorRef).build());
    }

    return convertToUserRepo(response.getReposList());
  }

  @Override
  public GitBranchesResponseDTO listBranchesV2(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorRef, String repoName, PageRequest pageRequest, String searchTerm) {
    final ScmConnector scmConnector = gitSyncConnectorHelper.getScmConnectorForGivenRepo(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, repoName);
    ListBranchesWithDefaultResponse listBranchesWithDefaultResponse =
        scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
            -> scmClientFacilitatorService.listBranches(accountIdentifier, orgIdentifier, projectIdentifier,
                scmConnector,
                PageRequestDTO.builder()
                    .pageIndex(pageRequest.getPageIndex())
                    .pageSize(pageRequest.getPageSize())
                    .build()),
            scmConnector);

    if (isFailureResponse(listBranchesWithDefaultResponse.getStatus())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.LIST_BRANCHES, scmConnector.getConnectorType(),
          scmConnector.getUrl(), listBranchesWithDefaultResponse.getStatus(),
          listBranchesWithDefaultResponse.getError(),
          ErrorMetadata.builder().connectorRef(connectorRef).repoName(repoName).build());
    }

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

  @Override
  public ScmGetFileResponseDTO getFileByBranch(ScmGetFileByBranchRequestDTO scmGetFileByBranchRequestDTO) {
    Scope scope = scmGetFileByBranchRequestDTO.getScope();
    String branchName = isEmpty(scmGetFileByBranchRequestDTO.getBranchName())
        ? getDefaultBranch(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(),
            scmGetFileByBranchRequestDTO.getConnectorRef(), scmGetFileByBranchRequestDTO.getRepoName())
        : scmGetFileByBranchRequestDTO.getBranchName();

    ScmConnector scmConnector = gitSyncConnectorHelper.getScmConnectorForGivenRepo(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmGetFileByBranchRequestDTO.getConnectorRef(),
        scmGetFileByBranchRequestDTO.getRepoName());

    FileContent fileContent = scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
        -> scmClientFacilitatorService.getFile(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
            scope.getProjectIdentifier(), scmGetFileByBranchRequestDTO.getConnectorRef(),
            scmGetFileByBranchRequestDTO.getRepoName(), branchName, scmGetFileByBranchRequestDTO.getFilePath(), null),
        scmConnector);

    if (isFailureResponse(fileContent.getStatus())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.GET_FILE, scmConnector.getConnectorType(),
          scmConnector.getUrl(), fileContent.getStatus(), fileContent.getError(),
          ErrorMetadata.builder()
              .connectorRef(scmGetFileByBranchRequestDTO.getConnectorRef())
              .repoName(scmGetFileByBranchRequestDTO.getRepoName())
              .filepath(scmGetFileByBranchRequestDTO.getFilePath())
              .branchName(scmGetFileByBranchRequestDTO.getBranchName())
              .build());
    }

    return ScmGetFileResponseDTO.builder()
        .fileContent(fileContent.getContent())
        .blobId(fileContent.getBlobId())
        .commitId(fileContent.getCommitId())
        .branchName(branchName)
        .build();
  }

  @Override
  public ScmCommitFileResponseDTO createFile(ScmCreateFileRequestDTO scmCreateFileRequestDTO) {
    Scope scope = scmCreateFileRequestDTO.getScope();
    // TODO Put validations over request here
    ScmConnector scmConnector = gitSyncConnectorHelper.getScmConnectorForGivenRepo(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmCreateFileRequestDTO.getConnectorRef(),
        scmCreateFileRequestDTO.getRepoName());

    if (scmCreateFileRequestDTO.isCommitToNewBranch()) {
      createNewBranch(
          scope, scmConnector, scmCreateFileRequestDTO.getBranchName(), scmCreateFileRequestDTO.getBaseBranch());
    }

    CreateFileResponse createFileResponse =
        scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
            -> scmClientFacilitatorService.createFile(CreateGitFileRequestDTO.builder()
                                                          .scope(scope)
                                                          .branchName(scmCreateFileRequestDTO.getBranchName())
                                                          .filePath(scmCreateFileRequestDTO.getFilePath())
                                                          .fileContent(scmCreateFileRequestDTO.getFileContent())
                                                          .commitMessage(scmCreateFileRequestDTO.getCommitMessage())
                                                          .scmConnector(scmConnector)
                                                          .build()),
            scmConnector);

    if (isFailureResponse(createFileResponse.getStatus())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.CREATE_FILE, scmConnector.getConnectorType(),
          scmConnector.getUrl(), createFileResponse.getStatus(), createFileResponse.getError(),
          ErrorMetadata.builder()
              .connectorRef(scmCreateFileRequestDTO.getConnectorRef())
              .branchName(scmCreateFileRequestDTO.getBranchName())
              .filepath(scmCreateFileRequestDTO.getFilePath())
              .repoName(scmCreateFileRequestDTO.getRepoName())
              .build());
    }

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

    if (scmUpdateFileRequestDTO.isCommitToNewBranch()) {
      createNewBranch(
          scope, scmConnector, scmUpdateFileRequestDTO.getBranchName(), scmUpdateFileRequestDTO.getBaseBranch());
    }

    UpdateFileResponse updateFileResponse =
        scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
            -> scmClientFacilitatorService.updateFile(UpdateGitFileRequestDTO.builder()
                                                          .scope(scope)
                                                          .branchName(scmUpdateFileRequestDTO.getBranchName())
                                                          .filePath(scmUpdateFileRequestDTO.getFilePath())
                                                          .fileContent(scmUpdateFileRequestDTO.getFileContent())
                                                          .commitMessage(scmUpdateFileRequestDTO.getCommitMessage())
                                                          .oldFileSha(scmUpdateFileRequestDTO.getOldFileSha())
                                                          .oldCommitId(scmUpdateFileRequestDTO.getOldCommitId())
                                                          .scmConnector(scmConnector)
                                                          .build()),
            scmConnector);

    if (isFailureResponse(updateFileResponse.getStatus())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.UPDATE_FILE, scmConnector.getConnectorType(),
          scmConnector.getUrl(), updateFileResponse.getStatus(), updateFileResponse.getError(),
          ErrorMetadata.builder()
              .connectorRef(scmUpdateFileRequestDTO.getConnectorRef())
              .repoName(scmUpdateFileRequestDTO.getRepoName())
              .filepath(scmUpdateFileRequestDTO.getFilePath())
              .branchName(scmUpdateFileRequestDTO.getBranchName())
              .build());
    }

    return ScmCommitFileResponseDTO.builder()
        .commitId(updateFileResponse.getCommitId())
        .blobId(updateFileResponse.getBlobId())
        .build();
  }

  @Override
  public ScmCreatePRResponseDTO createPR(ScmCreatePRRequestDTO scmCreatePRRequestDTO) {
    Scope scope = scmCreatePRRequestDTO.getScope();
    ScmConnector scmConnector =
        gitSyncConnectorHelper.getScmConnectorForGivenRepo(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
            scope.getProjectIdentifier(), scmCreatePRRequestDTO.getConnectorRef(), scmCreatePRRequestDTO.getRepoName());

    CreatePRResponse createPRResponse =
        scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
            -> scmClientFacilitatorService.createPullRequest(scope, scmCreatePRRequestDTO.getConnectorRef(),
                scmCreatePRRequestDTO.getRepoName(), scmCreatePRRequestDTO.getSourceBranch(),
                scmCreatePRRequestDTO.getTargetBranch(), scmCreatePRRequestDTO.getTitle()),
            scmConnector);

    if (isFailureResponse(createPRResponse.getStatus())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.CREATE_PULL_REQUEST, scmConnector.getConnectorType(),
          scmConnector.getUrl(), createPRResponse.getStatus(), createPRResponse.getError(),
          ErrorMetadata.builder()
              .connectorRef(scmCreatePRRequestDTO.getConnectorRef())
              .branchName(scmCreatePRRequestDTO.getSourceBranch())
              .targetBranchName(scmCreatePRRequestDTO.getTargetBranch())
              .repoName(scmCreatePRRequestDTO.getRepoName())
              .build());
    }

    return ScmCreatePRResponseDTO.builder().prNumber(createPRResponse.getNumber()).build();
  }

  @Override
  public ScmGetFileResponseDTO getFileByCommitId(ScmGetFileByCommitIdRequestDTO scmGetFileByCommitIdRequestDTO) {
    Scope scope = scmGetFileByCommitIdRequestDTO.getScope();

    ScmConnector scmConnector = gitSyncConnectorHelper.getScmConnectorForGivenRepo(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmGetFileByCommitIdRequestDTO.getConnectorRef(),
        scmGetFileByCommitIdRequestDTO.getRepoName());

    FileContent fileContent = scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
        -> scmClientFacilitatorService.getFile(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
            scope.getProjectIdentifier(), scmGetFileByCommitIdRequestDTO.getConnectorRef(),
            scmGetFileByCommitIdRequestDTO.getRepoName(), null, scmGetFileByCommitIdRequestDTO.getFilePath(),
            scmGetFileByCommitIdRequestDTO.getCommitId()),
        scmConnector);

    if (isFailureResponse(fileContent.getStatus())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.GET_FILE, scmConnector.getConnectorType(),
          scmConnector.getUrl(), fileContent.getStatus(), fileContent.getError(),
          ErrorMetadata.builder()
              .connectorRef(scmGetFileByCommitIdRequestDTO.getConnectorRef())
              .repoName(scmGetFileByCommitIdRequestDTO.getRepoName())
              .filepath(scmGetFileByCommitIdRequestDTO.getFilePath())
              .build());
    }

    return ScmGetFileResponseDTO.builder()
        .fileContent(fileContent.getContent())
        .blobId(fileContent.getBlobId())
        .commitId(fileContent.getCommitId())
        .build();
  }

  @Override
  public String getDefaultBranch(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef, String repoName) {
    final ScmConnector scmConnector = gitSyncConnectorHelper.getScmConnectorForGivenRepo(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, repoName);
    GetUserRepoResponse getUserRepoResponse =
        scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
            -> scmClientFacilitatorService.getRepoDetails(
                accountIdentifier, orgIdentifier, projectIdentifier, scmConnector),
            scmConnector);

    if (isFailureResponse(getUserRepoResponse.getStatus())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.GET_DEFAULT_BRANCH, scmConnector.getConnectorType(),
          scmConnector.getUrl(), getUserRepoResponse.getStatus(), getUserRepoResponse.getError(),
          ErrorMetadata.builder().connectorRef(connectorRef).repoName(repoName).build());
    }
    return getUserRepoResponse.getRepo().getBranch();
  }

  private List<GitRepositoryResponseDTO> prepareListRepoResponse(
      ScmConnector scmConnector, GetUserReposResponse response) {
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

  private boolean isFailureResponse(int statusCode) {
    return statusCode >= 300;
  }

  @VisibleForTesting
  protected void createNewBranch(Scope scope, ScmConnector scmConnector, String newBranchName, String baseBranchName) {
    CreateBranchResponse createBranchResponse =
        scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
            -> scmClientFacilitatorService.createNewBranch(scope, scmConnector, newBranchName, baseBranchName),
            scmConnector);

    if (isFailureResponse(createBranchResponse.getStatus())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.CREATE_BRANCH, scmConnector.getConnectorType(),
          scmConnector.getUrl(), createBranchResponse.getStatus(), createBranchResponse.getError(),
          ErrorMetadata.builder().newBranchName(newBranchName).branchName(baseBranchName).build());
    }
  }

  private List<UserRepoResponse> convertToUserRepo(List<Repository> allUserRepos) {
    ArrayList userRepoResponses = new ArrayList();
    for (Repository userRepo : allUserRepos) {
      userRepoResponses.add(
          UserRepoResponse.builder().namespace(userRepo.getNamespace()).name(userRepo.getName()).build());
    }
    return userRepoResponses;
  }
}
