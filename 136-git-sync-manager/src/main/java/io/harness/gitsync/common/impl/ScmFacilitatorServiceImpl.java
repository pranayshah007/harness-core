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

import static software.wings.beans.TaskType.SCM_BATCH_GET_FILE_TASK;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.GetBatchFileRequestIdentifier;
import io.harness.beans.PageRequestDTO;
import io.harness.beans.Scope;
import io.harness.beans.request.GitFileBatchRequest;
import io.harness.beans.request.GitFileRequest;
import io.harness.beans.request.GitFileRequestV2;
import io.harness.beans.request.ListFilesInCommitRequest;
import io.harness.beans.response.GitFileBatchResponse;
import io.harness.beans.response.GitFileResponse;
import io.harness.beans.response.ListFilesInCommitResponse;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.AccountId;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.exception.WingsException;
import io.harness.gitsync.beans.GitRepositoryDTO;
import io.harness.gitsync.caching.beans.CacheDetails;
import io.harness.gitsync.caching.beans.GitFileCacheDeleteResult;
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
import io.harness.gitsync.common.dtos.ScmGetBatchFileRequestIdentifier;
import io.harness.gitsync.common.dtos.ScmGetBatchFilesByBranchRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetBatchFilesResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetBranchHeadCommitRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetBranchHeadCommitResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByBranchRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByCommitIdRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetFileResponseV2DTO;
import io.harness.gitsync.common.dtos.ScmGetFileUrlRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileUrlResponseDTO;
import io.harness.gitsync.common.dtos.ScmListFilesRequestDTO;
import io.harness.gitsync.common.dtos.ScmListFilesResponseDTO;
import io.harness.gitsync.common.dtos.ScmUpdateFileRequestDTO;
import io.harness.gitsync.common.dtos.UpdateGitFileRequestDTO;
import io.harness.gitsync.common.dtos.UserDetailsRequestDTO;
import io.harness.gitsync.common.dtos.UserDetailsResponseDTO;
import io.harness.gitsync.common.dtos.UserRepoResponse;
import io.harness.gitsync.common.helper.GitClientEnabledHelper;
import io.harness.gitsync.common.helper.GitDefaultBranchCacheHelper;
import io.harness.gitsync.common.helper.GitFilePathHelper;
import io.harness.gitsync.common.helper.GitRepoUrlHelper;
import io.harness.gitsync.common.helper.GitSyncConnectorHelper;
import io.harness.gitsync.common.helper.GitSyncUtils;
import io.harness.gitsync.common.helper.ScmExceptionUtils;
import io.harness.gitsync.common.mappers.ScmCacheDetailsMapper;
import io.harness.gitsync.common.scmerrorhandling.ScmApiErrorHandlingHelper;
import io.harness.gitsync.common.scmerrorhandling.dtos.ErrorMetadata;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.gitsync.core.beans.GitBatchFileFetchRunnableParams;
import io.harness.gitsync.core.beans.GitFileFetchRunnableParams;
import io.harness.gitsync.core.runnable.GitBackgroundCacheRefreshHelper;
import io.harness.gitsync.utils.GitProviderUtils;
import io.harness.grpc.DelegateServiceGrpcClient;
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
import io.harness.utils.ConnectorUtils;
import io.harness.utils.FilePathUtils;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.RetryUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class ScmFacilitatorServiceImpl implements ScmFacilitatorService {
  private static final Integer MAX_ALLOWED_BATCH_FILE_REQUESTS_COUNT = 20;

  GitSyncConnectorHelper gitSyncConnectorHelper;
  ConnectorService connectorService;
  ScmOrchestratorService scmOrchestratorService;
  NGFeatureFlagHelperService ngFeatureFlagHelperService;
  GitClientEnabledHelper gitClientEnabledHelper;
  GitFileCacheService gitFileCacheService;
  GitFilePathHelper gitFilePathHelper;
  DelegateServiceGrpcClient delegateServiceGrpcClient;
  GitBackgroundCacheRefreshHelper gitBackgroundCacheRefreshHelper;
  GitDefaultBranchCacheHelper gitDefaultBranchCacheHelper;
  GitRepoUrlHelper gitRepoUrlHelper;

  @Inject
  public ScmFacilitatorServiceImpl(GitSyncConnectorHelper gitSyncConnectorHelper,
      @Named("connectorDecoratorService") ConnectorService connectorService,
      ScmOrchestratorService scmOrchestratorService, NGFeatureFlagHelperService ngFeatureFlagHelperService,
      GitClientEnabledHelper gitClientEnabledHelper, GitFileCacheService gitFileCacheService,
      GitFilePathHelper gitFilePathHelper, DelegateServiceGrpcClient delegateServiceGrpcClient,
      GitBackgroundCacheRefreshHelper gitBackgroundCacheRefreshHelper,
      GitDefaultBranchCacheHelper gitDefaultBranchCacheHelper, GitRepoUrlHelper gitRepoUrlHelper) {
    this.gitSyncConnectorHelper = gitSyncConnectorHelper;
    this.connectorService = connectorService;
    this.scmOrchestratorService = scmOrchestratorService;
    this.ngFeatureFlagHelperService = ngFeatureFlagHelperService;
    this.gitClientEnabledHelper = gitClientEnabledHelper;
    this.gitFileCacheService = gitFileCacheService;
    this.gitFilePathHelper = gitFilePathHelper;
    this.delegateServiceGrpcClient = delegateServiceGrpcClient;
    this.gitBackgroundCacheRefreshHelper = gitBackgroundCacheRefreshHelper;
    this.gitDefaultBranchCacheHelper = gitDefaultBranchCacheHelper;
    this.gitRepoUrlHelper = gitRepoUrlHelper;
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
      String projectIdentifier, String connectorRef, PageRequest pageRequest, String searchTerm,
      boolean applyGitXRepoAllowListFilter) {
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

    List<GitBranchDetailsDTO> gitBranches = prepareGitBranchList(listBranchesWithDefaultResponse);
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

    String branchName;
    if (isEmpty(scmGetFileByBranchRequestDTO.getCommitId())) {
      branchName = isEmpty(scmGetFileByBranchRequestDTO.getBranchName())
          ? getDefaultBranch(scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(),
              scmGetFileByBranchRequestDTO.getConnectorRef(), scmGetFileByBranchRequestDTO.getRepoName())
          : scmGetFileByBranchRequestDTO.getBranchName();
    } else {
      branchName = "";
    }

    FileContent fileContent = scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
        -> scmClientFacilitatorService.getFile(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
            scope.getProjectIdentifier(), scmGetFileByBranchRequestDTO.getConnectorRef(),
            scmGetFileByBranchRequestDTO.getRepoName(), branchName, scmGetFileByBranchRequestDTO.getFilePath(),
            scmGetFileByBranchRequestDTO.getCommitId()),
        scmConnector);

    GetLatestCommitOnFileResponse getLatestCommitOnFileResponse = null;
    if (isEmpty(scmGetFileByBranchRequestDTO.getCommitId())) {
      getLatestCommitOnFileResponse = getLatestCommitOnFile(scope, scmConnector, branchName, fileContent.getPath());

      ApiResponseDTO response = getGetFileAPIResponse(scmConnector, fileContent, getLatestCommitOnFileResponse);

      if (ScmApiErrorHandlingHelper.isFailureResponse(response.getStatusCode(), scmConnector.getConnectorType())) {
        try {
          ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.GET_FILE, scmConnector.getConnectorType(),
              scmConnector.getUrl(), response.getStatusCode(), response.getError(),
              ErrorMetadata.builder()
                  .connectorRef(scmGetFileByBranchRequestDTO.getConnectorRef())
                  .repoName(scmGetFileByBranchRequestDTO.getRepoName())
                  .filepath(scmGetFileByBranchRequestDTO.getFilePath())
                  .branchName(branchName)
                  .build());
        } catch (WingsException wingsException) {
          if (ScmExceptionUtils.isNestedScmBadRequestException(wingsException)) {
            invalidateGitFileCache(scope.getAccountIdentifier(), scmGetFileByBranchRequestDTO.getFilePath(),
                scmConnector, scmGetFileByBranchRequestDTO.getRepoName(), branchName);
          }
          throw wingsException;
        }
      }
    }

    String commitId =
        getLatestCommitOnFileResponse == null ? fileContent.getCommitId() : getLatestCommitOnFileResponse.getCommitId();

    try {
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
    } catch (Exception exception) {
      handleUpsertCacheFailure(exception);
    }

    return ScmGetFileResponseDTO.builder()
        .fileContent(fileContent.getContent())
        .blobId(fileContent.getBlobId())
        .commitId(commitId)
        .branchName(branchName)
        .build();
  }

  @Override
  public ScmGetFileResponseDTO getFileByBranchV2(ScmGetFileByBranchRequestDTO scmGetFileByBranchRequestDTO) {
    Scope scope = scmGetFileByBranchRequestDTO.getScope();
    ScmConnector scmConnector = scmGetFileByBranchRequestDTO.getScmConnector() != null
        ? scmGetFileByBranchRequestDTO.getScmConnector()
        : gitSyncConnectorHelper.getScmConnectorForGivenRepo(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
            scope.getProjectIdentifier(), scmGetFileByBranchRequestDTO.getConnectorRef(),
            scmGetFileByBranchRequestDTO.getRepoName());

    String requestBranch = scmGetFileByBranchRequestDTO.getBranchName();

    String resolvedBranch = gitDefaultBranchCacheHelper.setDefaultBranchIfInputBranchEmpty(scope.getAccountIdentifier(),
        gitSyncConnectorHelper.getScmConnectorForGivenRepo(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
            scope.getProjectIdentifier(), scmGetFileByBranchRequestDTO.getConnectorRef(),
            scmGetFileByBranchRequestDTO.getRepoName()),
        scmGetFileByBranchRequestDTO.getRepoName(), requestBranch);

    Optional<ScmGetFileResponseDTO> getFileResponseDTOOptional =
        getFileCacheResponseIfApplicable(scmGetFileByBranchRequestDTO, scmConnector);
    if (getFileResponseDTOOptional.isPresent()) {
      return getFileResponseDTOOptional.get();
    }

    GitFileResponse gitFileResponse =
        scmOrchestratorService.processScmRequestUsingConnectorSettings(scmClientFacilitatorService
            -> scmClientFacilitatorService.getFile(scope, scmConnector,
                GitFileRequest.builder()
                    .branch(resolvedBranch)
                    .commitId(scmGetFileByBranchRequestDTO.getCommitId())
                    .filepath(scmGetFileByBranchRequestDTO.getFilePath())
                    .getOnlyFileContent(scmGetFileByBranchRequestDTO.isGetOnlyFileContent())
                    .build()),
            scmConnector);

    checkIfErrorResponse(scope, gitFileResponse, scmConnector, scmGetFileByBranchRequestDTO.getConnectorRef(),
        scmGetFileByBranchRequestDTO.getRepoName(), scmGetFileByBranchRequestDTO.getFilePath());

    cacheGetFileOperationResponse(scope, gitFileResponse, scmConnector, scmGetFileByBranchRequestDTO.getConnectorRef(),
        scmGetFileByBranchRequestDTO.getRepoName(), scmGetFileByBranchRequestDTO.getFilePath(),
        scmGetFileByBranchRequestDTO.getBranchName(), scmGetFileByBranchRequestDTO.isGetOnlyFileContent());

    gitDefaultBranchCacheHelper.cacheDefaultBranchResponse(scope.getAccountIdentifier(), scmConnector,
        scmGetFileByBranchRequestDTO.getRepoName(), requestBranch, resolvedBranch, gitFileResponse.getBranch());
    return getScmGetFileResponseDTO(gitFileResponse);
  }

  @Override
  public ScmGetBatchFilesResponseDTO getBatchFilesByBranch(
      ScmGetBatchFilesByBranchRequestDTO scmGetBatchFilesByBranchRequestDTO) {
    doGetBatchFileValidations(scmGetBatchFilesByBranchRequestDTO);

    if (!isBatchGetFileTaskSupportedByDelegates(scmGetBatchFilesByBranchRequestDTO.getAccountIdentifier())) {
      return processGetBatchFileTaskUsingSingleGetFileAPI(scmGetBatchFilesByBranchRequestDTO);
    }

    Map<GetBatchFileRequestIdentifier, GitFileRequestV2> gitFileRequestMapForManager = new HashMap<>();
    Map<GetBatchFileRequestIdentifier, GitFileRequestV2> gitFileRequestMapForDelegate = new HashMap<>();
    Map<ScmGetBatchFileRequestIdentifier, ScmGetFileResponseV2DTO> cachedScmGetFileResponseMap = new HashMap<>();

    // Check if each file is present in cache, capture the response if yes
    // For files not present in cache, group them by communication mode whether its manager or delegate
    // Process each group as per the communication mode
    scmGetBatchFilesByBranchRequestDTO.getScmGetFileByBranchRequestDTOMap().forEach(
        (requestIdentifier, scmGetFileByBranchRequestDTO) -> {
          ScmConnector scmConnector =
              gitSyncConnectorHelper.getScmConnectorForGivenRepo(scmGetFileByBranchRequestDTO.getScope(),
                  scmGetFileByBranchRequestDTO.getConnectorRef(), scmGetFileByBranchRequestDTO.getRepoName());

          GetBatchFileRequestIdentifier identifier =
              GetBatchFileRequestIdentifier.builder().identifier(requestIdentifier.getIdentifier()).build();
          Optional<ScmGetFileResponseDTO> optionalScmGetFileResponseDTO =
              getFileCacheResponseIfApplicable(scmGetFileByBranchRequestDTO, scmConnector);
          if (optionalScmGetFileResponseDTO.isPresent()) {
            cachedScmGetFileResponseMap.put(
                requestIdentifier, optionalScmGetFileResponseDTO.get().toScmGetFileResponseV2DTO());
          } else {
            GitFileRequestV2 gitFileRequest = getGitFileRequestV2(scmGetFileByBranchRequestDTO, scmConnector);
            if (gitSyncConnectorHelper.isScmConnectorManagerExecutable(scmConnector)) {
              gitFileRequestMapForManager.put(identifier, gitFileRequest);
            } else {
              gitFileRequestMapForDelegate.put(identifier, gitFileRequest);
            }
          }
        });

    ScmGetBatchFilesResponseDTO scmGetBatchFilesResponseDTO =
        processGitFileBatchRequest(scmGetBatchFilesByBranchRequestDTO.getAccountIdentifier(),
            gitFileRequestMapForManager, gitFileRequestMapForDelegate);
    scmGetBatchFilesResponseDTO.getScmGetFileResponseV2DTOMap().putAll(cachedScmGetFileResponseMap);
    return scmGetBatchFilesResponseDTO;
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
  public ScmGetFileUrlResponseDTO getFileUrl(ScmGetFileUrlRequestDTO scmGetFileUrlRequestDTO) {
    GitRepositoryDTO gitRepositoryDTO = GitRepositoryDTO.builder().name(scmGetFileUrlRequestDTO.getRepoName()).build();
    return ScmGetFileUrlResponseDTO.builder()
        .fileURL(gitFilePathHelper.getFileUrl(scmGetFileUrlRequestDTO.getScope(),
            scmGetFileUrlRequestDTO.getConnectorRef(), scmGetFileUrlRequestDTO.getBranch(),
            scmGetFileUrlRequestDTO.getFilePath(), scmGetFileUrlRequestDTO.getCommitId(), gitRepositoryDTO))
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

    try {
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
    } catch (Exception exception) {
      handleUpsertCacheFailure(exception);
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

    try {
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
    } catch (Exception exception) {
      handleUpsertCacheFailure(exception);
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
    return gitRepoUrlHelper.getRepoUrl(scmConnector, repoName);
  }

  @Override
  public UserDetailsResponseDTO getUserDetails(UserDetailsRequestDTO userDetailsRequestDTO) {
    if (isExecuteOnManager(userDetailsRequestDTO)) {
      UserDetailsResponseDTO userDetailsResponse = scmOrchestratorService.processScmRequestUsingManager(
          scmClientFacilitatorService -> scmClientFacilitatorService.getUserDetails(userDetailsRequestDTO));
      return UserDetailsResponseDTO.builder()
          .userEmail(userDetailsResponse.getUserEmail())
          .userName(userDetailsResponse.getUserName())
          .build();
    } else {
      UserDetailsResponseDTO userDetailsResponse = scmOrchestratorService.processScmRequestUsingDelegate(
          scmClientFacilitatorService -> scmClientFacilitatorService.getUserDetails(userDetailsRequestDTO));
      return UserDetailsResponseDTO.builder()
          .userEmail(userDetailsResponse.getUserEmail())
          .userName(userDetailsResponse.getUserName())
          .build();
    }
  }

  @VisibleForTesting
  protected List<GitBranchDetailsDTO> prepareGitBranchList(
      ListBranchesWithDefaultResponse listBranchesWithDefaultResponse) {
    List<String> branchList = new ArrayList<>(emptyIfNull(listBranchesWithDefaultResponse.getBranchesList()));
    if (!branchList.isEmpty() && !branchList.contains(listBranchesWithDefaultResponse.getDefaultBranch())) {
      branchList.set(branchList.size() - 1, listBranchesWithDefaultResponse.getDefaultBranch());
    }
    return branchList.stream()
        .map(branchName -> GitBranchDetailsDTO.builder().name(branchName).build())
        .distinct()
        .collect(Collectors.toList());
  }

  private List<GitRepositoryResponseDTO> prepareListRepoResponse(
      ScmConnector scmConnector, GetUserReposResponse response) {
    GitRepositoryDTO gitRepository = scmConnector.getGitRepositoryDetails();

    if (isEmpty(gitRepository.getOrg())
        && GitConnectionType.ACCOUNT.equals(ConnectorUtils.getConnectionType(scmConnector))) {
      return emptyIfNull(response.getReposList())
          .stream()
          .map(repository
              -> GitRepositoryResponseDTO.builder()
                     .name(repository.getNamespace() + "/" + repository.getName())
                     .build())
          .collect(Collectors.toList());
    }
    if (isNotEmpty(gitRepository.getName())) {
      return Collections.singletonList(GitRepositoryResponseDTO.builder().name(gitRepository.getName()).build());
    } else if (isNotEmpty(gitRepository.getOrg()) && isNamespaceNotEmpty(response)) {
      return prepareListRepoResponseWithNamespace(scmConnector, response, gitRepository);
    } else {
      return emptyIfNull(response.getReposList())
          .stream()
          .map(repository -> GitRepositoryResponseDTO.builder().name(repository.getName()).build())
          .collect(Collectors.toList());
    }
  }

  private List<GitRepositoryResponseDTO> prepareListRepoResponseWithNamespace(
      ScmConnector scmConnector, GetUserReposResponse response, GitRepositoryDTO gitRepository) {
    if (ConnectorType.GITLAB.equals(scmConnector.getConnectorType())) {
      return emptyIfNull(response.getReposList())
          .stream()
          .map(repository
              -> GitRepositoryResponseDTO.builder()
                     .name(GitProviderUtils.buildRepoForGitlab(repository.getNamespace(), repository.getName()))
                     .build())
          .collect(Collectors.toList());
    } else {
      return emptyIfNull(response.getReposList())
          .stream()
          .filter(repository -> repository.getNamespace().equals(gitRepository.getOrg()))
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
      GitFileCacheKey cacheKey = getCacheKey(scmGetFileByBranchRequestDTO, scmConnector);
      GitFileCacheResponse gitFileCacheResponse = getFileFromCache(cacheKey);
      if (gitFileCacheResponse != null) {
        log.info("CACHE HIT for cacheKey : {}", cacheKey);
        return Optional.of(prepareScmGetFileCacheResponse(gitFileCacheResponse.getGitFileCacheObject().getFileContent(),
            gitFileCacheResponse.getGitFileCacheResponseMetadata().getRef(),
            gitFileCacheResponse.getGitFileCacheObject().getCommitId(),
            gitFileCacheResponse.getGitFileCacheObject().getObjectId(), gitFileCacheResponse.getCacheDetails()));
      }
    }
    return Optional.empty();
  }

  private GitFileFetchRunnableParams getGitFileFetchRunnableParams(Scope scope, String repoName, String branchName,
      String filePath, String connectorRef, ScmConnector scmConnector) {
    return GitFileFetchRunnableParams.builder()
        .filePath(filePath)
        .branchName(branchName)
        .connectorRef(connectorRef)
        .repoName(repoName)
        .scope(scope)
        .scmConnector(scmConnector)
        .build();
  }

  private void invalidateGitFileCache(
      String accountIdentifier, String filePath, ScmConnector scmConnector, String repoName, String branchName) {
    try {
      GitFileCacheKey cacheKey = GitFileCacheKey.builder()
                                     .accountIdentifier(accountIdentifier)
                                     .completeFilePath(filePath)
                                     .gitProvider(GitProviderUtils.getGitProvider(scmConnector))
                                     .repoName(repoName)
                                     .ref(branchName)
                                     .build();
      GitFileCacheDeleteResult gitFileCacheDeleteResult = gitFileCacheService.invalidateCache(cacheKey);
      log.info("Invalidated cache for key: {} , result: {}", cacheKey, gitFileCacheDeleteResult);
    } catch (Exception exception) {
      log.error("invalidateGitFileCache Failure, skipping invalidation of cache", exception);
    }
  }

  private boolean isBatchGetFileTaskSupportedByDelegates(String accountIdentifier) {
    io.harness.delegate.TaskType taskType =
        io.harness.delegate.TaskType.newBuilder().setType(SCM_BATCH_GET_FILE_TASK.name()).build();
    AccountId accountId = AccountId.newBuilder().setId(accountIdentifier).build();
    return delegateServiceGrpcClient.isTaskTypeSupported(accountId, taskType);
  }

  private GitFileRequestV2 getGitFileRequestV2(
      ScmGetFileByBranchRequestDTO scmGetFileByBranchRequestDTO, ScmConnector scmConnector) {
    return GitFileRequestV2.builder()
        .scope(scmGetFileByBranchRequestDTO.getScope())
        .branch(scmGetFileByBranchRequestDTO.getBranchName())
        .filepath(scmGetFileByBranchRequestDTO.getFilePath())
        .repo(scmGetFileByBranchRequestDTO.getRepoName())
        .scmConnector(scmConnector)
        .connectorRef(scmGetFileByBranchRequestDTO.getConnectorRef())
        .getOnlyFileContent(scmGetFileByBranchRequestDTO.isGetOnlyFileContent())
        .build();
  }

  private ScmGetFileResponseV2DTO prepareScmGetFileResponseV2FromException(Exception exception) {
    return ScmGetFileResponseV2DTO.builder()
        .isErrorResponse(true)
        .scmErrorDetails(ScmExceptionUtils.getScmErrorDetails(exception))
        .build();
  }

  private ScmGetBatchFilesResponseDTO processGetBatchFileTaskUsingSingleGetFileAPI(
      ScmGetBatchFilesByBranchRequestDTO scmGetBatchFilesByBranchRequestDTO) {
    Map<ScmGetBatchFileRequestIdentifier, ScmGetFileResponseV2DTO> scmGetFileResponseV2DTOMap = new HashMap<>();
    scmGetBatchFilesByBranchRequestDTO.getScmGetFileByBranchRequestDTOMap().forEach((identifier, request) -> {
      ScmGetFileResponseV2DTO scmGetFileResponseV2DTO;
      try {
        ScmGetFileResponseDTO scmGetFileResponseDTO = getFileByBranch(request);
        scmGetFileResponseV2DTO = scmGetFileResponseDTO.toScmGetFileResponseV2DTO();
      } catch (Exception exception) {
        scmGetFileResponseV2DTO = prepareScmGetFileResponseV2FromException(exception);
      }
      scmGetFileResponseV2DTOMap.put(identifier, scmGetFileResponseV2DTO);
    });
    return ScmGetBatchFilesResponseDTO.builder().scmGetFileResponseV2DTOMap(scmGetFileResponseV2DTOMap).build();
  }

  private ScmGetBatchFilesResponseDTO processGitFileBatchRequest(String accountIdentifier,
      Map<GetBatchFileRequestIdentifier, GitFileRequestV2> requestsViaManager,
      Map<GetBatchFileRequestIdentifier, GitFileRequestV2> requestsViaDelegate) {
    Map<GetBatchFileRequestIdentifier, GitFileRequestV2> allFileRequestMap = new HashMap<>();

    GitFileBatchResponse gitFileBatchResponseForManager =
        processGitFileBatchRequest(accountIdentifier, requestsViaManager, true);
    GitFileBatchResponse gitFileBatchResponseForDelegate =
        processGitFileBatchRequest(accountIdentifier, requestsViaDelegate, false);

    Map<GetBatchFileRequestIdentifier, GitFileResponse> gitFileResponseMap = new HashMap<>();
    gitFileResponseMap.putAll(gitFileBatchResponseForManager.getGetBatchFileRequestIdentifierGitFileResponseMap());
    gitFileResponseMap.putAll(gitFileBatchResponseForDelegate.getGetBatchFileRequestIdentifierGitFileResponseMap());
    GitFileBatchResponse gitFileBatchResponse =
        GitFileBatchResponse.builder().getBatchFileRequestIdentifierGitFileResponseMap(gitFileResponseMap).build();

    allFileRequestMap.putAll(requestsViaManager);
    allFileRequestMap.putAll(requestsViaDelegate);
    return prepareScmGetBatchFilesResponse(accountIdentifier,
        gitFileBatchResponse.getGetBatchFileRequestIdentifierGitFileResponseMap(), allFileRequestMap);
  }

  @VisibleForTesting
  protected GitFileBatchResponse processGitFileBatchRequest(String accountIdentifier,
      Map<GetBatchFileRequestIdentifier, GitFileRequestV2> gitFileRequestMap, boolean isManagerExecutable) {
    if (gitFileRequestMap.isEmpty()) {
      return GitFileBatchResponse.builder().getBatchFileRequestIdentifierGitFileResponseMap(new HashMap<>()).build();
    }

    GitFileBatchResponse gitFileBatchResponse;
    GitFileBatchRequest gitFileBatchRequest = GitFileBatchRequest.builder()
                                                  .accountIdentifier(accountIdentifier)
                                                  .getBatchFileRequestIdentifierGitFileRequestV2Map(gitFileRequestMap)
                                                  .build();
    if (isManagerExecutable) {
      gitFileBatchResponse = scmOrchestratorService.processScmRequestUsingManager(
          scmClientFacilitatorService -> scmClientFacilitatorService.getFileBatch(gitFileBatchRequest));
    } else {
      gitFileBatchResponse = scmOrchestratorService.processScmRequestUsingDelegate(
          scmClientFacilitatorService -> scmClientFacilitatorService.getFileBatch(gitFileBatchRequest));
    }
    return gitFileBatchResponse;
  }

  private ScmGetBatchFilesResponseDTO prepareScmGetBatchFilesResponse(String accountIdentifier,
      Map<GetBatchFileRequestIdentifier, GitFileResponse> gitFileResponseMap,
      Map<GetBatchFileRequestIdentifier, GitFileRequestV2> gitFileRequestMap) {
    Map<ScmGetBatchFileRequestIdentifier, ScmGetFileResponseV2DTO> finalResponseMap = new HashMap<>();
    Map<GetBatchFileRequestIdentifier, GitFileRequestV2> requestsToCacheInBackground = new HashMap<>();

    gitFileResponseMap.forEach((requestIdentifier, gitFileResponse) -> {
      GitFileRequestV2 gitFileRequest = gitFileRequestMap.get(requestIdentifier);
      ScmGetBatchFileRequestIdentifier identifier =
          ScmGetBatchFileRequestIdentifier.fromGetBatchFileRequestIdentifier(requestIdentifier);
      try {
        checkIfErrorResponse(gitFileRequest.getScope(), gitFileResponse, gitFileRequest.getScmConnector(),
            gitFileRequest.getConnectorRef(), gitFileRequest.getRepo(), gitFileRequest.getFilepath());
        if (gitFileRequest.isGetOnlyFileContent()) {
          requestsToCacheInBackground.put(requestIdentifier, gitFileRequest);
        } else {
          upsertGetFileCache(accountIdentifier, gitFileResponse, gitFileRequest.getScmConnector(),
              gitFileRequest.getRepo(), gitFileRequest.getBranch(), gitFileRequest.getFilepath());
        }
        finalResponseMap.put(identifier, getScmGetFileResponseDTO(gitFileResponse).toScmGetFileResponseV2DTO());
      } catch (Exception exception) {
        finalResponseMap.put(identifier, prepareScmGetFileResponseV2FromException(exception));
      }
    });

    // For requests with get-only-file-content param, we cannot update cache directly as we don't have required commit
    // id Thus, we trigger background cache update thread for it
    triggerBackgroundCacheUpdateForRequestsWithGetOnlyFileContent(accountIdentifier, requestsToCacheInBackground);
    return ScmGetBatchFilesResponseDTO.builder().scmGetFileResponseV2DTOMap(finalResponseMap).build();
  }

  private void checkIfErrorResponse(Scope scope, GitFileResponse gitFileResponse, ScmConnector scmConnector,
      String connectorRef, String repoName, String filepath) {
    if (ScmApiErrorHandlingHelper.isFailureResponse(gitFileResponse.getStatusCode(), scmConnector.getConnectorType())) {
      try {
        ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.GET_FILE, scmConnector.getConnectorType(),
            scmConnector.getUrl(), gitFileResponse.getStatusCode(), gitFileResponse.getError(),
            ErrorMetadata.builder()
                .connectorRef(connectorRef)
                .repoName(repoName)
                .filepath(filepath)
                .branchName(gitFileResponse.getBranch())
                .build());
      } catch (WingsException wingsException) {
        if (ScmExceptionUtils.isNestedScmBadRequestException(wingsException)) {
          invalidateGitFileCache(
              scope.getAccountIdentifier(), filepath, scmConnector, repoName, gitFileResponse.getBranch());
        }
        throw wingsException;
      }
    }
  }

  private void cacheGetFileOperationResponse(Scope scope, GitFileResponse gitFileResponse, ScmConnector scmConnector,
      String connectorRef, String repoName, String filepath, String requestBranch, boolean getOnlyFileContent) {
    if (getOnlyFileContent) {
      gitBackgroundCacheRefreshHelper.submitTask(getGitFileFetchRunnableParams(
          scope, repoName, gitFileResponse.getBranch(), filepath, connectorRef, scmConnector));
    } else {
      upsertGetFileCache(
          scope.getAccountIdentifier(), gitFileResponse, scmConnector, repoName, requestBranch, filepath);
    }
  }

  private void upsertGetFileCache(String accountIdentifier, GitFileResponse gitFileResponse, ScmConnector scmConnector,
      String repoName, String requestBranch, String filepath) {
    try {
      gitFileCacheService.upsertCache(GitFileCacheKey.builder()
                                          .accountIdentifier(accountIdentifier)
                                          .completeFilePath(filepath)
                                          .gitProvider(GitProviderUtils.getGitProvider(scmConnector))
                                          .repoName(repoName)
                                          .ref(gitFileResponse.getBranch())
                                          .isDefaultBranch(isEmpty(requestBranch))
                                          .build(),
          GitFileCacheObject.builder()
              .fileContent(gitFileResponse.getContent())
              .commitId(gitFileResponse.getCommitId())
              .objectId(gitFileResponse.getObjectId())
              .build());
    } catch (Exception exception) {
      handleUpsertCacheFailure(exception);
    }
  }

  private ScmGetFileResponseDTO getScmGetFileResponseDTO(GitFileResponse gitFileResponse) {
    String branch = isEmpty(gitFileResponse.getBranch()) ? "" : gitFileResponse.getBranch();

    return ScmGetFileResponseDTO.builder()
        .fileContent(gitFileResponse.getContent())
        .blobId(gitFileResponse.getObjectId())
        .commitId(gitFileResponse.getCommitId())
        .branchName(branch)
        .build();
  }

  //  TODO: @Adithya remove logger before FF PIE_NG_BATCH_GET_TEMPLATES GA
  private void logBatchFileRequestIdentifiers(ScmGetBatchFilesByBranchRequestDTO scmGetBatchFilesByBranchRequestDTO) {
    List<String> uniqueFileRequests = new ArrayList<>();
    scmGetBatchFilesByBranchRequestDTO.getScmGetFileByBranchRequestDTOMap().forEach(
        (requestIdentifier, scmGetFileByBranchRequestDTO) -> {
          uniqueFileRequests.add(requestIdentifier.getIdentifier());
        });
    log.info(String.format("getBatchFilesByBranch request size %d and entity request list %s",
        uniqueFileRequests.size(), uniqueFileRequests));
  }

  private void handleUpsertCacheFailure(Exception exception) {
    log.error("Upsert Cache Failure, skipping Upsert cache operation", exception);
  }

  private void doGetBatchFileValidations(ScmGetBatchFilesByBranchRequestDTO scmGetBatchFilesByBranchRequestDTO) {
    scmGetBatchFilesByBranchRequestDTO.validate();
    logBatchFileRequestIdentifiers(scmGetBatchFilesByBranchRequestDTO);
    if (scmGetBatchFilesByBranchRequestDTO.getScmGetFileByBranchRequestDTOMap().size()
        > MAX_ALLOWED_BATCH_FILE_REQUESTS_COUNT) {
      log.warn("Too many file requests {} in single batch file request, exceeding threshold of {}",
          scmGetBatchFilesByBranchRequestDTO.getScmGetFileByBranchRequestDTOMap().size(),
          MAX_ALLOWED_BATCH_FILE_REQUESTS_COUNT);
    }
  }

  private GitBatchFileFetchRunnableParams getGitBatchFileFetchRunnableParams(
      String accountIdentifier, Map<GetBatchFileRequestIdentifier, GitFileRequestV2> requestMap) {
    Map<GetBatchFileRequestIdentifier, GitFileFetchRunnableParams> gitFileFetchRunnableParamsMap = new HashMap<>();
    requestMap.forEach((requestIdentifier, request) -> {
      gitFileFetchRunnableParamsMap.put(requestIdentifier,
          GitFileFetchRunnableParams.builder()
              .scmConnector(request.getScmConnector())
              .scope(request.getScope())
              .filePath(request.getFilepath())
              .repoName(request.getRepo())
              .connectorRef(request.getConnectorRef())
              .branchName(request.getBranch())
              .build());
    });
    return GitBatchFileFetchRunnableParams.builder()
        .accountIdentifier(accountIdentifier)
        .gitFileFetchRunnableParamsMap(gitFileFetchRunnableParamsMap)
        .build();
  }

  private void triggerBackgroundCacheUpdateForRequestsWithGetOnlyFileContent(
      String accountIdentifier, Map<GetBatchFileRequestIdentifier, GitFileRequestV2> requestsToCache) {
    if (requestsToCache.isEmpty()) {
      return;
    }
    gitBackgroundCacheRefreshHelper.submitBatchTask(
        getGitBatchFileFetchRunnableParams(accountIdentifier, requestsToCache));
  }

  private boolean isExecuteOnManager(UserDetailsRequestDTO userDetailsRequestDTO) {
    // TODO: return true if the secret manager used for token is harness secret manager
    return true;
  }

  @VisibleForTesting
  protected void cacheDefaultBranchResponse(String accountIdentifier, ScmConnector scmConnector, String repoName,
      String requestBranch, String resolvedBranch, String responseBranch) {
    if (isEmpty(requestBranch) && isEmpty(resolvedBranch)) {
      gitDefaultBranchCacheHelper.upsertDefaultBranch(accountIdentifier, repoName, responseBranch, scmConnector);
    }
  }
}
