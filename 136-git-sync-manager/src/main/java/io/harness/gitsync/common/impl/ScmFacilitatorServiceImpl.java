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

import static java.lang.String.format;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequestDTO;
import io.harness.beans.Scope;
import io.harness.beans.request.GitFileRequest;
import io.harness.beans.request.ListFilesInCommitRequest;
import io.harness.beans.response.GitFileResponse;
import io.harness.beans.response.ListFilesInCommitResponse;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientHelper;
import io.harness.gitsync.beans.GitRepositoryDTO;
import io.harness.gitsync.caching.beans.CacheDetails;
import io.harness.gitsync.caching.beans.GitFileCacheKey;
import io.harness.gitsync.caching.beans.GitFileCacheObject;
import io.harness.gitsync.caching.beans.GitFileCacheResponse;
import io.harness.gitsync.caching.service.GitFileCacheService;
import io.harness.gitsync.common.beans.ScmApis;
import io.harness.gitsync.common.dtos.ApiResponseDTO;
import io.harness.gitsync.common.dtos.CreateGitFileRequestDTO;
import io.harness.gitsync.common.dtos.GetLatestCommitOnFileRequestDTO;
import io.harness.gitsync.common.dtos.GitBranchDetailsDTO;
import io.harness.gitsync.common.dtos.GitBranchesResponseDTO;
import io.harness.gitsync.common.dtos.GitRepositoryResponseDTO;
import io.harness.gitsync.common.dtos.ScmCommitFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmCreateFileRequestDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRRequestDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRResponseDTO;
import io.harness.gitsync.common.dtos.ScmFileGitDetailsDTO;
import io.harness.gitsync.common.dtos.ScmGetBranchHeadCommitRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetBranchHeadCommitResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByBranchRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByCommitIdRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmListFilesRequestDTO;
import io.harness.gitsync.common.dtos.ScmListFilesResponseDTO;
import io.harness.gitsync.common.dtos.ScmUpdateFileRequestDTO;
import io.harness.gitsync.common.dtos.UpdateGitFileRequestDTO;
import io.harness.gitsync.common.dtos.UserRepoResponse;
import io.harness.gitsync.common.helper.GitClientEnabledHelper;
import io.harness.gitsync.common.helper.GitSyncConnectorHelper;
import io.harness.gitsync.common.helper.GitSyncUtils;
import io.harness.gitsync.common.mappers.ScmCacheDetailsMapper;
import io.harness.gitsync.common.scmerrorhandling.ScmApiErrorHandlingHelper;
import io.harness.gitsync.common.scmerrorhandling.dtos.ErrorMetadata;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.gitsync.utils.GitProviderUtils;
import io.harness.ng.beans.PageRequest;
import io.harness.product.ci.scm.proto.CreateBranchResponse;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.product.ci.scm.proto.CreatePRResponse;
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.product.ci.scm.proto.GetLatestCommitOnFileResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitResponse;
import io.harness.product.ci.scm.proto.GetUserRepoResponse;
import io.harness.product.ci.scm.proto.GetUserReposResponse;
import io.harness.product.ci.scm.proto.ListBranchesWithDefaultResponse;
import io.harness.product.ci.scm.proto.Repository;
import io.harness.product.ci.scm.proto.UpdateFileResponse;
import io.harness.utils.FilePathUtils;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.RetryUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class ScmFacilitatorServiceImpl implements ScmFacilitatorService {
  GitSyncConnectorHelper gitSyncConnectorHelper;
  @Named("connectorDecoratorService") ConnectorService connectorService;
  ScmOrchestratorService scmOrchestratorService;
  NGFeatureFlagHelperService ngFeatureFlagHelperService;
  GitClientEnabledHelper gitClientEnabledHelper;
  GitFileCacheService gitFileCacheService;

  @Inject
  public ScmFacilitatorServiceImpl(GitSyncConnectorHelper gitSyncConnectorHelper,
      @Named("connectorDecoratorService") ConnectorService connectorService,
      ScmOrchestratorService scmOrchestratorService, NGFeatureFlagHelperService ngFeatureFlagHelperService,
      GitClientEnabledHelper gitClientEnabledHelper, GitFileCacheService gitFileCacheService) {
    this.gitSyncConnectorHelper = gitSyncConnectorHelper;
    this.connectorService = connectorService;
    this.scmOrchestratorService = scmOrchestratorService;
    this.ngFeatureFlagHelperService = ngFeatureFlagHelperService;
    this.gitClientEnabledHelper = gitClientEnabledHelper;
    this.gitFileCacheService = gitFileCacheService;
  }

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
    if (ScmApiErrorHandlingHelper.isFailureResponse(response.getStatus(), scmConnector.getConnectorType())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.LIST_REPOSITORIES, scmConnector.getConnectorType(),
          scmConnector.getUrl(), response.getStatus(), response.getError(),
          ErrorMetadata.builder().connectorRef(connectorRef).build());
    }

    return prepareListRepoResponse(scmConnector, response);
  }

  @Override
  public List<UserRepoResponse> listAllReposForOnboardingFlow(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef) {
    ScmConnector scmConnector =
        gitSyncConnectorHelper.getScmConnector(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef);

    int maxRetries = 1;
    // Adding retry only on manager as delegate already has retry logic
    if (!GitSyncUtils.isExecuteOnDelegate(scmConnector)) {
      maxRetries = 4;
    }

    RetryPolicy<Object> retryPolicy =
        RetryUtils.createRetryPolicy("Scm grpc retry attempt: ", Duration.ofMillis(750), maxRetries, log);
    GetUserReposResponse response =
        Failsafe.with(retryPolicy)
            .get(()
                     -> scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
                         -> scmClientFacilitatorService.listUserRepos(accountIdentifier, orgIdentifier,
                             projectIdentifier, scmConnector, PageRequestDTO.builder().fetchAll(true).build()),
                         scmConnector));

    if (ScmApiErrorHandlingHelper.isFailureResponse(response.getStatus(), scmConnector.getConnectorType())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.LIST_REPOSITORIES, scmConnector.getConnectorType(),
          scmConnector.getUrl(), response.getStatus(), response.getError(),
          ErrorMetadata.builder().connectorRef(connectorRef).build());
    }

    // For hosted flow where we are creating a default docker connector
    gitSyncConnectorHelper.testConnectionAsync(accountIdentifier, null, null, NGCommonEntityConstants.HARNESS_IMAGE);

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

    if (ScmApiErrorHandlingHelper.isFailureResponse(
            listBranchesWithDefaultResponse.getStatus(), scmConnector.getConnectorType())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.LIST_BRANCHES, scmConnector.getConnectorType(),
          scmConnector.getUrl(), listBranchesWithDefaultResponse.getStatus(),
          listBranchesWithDefaultResponse.getError(),
          ErrorMetadata.builder().connectorRef(connectorRef).repoName(repoName).build());
    }

    List<GitBranchDetailsDTO> gitBranches =
        emptyIfNull(listBranchesWithDefaultResponse.getBranchesList())
            .stream()
            .map(branchName -> GitBranchDetailsDTO.builder().name(branchName).build())
            .distinct()
            .collect(Collectors.toList());
    return GitBranchesResponseDTO.builder()
        .branches(gitBranches)
        .defaultBranch(GitBranchDetailsDTO.builder().name(listBranchesWithDefaultResponse.getDefaultBranch()).build())
        .build();
  }

  @Override
  public ScmGetFileResponseDTO getFileByBranch(ScmGetFileByBranchRequestDTO scmGetFileByBranchRequestDTO) {
    Scope scope = scmGetFileByBranchRequestDTO.getScope();

    if (ngFeatureFlagHelperService.isEnabled(scope.getAccountIdentifier(), FeatureName.USE_GET_FILE_V2_GIT_CALL)) {
      log.info("Using V2 GET FILE Call for account id : {}", scope.getAccountIdentifier());
      return getFileByBranchV2(scmGetFileByBranchRequestDTO);
    }

    ScmConnector scmConnector = gitSyncConnectorHelper.getScmConnectorForGivenRepo(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmGetFileByBranchRequestDTO.getConnectorRef(),
        scmGetFileByBranchRequestDTO.getRepoName());

    Optional<ScmGetFileResponseDTO> getFileResponseDTOOptional =
        getFileCacheResponseIfApplicable(scmGetFileByBranchRequestDTO, scmConnector);
    if (getFileResponseDTOOptional.isPresent()) {
      return getFileResponseDTOOptional.get();
    }

    String branchName = isEmpty(scmGetFileByBranchRequestDTO.getBranchName())
        ? getDefaultBranch(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(),
            scmGetFileByBranchRequestDTO.getConnectorRef(), scmGetFileByBranchRequestDTO.getRepoName())
        : scmGetFileByBranchRequestDTO.getBranchName();

    FileContent fileContent = scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
        -> scmClientFacilitatorService.getFile(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
            scope.getProjectIdentifier(), scmGetFileByBranchRequestDTO.getConnectorRef(),
            scmGetFileByBranchRequestDTO.getRepoName(), branchName, scmGetFileByBranchRequestDTO.getFilePath(), null),
        scmConnector);

    GetLatestCommitOnFileResponse getLatestCommitOnFileResponse =
        getLatestCommitOnFile(scope, scmConnector, branchName, fileContent.getPath());

    ApiResponseDTO response = getGetFileAPIResponse(scmConnector, fileContent, getLatestCommitOnFileResponse);

    if (ScmApiErrorHandlingHelper.isFailureResponse(response.getStatusCode(), scmConnector.getConnectorType())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.GET_FILE, scmConnector.getConnectorType(),
          scmConnector.getUrl(), response.getStatusCode(), response.getError(),
          ErrorMetadata.builder()
              .connectorRef(scmGetFileByBranchRequestDTO.getConnectorRef())
              .repoName(scmGetFileByBranchRequestDTO.getRepoName())
              .filepath(scmGetFileByBranchRequestDTO.getFilePath())
              .branchName(branchName)
              .build());
    }

    if (ngFeatureFlagHelperService.isEnabled(scope.getAccountIdentifier(), FeatureName.PIE_NG_GITX_CACHING)) {
      gitFileCacheService.upsertCache(GitFileCacheKey.builder()
                                          .accountIdentifier(scope.getAccountIdentifier())
                                          .completeFilePath(scmGetFileByBranchRequestDTO.getFilePath())
                                          .gitProvider(GitProviderUtils.getGitProvider(scmConnector))
                                          .repoName(scmGetFileByBranchRequestDTO.getRepoName())
                                          .ref(branchName)
                                          .isDefaultBranch(isEmpty(scmGetFileByBranchRequestDTO.getBranchName()))
                                          .build(),
          GitFileCacheObject.builder()
              .fileContent(fileContent.getContent())
              .commitId(fileContent.getCommitId())
              .objectId(fileContent.getBlobId())
              .build());
    }

    return ScmGetFileResponseDTO.builder()
        .fileContent(fileContent.getContent())
        .blobId(fileContent.getBlobId())
        .commitId(getLatestCommitOnFileResponse.getCommitId())
        .branchName(branchName)
        .build();
  }

  @Override
  public ScmGetFileResponseDTO getFileByBranchV2(ScmGetFileByBranchRequestDTO scmGetFileByBranchRequestDTO) {
    Scope scope = scmGetFileByBranchRequestDTO.getScope();
    ScmConnector scmConnector = gitSyncConnectorHelper.getScmConnectorForGivenRepo(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmGetFileByBranchRequestDTO.getConnectorRef(),
        scmGetFileByBranchRequestDTO.getRepoName());

    Optional<ScmGetFileResponseDTO> getFileResponseDTOOptional =
        getFileCacheResponseIfApplicable(scmGetFileByBranchRequestDTO, scmConnector);
    if (getFileResponseDTOOptional.isPresent()) {
      return getFileResponseDTOOptional.get();
    }

    GitFileResponse gitFileResponse =
        scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
            -> scmClientFacilitatorService.getFile(scope, scmConnector,
                GitFileRequest.builder()
                    .branch(scmGetFileByBranchRequestDTO.getBranchName())
                    .commitId(null)
                    .filepath(scmGetFileByBranchRequestDTO.getFilePath())
                    .build()),
            scmConnector);

    if (ScmApiErrorHandlingHelper.isFailureResponse(gitFileResponse.getStatusCode(), scmConnector.getConnectorType())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.GET_FILE, scmConnector.getConnectorType(),
          scmConnector.getUrl(), gitFileResponse.getStatusCode(), gitFileResponse.getError(),
          ErrorMetadata.builder()
              .connectorRef(scmGetFileByBranchRequestDTO.getConnectorRef())
              .repoName(scmGetFileByBranchRequestDTO.getRepoName())
              .filepath(scmGetFileByBranchRequestDTO.getFilePath())
              .branchName(gitFileResponse.getBranch())
              .build());
    }

    if (ngFeatureFlagHelperService.isEnabled(scope.getAccountIdentifier(), FeatureName.PIE_NG_GITX_CACHING)) {
      gitFileCacheService.upsertCache(GitFileCacheKey.builder()
                                          .accountIdentifier(scope.getAccountIdentifier())
                                          .completeFilePath(scmGetFileByBranchRequestDTO.getFilePath())
                                          .gitProvider(GitProviderUtils.getGitProvider(scmConnector))
                                          .repoName(scmGetFileByBranchRequestDTO.getRepoName())
                                          .ref(gitFileResponse.getBranch())
                                          .isDefaultBranch(isEmpty(scmGetFileByBranchRequestDTO.getBranchName()))
                                          .build(),
          GitFileCacheObject.builder()
              .fileContent(gitFileResponse.getContent())
              .commitId(gitFileResponse.getCommitId())
              .objectId(gitFileResponse.getObjectId())
              .build());
    }

    return ScmGetFileResponseDTO.builder()
        .fileContent(gitFileResponse.getContent())
        .blobId(gitFileResponse.getObjectId())
        .commitId(gitFileResponse.getCommitId())
        .branchName(gitFileResponse.getBranch())
        .build();
  }

  @Override
  public ScmGetBranchHeadCommitResponseDTO getBranchHeadCommitDetails(
      ScmGetBranchHeadCommitRequestDTO scmGetBranchHeadCommitRequestDTO) {
    Scope scope = scmGetBranchHeadCommitRequestDTO.getScope();

    ScmConnector scmConnector = gitSyncConnectorHelper.getScmConnectorForGivenRepo(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmGetBranchHeadCommitRequestDTO.getConnectorRef(),
        scmGetBranchHeadCommitRequestDTO.getRepoName());

    GetLatestCommitResponse latestCommitResponse =
        scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
            -> scmClientFacilitatorService.getBranchHeadCommitDetails(
                scope, scmConnector, scmGetBranchHeadCommitRequestDTO.getBranchName()),
            scmConnector);

    if (ScmApiErrorHandlingHelper.isFailureResponse(
            latestCommitResponse.getStatus(), scmConnector.getConnectorType())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.GET_BRANCH_HEAD_COMMIT, scmConnector.getConnectorType(),
          scmConnector.getUrl(), latestCommitResponse.getStatus(), latestCommitResponse.getError(),
          ErrorMetadata.builder()
              .connectorRef(scmGetBranchHeadCommitRequestDTO.getConnectorRef())
              .branchName(scmGetBranchHeadCommitRequestDTO.getBranchName())
              .repoName(scmGetBranchHeadCommitRequestDTO.getRepoName())
              .build());
    }

    return ScmGetBranchHeadCommitResponseDTO.builder()
        .commitId(latestCommitResponse.getCommit().getSha())
        .commitLink(latestCommitResponse.getCommit().getLink())
        .message(latestCommitResponse.getCommit().getMessage())
        .build();
  }

  @Override
  public ScmListFilesResponseDTO listFiles(ScmListFilesRequestDTO scmListFilesRequestDTO) {
    Scope scope = scmListFilesRequestDTO.getScope();
    ScmConnector scmConnector = gitSyncConnectorHelper.getScmConnectorForGivenRepo(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmListFilesRequestDTO.getConnectorRef(),
        scmListFilesRequestDTO.getRepoName());

    ListFilesInCommitResponse listFilesInCommitResponse =
        scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
            -> scmClientFacilitatorService.listFiles(scope, scmConnector,
                ListFilesInCommitRequest.builder()
                    .ref(scmListFilesRequestDTO.getRef())
                    .fileDirectoryPath(scmListFilesRequestDTO.getFileDirectoryPath())
                    .build()),
            scmConnector);

    if (ScmApiErrorHandlingHelper.isFailureResponse(
            listFilesInCommitResponse.getStatusCode(), scmConnector.getConnectorType())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.LIST_FILES, scmConnector.getConnectorType(),
          scmConnector.getUrl(), listFilesInCommitResponse.getStatusCode(), listFilesInCommitResponse.getError(),
          ErrorMetadata.builder()
              .connectorRef(scmListFilesRequestDTO.getConnectorRef())
              .repoName(scmListFilesRequestDTO.getRepoName())
              .filepath(scmListFilesRequestDTO.getFileDirectoryPath())
              .ref(scmListFilesRequestDTO.getRef())
              .build());
    }

    return ScmListFilesResponseDTO.builder()
        .fileGitDetailsDTOList(
            ScmFileGitDetailsDTO.toScmFileGitDetailsDTOList(listFilesInCommitResponse.getFileGitDetailsList()))
        .build();
  }

  @Override
  public ScmCommitFileResponseDTO createFile(ScmCreateFileRequestDTO scmCreateFileRequestDTO) {
    Scope scope = scmCreateFileRequestDTO.getScope();
    ScmConnector scmConnector = gitSyncConnectorHelper.getScmConnectorForGivenRepo(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmCreateFileRequestDTO.getConnectorRef(),
        scmCreateFileRequestDTO.getRepoName());

    if (scmCreateFileRequestDTO.isCommitToNewBranch()) {
      createNewBranch(
          scope, scmConnector, scmCreateFileRequestDTO.getBranchName(), scmCreateFileRequestDTO.getBaseBranch());
    }
    boolean useGitClient = gitClientEnabledHelper.isGitClientEnabledInSettings(scope.getAccountIdentifier());
    if (useGitClient) {
      log.info("Executing using gitClient");
    }

    CreateFileResponse createFileResponse =
        scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
            -> scmClientFacilitatorService.createFile(CreateGitFileRequestDTO.builder()
                                                          .useGitClient(useGitClient)
                                                          .scope(scope)
                                                          .branchName(scmCreateFileRequestDTO.getBranchName())
                                                          .filePath(scmCreateFileRequestDTO.getFilePath())
                                                          .fileContent(scmCreateFileRequestDTO.getFileContent())
                                                          .commitMessage(scmCreateFileRequestDTO.getCommitMessage())
                                                          .scmConnector(scmConnector)
                                                          .build()),
            scmConnector);

    if (ScmApiErrorHandlingHelper.isFailureResponse(createFileResponse.getStatus(), scmConnector.getConnectorType())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.CREATE_FILE, scmConnector.getConnectorType(),
          scmConnector.getUrl(), createFileResponse.getStatus(), createFileResponse.getError(),
          ErrorMetadata.builder()
              .connectorRef(scmCreateFileRequestDTO.getConnectorRef())
              .branchName(scmCreateFileRequestDTO.getBranchName())
              .filepath(scmCreateFileRequestDTO.getFilePath())
              .repoName(scmCreateFileRequestDTO.getRepoName())
              .build());
    }

    if (ngFeatureFlagHelperService.isEnabled(scope.getAccountIdentifier(), FeatureName.PIE_NG_GITX_CACHING)) {
      gitFileCacheService.upsertCache(GitFileCacheKey.builder()
                                          .accountIdentifier(scope.getAccountIdentifier())
                                          .completeFilePath(scmCreateFileRequestDTO.getFilePath())
                                          .gitProvider(GitProviderUtils.getGitProvider(scmConnector))
                                          .repoName(scmCreateFileRequestDTO.getRepoName())
                                          .ref(scmCreateFileRequestDTO.getBranchName())
                                          .build(),
          GitFileCacheObject.builder()
              .fileContent(scmCreateFileRequestDTO.getFileContent())
              .commitId(createFileResponse.getCommitId())
              .objectId(createFileResponse.getBlobId())
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
    ScmConnector scmConnector = gitSyncConnectorHelper.getScmConnectorForGivenRepo(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmUpdateFileRequestDTO.getConnectorRef(),
        scmUpdateFileRequestDTO.getRepoName());

    if (scmUpdateFileRequestDTO.isCommitToNewBranch()) {
      createNewBranch(
          scope, scmConnector, scmUpdateFileRequestDTO.getBranchName(), scmUpdateFileRequestDTO.getBaseBranch());
    }
    boolean isGitClientEnabled = gitClientEnabledHelper.isGitClientEnabledInSettings(scope.getAccountIdentifier());
    if (isGitClientEnabled) {
      log.info("Executing using gitClient");
    }
    UpdateFileResponse updateFileResponse =
        scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
            -> scmClientFacilitatorService.updateFile(UpdateGitFileRequestDTO.builder()
                                                          .useGitClient(isGitClientEnabled)
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

    if (ScmApiErrorHandlingHelper.isFailureResponse(updateFileResponse.getStatus(), scmConnector.getConnectorType())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.UPDATE_FILE, scmConnector.getConnectorType(),
          scmConnector.getUrl(), updateFileResponse.getStatus(), updateFileResponse.getError(),
          ErrorMetadata.builder()
              .connectorRef(scmUpdateFileRequestDTO.getConnectorRef())
              .repoName(scmUpdateFileRequestDTO.getRepoName())
              .filepath(scmUpdateFileRequestDTO.getFilePath())
              .branchName(scmUpdateFileRequestDTO.getBranchName())
              .build());
    }

    if (ngFeatureFlagHelperService.isEnabled(scope.getAccountIdentifier(), FeatureName.PIE_NG_GITX_CACHING)) {
      gitFileCacheService.upsertCache(GitFileCacheKey.builder()
                                          .accountIdentifier(scope.getAccountIdentifier())
                                          .completeFilePath(scmUpdateFileRequestDTO.getFilePath())
                                          .gitProvider(GitProviderUtils.getGitProvider(scmConnector))
                                          .repoName(scmUpdateFileRequestDTO.getRepoName())
                                          .ref(scmUpdateFileRequestDTO.getBranchName())
                                          .build(),
          GitFileCacheObject.builder()
              .fileContent(scmUpdateFileRequestDTO.getFileContent())
              .commitId(updateFileResponse.getCommitId())
              .objectId(updateFileResponse.getBlobId())
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

    if (ScmApiErrorHandlingHelper.isFailureResponse(createPRResponse.getStatus(), scmConnector.getConnectorType())) {
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

    if (ScmApiErrorHandlingHelper.isFailureResponse(fileContent.getStatus(), scmConnector.getConnectorType())) {
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

    if (ScmApiErrorHandlingHelper.isFailureResponse(getUserRepoResponse.getStatus(), scmConnector.getConnectorType())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.GET_DEFAULT_BRANCH, scmConnector.getConnectorType(),
          scmConnector.getUrl(), getUserRepoResponse.getStatus(), getUserRepoResponse.getError(),
          ErrorMetadata.builder().connectorRef(connectorRef).repoName(repoName).build());
    }
    return getUserRepoResponse.getRepo().getBranch();
  }

  @Override
  public String getRepoUrl(Scope scope, String connectorRef, String repoName) {
    final ScmConnector scmConnector = gitSyncConnectorHelper.getScmConnectorForGivenRepo(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), connectorRef, repoName);
    String gitConnectionUrl = scmConnector.getGitConnectionUrl(GitRepositoryDTO.builder().name(repoName).build());

    switch (scmConnector.getConnectorType()) {
      case GITHUB:
        return GitClientHelper.getCompleteHTTPUrlForGithub(gitConnectionUrl);
      case BITBUCKET:
        if (GitClientHelper.isBitBucketSAAS(gitConnectionUrl)) {
          return GitClientHelper.getCompleteHTTPUrlForBitbucketSaas(gitConnectionUrl);
        }
        BitbucketConnectorDTO bitbucketConnectorDTO = (BitbucketConnectorDTO) scmConnector;
        if (GitAuthType.SSH.equals(bitbucketConnectorDTO.getAuthentication().getAuthType())) {
          return GitClientHelper.getCompleteHTTPUrlFromSSHUrlForBitbucketServer(gitConnectionUrl);
        } else {
          return gitConnectionUrl;
        }
      case AZURE_REPO:
        return GitClientHelper.getCompleteHTTPRepoUrlForAzureRepoSaas(gitConnectionUrl);
      default:
        throw new InvalidRequestException(
            format("Connector of given type : %s isn't supported", scmConnector.getConnectorType()));
    }
  }

  private List<GitRepositoryResponseDTO> prepareListRepoResponse(
      ScmConnector scmConnector, GetUserReposResponse response) {
    GitRepositoryDTO gitRepository = scmConnector.getGitRepositoryDetails();
    if (isNotEmpty(gitRepository.getName())) {
      return Collections.singletonList(GitRepositoryResponseDTO.builder().name(gitRepository.getName()).build());
    } else if (isNotEmpty(gitRepository.getOrg()) && isNamespaceNotEmpty(response)) {
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

  @VisibleForTesting
  protected void createNewBranch(Scope scope, ScmConnector scmConnector, String newBranchName, String baseBranchName) {
    String branchName = FilePathUtils.removeStartingAndEndingSlash(newBranchName);
    CreateBranchResponse createBranchResponse =
        scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
            -> scmClientFacilitatorService.createNewBranch(scope, scmConnector, branchName, baseBranchName),
            scmConnector);

    if (ScmApiErrorHandlingHelper.isFailureResponse(
            createBranchResponse.getStatus(), scmConnector.getConnectorType())) {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.CREATE_BRANCH, scmConnector.getConnectorType(),
          scmConnector.getUrl(), createBranchResponse.getStatus(), createBranchResponse.getError(),
          ErrorMetadata.builder().newBranchName(branchName).branchName(baseBranchName).build());
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

  private boolean isNamespaceNotEmpty(GetUserReposResponse response) {
    return isNotEmpty(response.getReposList()) && isNotEmpty(response.getRepos(0).getNamespace());
  }

  private ApiResponseDTO getGetFileAPIResponse(
      ScmConnector scmConnector, FileContent fileContent, GetLatestCommitOnFileResponse getLatestCommitOnFileResponse) {
    if (ScmApiErrorHandlingHelper.isFailureResponse(fileContent.getStatus(), scmConnector.getConnectorType())) {
      return ApiResponseDTO.builder().statusCode(fileContent.getStatus()).error(fileContent.getError()).build();
    }
    if (isNotEmpty(getLatestCommitOnFileResponse.getError())) {
      return ApiResponseDTO.builder().statusCode(400).error(getLatestCommitOnFileResponse.getError()).build();
    }
    return ApiResponseDTO.builder().statusCode(200).build();
  }

  @VisibleForTesting
  GetLatestCommitOnFileResponse getLatestCommitOnFile(
      Scope scope, ScmConnector scmConnector, String branchName, String filePath) {
    return scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
        -> scmClientFacilitatorService.getLatestCommitOnFile(GetLatestCommitOnFileRequestDTO.builder()
                                                                 .branchName(branchName)
                                                                 .filePath(filePath)
                                                                 .scmConnector(scmConnector)
                                                                 .scope(scope)
                                                                 .build()),
        scmConnector);
  }

  private GitFileCacheResponse getFileFromCache(GitFileCacheKey gitFileCacheKey) {
    try {
      return gitFileCacheService.fetchFromCache(gitFileCacheKey);
    } catch (Exception ex) {
      log.error("Faced exception while fetching file from cache, fetching from GIT now", ex);
      return null;
    }
  }

  private GitFileCacheKey getCacheKey(
      ScmGetFileByBranchRequestDTO scmGetFileByBranchRequestDTO, ScmConnector scmConnector) {
    return GitFileCacheKey.builder()
        .accountIdentifier(scmGetFileByBranchRequestDTO.getScope().getAccountIdentifier())
        .completeFilePath(scmGetFileByBranchRequestDTO.getFilePath())
        .repoName(scmGetFileByBranchRequestDTO.getRepoName())
        .gitProvider(GitProviderUtils.getGitProvider(scmConnector))
        .ref(scmGetFileByBranchRequestDTO.getBranchName())
        .isDefaultBranch(isEmpty(scmGetFileByBranchRequestDTO.getBranchName()))
        .build();
  }

  private GitFileCacheKey getCacheKey(
      ScmGetFileByBranchRequestDTO scmGetFileByBranchRequestDTO, ScmConnector scmConnector, String branchName) {
    return GitFileCacheKey.builder()
        .accountIdentifier(scmGetFileByBranchRequestDTO.getScope().getAccountIdentifier())
        .completeFilePath(scmGetFileByBranchRequestDTO.getFilePath())
        .repoName(scmGetFileByBranchRequestDTO.getRepoName())
        .gitProvider(GitProviderUtils.getGitProvider(scmConnector))
        .ref(branchName)
        .isDefaultBranch(isEmpty(branchName))
        .build();
  }

  private ScmGetFileResponseDTO prepareScmGetFileCacheResponse(
      String fileContent, String branchName, String commitId, String objectId, CacheDetails cacheDetails) {
    return ScmGetFileResponseDTO.builder()
        .blobId(objectId)
        .commitId(commitId)
        .branchName(branchName)
        .fileContent(fileContent)
        .cacheDetails(ScmCacheDetailsMapper.getScmCacheDetails(cacheDetails))
        .build();
  }

  private Optional<ScmGetFileResponseDTO> getFileCacheResponseIfApplicable(
      ScmGetFileByBranchRequestDTO scmGetFileByBranchRequestDTO, ScmConnector scmConnector) {
    if (scmGetFileByBranchRequestDTO.isUseCache()) {
      GitFileCacheResponse gitFileCacheResponse =
          getFileFromCache(getCacheKey(scmGetFileByBranchRequestDTO, scmConnector));
      if (gitFileCacheResponse != null) {
        return Optional.of(prepareScmGetFileCacheResponse(gitFileCacheResponse.getGitFileCacheObject().getFileContent(),
            scmGetFileByBranchRequestDTO.getBranchName(), gitFileCacheResponse.getGitFileCacheObject().getCommitId(),
            gitFileCacheResponse.getGitFileCacheObject().getObjectId(), gitFileCacheResponse.getCacheDetails()));
      }
    }
    return Optional.empty();
  }
}
