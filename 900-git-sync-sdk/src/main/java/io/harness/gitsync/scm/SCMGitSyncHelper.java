/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.scm;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.gitsync.interceptor.GitSyncConstants.DEFAULT;

import io.harness.EntityType;
import io.harness.ScopeIdentifiers;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.ngexception.beans.ScmErrorMetadataDTO;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.CreateFileRequest;
import io.harness.gitsync.CreateFileResponse;
import io.harness.gitsync.CreatePRRequest;
import io.harness.gitsync.CreatePRResponse;
import io.harness.gitsync.ErrorDetails;
import io.harness.gitsync.FileInfo;
import io.harness.gitsync.GetBatchFilesRequest;
import io.harness.gitsync.GetBatchFilesResponse;
import io.harness.gitsync.GetFileRequest;
import io.harness.gitsync.GetFileResponse;
import io.harness.gitsync.GetRepoUrlRequest;
import io.harness.gitsync.GetRepoUrlResponse;
import io.harness.gitsync.GitMetaData;
import io.harness.gitsync.GitSettings;
import io.harness.gitsync.HarnessToGitPushInfoServiceGrpc.HarnessToGitPushInfoServiceBlockingStub;
import io.harness.gitsync.PushFileResponse;
import io.harness.gitsync.UpdateFileRequest;
import io.harness.gitsync.UpdateFileResponse;
import io.harness.gitsync.ValidateRepoRequest;
import io.harness.gitsync.ValidateRepoResponse;
import io.harness.gitsync.common.beans.GitOperation;
import io.harness.gitsync.common.helper.CacheRequestMapper;
import io.harness.gitsync.common.helper.ChangeTypeMapper;
import io.harness.gitsync.common.helper.EntityTypeMapper;
import io.harness.gitsync.common.helper.GitSyncGrpcClientUtils;
import io.harness.gitsync.common.helper.GitSyncLogContextHelper;
import io.harness.gitsync.common.helper.ScopeIdentifierMapper;
import io.harness.gitsync.common.helper.UserPrincipalMapper;
import io.harness.gitsync.exceptions.GitSyncException;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.scm.beans.SCMNoOpResponse;
import io.harness.gitsync.scm.beans.ScmCreateFileGitRequest;
import io.harness.gitsync.scm.beans.ScmCreateFileGitResponse;
import io.harness.gitsync.scm.beans.ScmCreatePRResponse;
import io.harness.gitsync.scm.beans.ScmErrorDetails;
import io.harness.gitsync.scm.beans.ScmGetBatchFileRequest;
import io.harness.gitsync.scm.beans.ScmGetBatchFilesResponse;
import io.harness.gitsync.scm.beans.ScmGetFileRequest;
import io.harness.gitsync.scm.beans.ScmGetFileResponse;
import io.harness.gitsync.scm.beans.ScmGetRepoUrlResponse;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitsync.scm.beans.ScmPushResponse;
import io.harness.gitsync.scm.beans.ScmUpdateFileGitRequest;
import io.harness.gitsync.scm.beans.ScmUpdateFileGitResponse;
import io.harness.gitsync.scm.errorhandling.ScmErrorHandler;
import io.harness.gitsync.sdk.CacheResponse;
import io.harness.gitsync.sdk.CacheState;
import io.harness.impl.ScmResponseStatusUtils;
import io.harness.logging.MdcContextSetter;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailRestToProtoMapper;
import io.harness.security.Principal;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.StringValue;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

@Singleton
@Slf4j
@OwnedBy(DX)
public class SCMGitSyncHelper {
  @Inject private HarnessToGitPushInfoServiceBlockingStub harnessToGitPushInfoServiceBlockingStub;
  @Inject private EntityDetailRestToProtoMapper entityDetailRestToProtoMapper;
  @Inject GitSyncSdkService gitSyncSdkService;
  @Inject private ScmErrorHandler scmErrorHandler;

  public ScmPushResponse pushToGit(
      GitEntityInfo gitBranchInfo, String yaml, ChangeType changeType, EntityDetail entityDetail) {
    if (gitBranchInfo.isSyncFromGit()) {
      return getResponseInG2H(gitBranchInfo, entityDetail);
    }

    final FileInfo fileInfo = getFileInfo(gitBranchInfo, yaml, changeType, entityDetail);
    final PushFileResponse pushFileResponse =
        GitSyncGrpcClientUtils.retryAndProcessException(harnessToGitPushInfoServiceBlockingStub::pushFile, fileInfo);
    try {
      checkForError(pushFileResponse);
    } catch (WingsException e) {
      throwDifferentExceptionInCaseOfChangeTypeAdd(gitBranchInfo, changeType, e);
    }
    return ScmGitUtils.createScmPushResponse(yaml, gitBranchInfo, pushFileResponse, entityDetail, changeType);
  }

  public ScmGetFileResponse getFileByBranch(Scope scope, String repoName, String branchName, String commitId,
      String filePath, String connectorRef, boolean loadFromCache, EntityType entityType,
      Map<String, String> contextMap, boolean getOnlyFileContent, boolean applyRepoAllowListFilter) {
    contextMap = GitSyncLogContextHelper.setContextMap(
        scope, repoName, branchName, commitId, filePath, GitOperation.GET_FILE, contextMap);
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
         MdcContextSetter ignore1 = new MdcContextSetter(contextMap)) {
      final GetFileRequest getFileRequest =
          GetFileRequest.newBuilder()
              .setRepoName(repoName)
              .setConnectorRef(connectorRef)
              .setBranchName(Strings.nullToEmpty(branchName))
              .setCommitId(Strings.nullToEmpty(commitId))
              .setFilePath(filePath)
              .setCacheRequestParams(CacheRequestMapper.getCacheRequest(loadFromCache))
              .putAllContextMap(contextMap)
              .setEntityType(EntityTypeMapper.getEntityType(entityType))
              .setScopeIdentifiers(ScopeIdentifierMapper.getScopeIdentifiersFromScope(scope))
              .setPrincipal(getPrincipal())
              .setGetOnlyFileContent(getOnlyFileContent)
              .setGitSettings(GitSettings.newBuilder().setApplyRepoAllowListFilter(applyRepoAllowListFilter).build())
              .build();
      final GetFileResponse getFileResponse = GitSyncGrpcClientUtils.retryAndProcessExceptionV2(
          harnessToGitPushInfoServiceBlockingStub::getFile, getFileRequest);

      ScmGitMetaData scmGitMetaData = getScmGitMetaData(getFileResponse);
      if (isFailureResponse(getFileResponse.getStatusCode())) {
        log.error("Git SDK getFile Failure: {}", getFileResponse);
        if (isEmpty(scmGitMetaData.getBranchName())) {
          scmGitMetaData.setRepoName(getFileRequest.getBranchName());
        }
        scmErrorHandler.processAndThrowException(getFileResponse.getStatusCode(),
            getScmErrorDetailsFromGitProtoResponse(getFileResponse.getError()), scmGitMetaData);
      }
      log.info("Git SDK file content {} and scmGetMetadata : {}", getFileResponse.getFileContent(), scmGitMetaData);
      return ScmGetFileResponse.builder()
          .fileContent(getFileResponse.getFileContent())
          .gitMetaData(scmGitMetaData)
          .build();
    }
  }

  public ScmCreateFileGitResponse createFile(
      Scope scope, ScmCreateFileGitRequest gitRequest, Map<String, String> contextMap) {
    contextMap = GitSyncLogContextHelper.setContextMap(scope, gitRequest.getRepoName(), gitRequest.getBranchName(), "",
        gitRequest.getFilePath(), GitOperation.CREATE_FILE, contextMap);
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
         MdcContextSetter ignore1 = new MdcContextSetter(contextMap)) {
      final CreateFileRequest createFileRequest =
          CreateFileRequest.newBuilder()
              .setRepoName(gitRequest.getRepoName())
              .setFilePath(gitRequest.getFilePath())
              .setBranchName(gitRequest.getBranchName())
              .setConnectorRef(gitRequest.getConnectorRef())
              .setFileContent(gitRequest.getFileContent())
              .setIsCommitToNewBranch(gitRequest.isCommitToNewBranch())
              .setCommitMessage(gitRequest.getCommitMessage())
              .setScopeIdentifiers(ScopeIdentifierMapper.getScopeIdentifiersFromScope(scope))
              .putAllContextMap(contextMap)
              .setBaseBranchName((gitRequest.isCommitToNewBranch()) ? gitRequest.getBaseBranch() : "")
              .setPrincipal(getPrincipal())
              .build();

      final CreateFileResponse createFileResponse = GitSyncGrpcClientUtils.retryAndProcessExceptionV2(
          harnessToGitPushInfoServiceBlockingStub::createFile, createFileRequest);

      if (isFailureResponse(createFileResponse.getStatusCode())) {
        log.error("Git SDK createFile Failure: {}", createFileResponse);
        scmErrorHandler.processAndThrowException(createFileResponse.getStatusCode(),
            getScmErrorDetailsFromGitProtoResponse(createFileResponse.getError()),
            getScmGitMetaDataFromGitProtoResponse(createFileResponse.getGitMetaData()));
      }

      return ScmCreateFileGitResponse.builder()
          .gitMetaData(getScmGitMetaDataFromGitProtoResponse(createFileResponse.getGitMetaData()))
          .build();
    }
  }

  public ScmUpdateFileGitResponse updateFile(
      Scope scope, ScmUpdateFileGitRequest gitRequest, Map<String, String> contextMap) {
    contextMap = GitSyncLogContextHelper.setContextMap(scope, gitRequest.getRepoName(), gitRequest.getBranchName(), "",
        gitRequest.getFilePath(), GitOperation.UPDATE_FILE, contextMap);
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
         MdcContextSetter ignore1 = new MdcContextSetter(contextMap)) {
      final UpdateFileRequest updateFileRequest =
          UpdateFileRequest.newBuilder()
              .setRepoName(gitRequest.getRepoName())
              .setFilePath(gitRequest.getFilePath())
              .setBranchName(gitRequest.getBranchName())
              .setConnectorRef(gitRequest.getConnectorRef())
              .setFileContent(gitRequest.getFileContent())
              .setIsCommitToNewBranch(gitRequest.isCommitToNewBranch())
              .setCommitMessage(gitRequest.getCommitMessage())
              .setScopeIdentifiers(ScopeIdentifierMapper.getScopeIdentifiersFromScope(scope))
              .putAllContextMap(contextMap)
              .setBaseBranchName((gitRequest.isCommitToNewBranch()) ? gitRequest.getBaseBranch() : "")
              .setOldCommitId(emptyIfNull(gitRequest.getOldCommitId()))
              .setOldFileSha(emptyIfNull(gitRequest.getOldFileSha()))
              .setPrincipal(getPrincipal())
              .build();

      final UpdateFileResponse updateFileResponse = GitSyncGrpcClientUtils.retryAndProcessExceptionV2(
          harnessToGitPushInfoServiceBlockingStub::updateFile, updateFileRequest);

      if (isFailureResponse(updateFileResponse.getStatusCode())) {
        log.error("Git SDK updateFile Failure: {}", updateFileResponse);
        scmErrorHandler.processAndThrowException(updateFileResponse.getStatusCode(),
            getScmErrorDetailsFromGitProtoResponse(updateFileResponse.getError()),
            getScmGitMetaDataFromGitProtoResponse(updateFileResponse.getGitMetaData()));
      }

      return ScmUpdateFileGitResponse.builder()
          .gitMetaData(getScmGitMetaDataFromGitProtoResponse(updateFileResponse.getGitMetaData()))
          .build();
    }
  }

  public ScmCreatePRResponse createPullRequest(Scope scope, String repoName, String connectorRef, String sourceBranch,
      String targetBranch, String title, Map<String, String> contextMap) {
    final CreatePRRequest createPRRequest =
        CreatePRRequest.newBuilder()
            .setRepoName(repoName)
            .setConnectorRef(connectorRef)
            .setScopeIdentifiers(ScopeIdentifierMapper.getScopeIdentifiersFromScope(scope))
            .setSourceBranch(sourceBranch)
            .setTargetBranch(targetBranch)
            .setTitle(title)
            .putAllContextMap(contextMap)
            .setPrincipal(getPrincipal())
            .build();

    final CreatePRResponse createPRResponse = GitSyncGrpcClientUtils.retryAndProcessExceptionV2(
        harnessToGitPushInfoServiceBlockingStub::createPullRequest, createPRRequest);

    if (isFailureResponse(createPRResponse.getStatusCode())) {
      log.error("Git SDK createPullRequest Failure: {}", createPRResponse);
      scmErrorHandler.processAndThrowException(createPRResponse.getStatusCode(),
          getScmErrorDetailsFromGitProtoResponse(createPRResponse.getError()), ScmGitMetaData.builder().build());
    }

    return ScmCreatePRResponse.builder().prNumber(createPRResponse.getPrNumber()).build();
  }

  public ScmGetRepoUrlResponse getRepoUrl(
      Scope scope, String repoName, String connectorRef, Map<String, String> contextMap) {
    contextMap =
        GitSyncLogContextHelper.setContextMap(scope, repoName, "", "", "", GitOperation.GET_REPO_URL, contextMap);
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
         MdcContextSetter ignore1 = new MdcContextSetter(contextMap)) {
      final GetRepoUrlRequest getRepoUrlRequest =
          GetRepoUrlRequest.newBuilder()
              .setRepoName(repoName)
              .setConnectorRef(connectorRef)
              .setScopeIdentifiers(ScopeIdentifierMapper.getScopeIdentifiersFromScope(scope))
              .putAllContextMap(contextMap)
              .build();

      final GetRepoUrlResponse getRepoUrlResponse = GitSyncGrpcClientUtils.retryAndProcessExceptionV2(
          harnessToGitPushInfoServiceBlockingStub::getRepoUrl, getRepoUrlRequest);

      if (isFailureResponse(getRepoUrlResponse.getStatusCode())) {
        log.error("Git SDK getRepoUrl Failure: {}", getRepoUrlResponse);
        scmErrorHandler.processAndThrowException(getRepoUrlResponse.getStatusCode(),
            getScmErrorDetailsFromGitProtoResponse(getRepoUrlResponse.getError()), ScmGitMetaData.builder().build());
      }

      return ScmGetRepoUrlResponse.builder().repoUrl(getRepoUrlResponse.getRepoUrl()).build();
    }
  }

  public ScmGetBatchFilesResponse getBatchFilesByBranch(
      String accountIdentifier, ScmGetBatchFileRequest scmGetBatchFileRequest) {
    Map<String, ScmGetFileRequest> scmGetBatchFilesRequestMap = scmGetBatchFileRequest.getScmGetBatchFilesRequestMap();
    if (scmGetBatchFilesRequestMap.isEmpty()) {
      return ScmGetBatchFilesResponse.builder().build();
    }
    Map<String, GetFileRequest> sdkRequestMap = new HashMap<>();

    for (Map.Entry<String, ScmGetFileRequest> scmGetFileRequestEntry : scmGetBatchFilesRequestMap.entrySet()) {
      ScmGetFileRequest scmGetFileRequest = scmGetFileRequestEntry.getValue();
      GetFileRequest getFileRequest =
          GetFileRequest.newBuilder()
              .setRepoName(scmGetFileRequest.getRepoName())
              .setConnectorRef(scmGetFileRequest.getConnectorRef())
              .setBranchName(Strings.nullToEmpty(scmGetFileRequest.getBranchName()))
              .setFilePath(scmGetFileRequest.getFilePath())
              .setCacheRequestParams(CacheRequestMapper.getCacheRequest(scmGetFileRequest.isLoadFromCache()))
              .putAllContextMap(scmGetFileRequest.getContextMap())
              .setEntityType(EntityTypeMapper.getEntityType(scmGetFileRequest.getEntityType()))
              .setScopeIdentifiers(ScopeIdentifierMapper.getScopeIdentifiersFromScope(scmGetFileRequest.getScope()))
              .setPrincipal(getPrincipal())
              .setGetOnlyFileContent(scmGetFileRequest.isGetOnlyFileContent())
              .build();
      sdkRequestMap.put(scmGetFileRequestEntry.getKey(), getFileRequest);
    }

    final GetBatchFilesRequest getBatchFilesRequest = GetBatchFilesRequest.newBuilder()
                                                          .setAccountIdentifier(accountIdentifier)
                                                          .putAllGetFileRequestMap(sdkRequestMap)
                                                          .build();

    final GetBatchFilesResponse getBatchFilesResponse = GitSyncGrpcClientUtils.retryAndProcessExceptionV2(
        harnessToGitPushInfoServiceBlockingStub::getBatchFiles, getBatchFilesRequest);

    if (isFailureResponse(getBatchFilesResponse.getStatusCode())) {
      log.error("Git SDK getBatchFiles Failure: {}", getBatchFilesResponse);
      scmErrorHandler.processAndThrowException(getBatchFilesResponse.getStatusCode(),
          getScmErrorDetailsFromGitProtoResponse(getBatchFilesResponse.getError()), ScmGitMetaData.builder().build());
    }

    return prepareScmGetBatchFilesResponse(getBatchFilesResponse);
  }

  public void validateRepo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef, String repo) {
    final ValidateRepoResponse validateRepoResponse =
        GitSyncGrpcClientUtils.retryAndProcessExceptionV2(harnessToGitPushInfoServiceBlockingStub::validateRepo,
            ValidateRepoRequest.newBuilder()
                .setScope(ScopeIdentifiers.newBuilder()
                              .setAccountIdentifier(accountIdentifier)
                              .setOrgIdentifier(orgIdentifier)
                              .setProjectIdentifier(projectIdentifier)
                              .build())
                .setConnectorRef(connectorRef)
                .setRepo(repo)
                .build());

    if (isFailureResponse(validateRepoResponse.getStatusCode())) {
      log.error("Git SDK validateRepo Failure: {}", validateRepoResponse);
      scmErrorHandler.processAndThrowException(validateRepoResponse.getStatusCode(),
          getScmErrorDetailsFromGitProtoResponse(validateRepoResponse.getError()));
    }
  }

  private ScmGetBatchFilesResponse prepareScmGetBatchFilesResponse(GetBatchFilesResponse getBatchFilesResponse) {
    Map<String, GetFileResponse> getFileResponseMap = getBatchFilesResponse.getGetFileResponseMapMap();
    Map<String, ScmGetFileResponse> batchFilesResponse = new HashMap<>();

    getFileResponseMap.forEach((identifier, getFileResponse) -> {
      batchFilesResponse.put(identifier,
          ScmGetFileResponse.builder()
              .fileContent(getFileResponse.getFileContent())
              .gitMetaData(getScmGitMetaData(getFileResponse))
              .build());
    });

    return ScmGetBatchFilesResponse.builder().batchFilesResponse(batchFilesResponse).build();
  }

  @VisibleForTesting
  protected void throwDifferentExceptionInCaseOfChangeTypeAdd(
      GitEntityInfo gitBranchInfo, ChangeType changeType, WingsException e) {
    if (changeType.equals(ChangeType.ADD)) {
      final WingsException cause = ExceptionUtils.cause(ErrorCode.SCM_CONFLICT_ERROR, e);
      if (cause != null) {
        throw new InvalidRequestException(String.format(
            "A file with name %s already exists in the remote Git repository", gitBranchInfo.getFilePath()));
      }
    }
    throw e;
  }

  private FileInfo getFileInfo(
      GitEntityInfo gitBranchInfo, String yaml, ChangeType changeType, EntityDetail entityDetail) {
    FileInfo.Builder builder = FileInfo.newBuilder()
                                   .setPrincipal(getPrincipal())
                                   .setAccountId(entityDetail.getEntityRef().getAccountIdentifier())
                                   .setBranch(gitBranchInfo.getBranch())
                                   .setEntityDetail(entityDetailRestToProtoMapper.createEntityDetailDTO(entityDetail))
                                   .setChangeType(ChangeTypeMapper.toProto(changeType))
                                   .setFilePath(gitBranchInfo.getFilePath())
                                   .setFolderPath(gitBranchInfo.getFolderPath())
                                   .setIsNewBranch(gitBranchInfo.isNewBranch())
                                   .setCommitMsg(StringValue.of(gitBranchInfo.getCommitMsg()))
                                   .setYamlGitConfigId(gitBranchInfo.getYamlGitConfigId())
                                   .putAllContextMap(MDC.getCopyOfContextMap())
                                   .setYaml(emptyIfNull(yaml));

    if (gitBranchInfo.getIsFullSyncFlow() != null) {
      builder.setIsFullSyncFlow(gitBranchInfo.getIsFullSyncFlow());
    } else {
      builder.setIsFullSyncFlow(false);
    }

    if (gitBranchInfo.getBaseBranch() != null) {
      builder.setBaseBranch(StringValue.of(gitBranchInfo.getBaseBranch()));
    }

    if (gitBranchInfo.getLastObjectId() != null) {
      builder.setOldFileSha(StringValue.of(gitBranchInfo.getLastObjectId()));
    }

    if (gitBranchInfo.getResolvedConflictCommitId() != null
        && !gitBranchInfo.getResolvedConflictCommitId().equals(DEFAULT)) {
      builder.setCommitId(gitBranchInfo.getResolvedConflictCommitId());
    }

    return builder.build();
  }

  private SCMNoOpResponse getResponseInG2H(GitEntityInfo gitBranchInfo, EntityDetail entityDetail) {
    final boolean defaultBranch = gitSyncSdkService.isDefaultBranch(entityDetail.getEntityRef().getAccountIdentifier(),
        entityDetail.getEntityRef().getOrgIdentifier(), entityDetail.getEntityRef().getProjectIdentifier());
    return SCMNoOpResponse.builder()
        .filePath(gitBranchInfo.getFilePath())
        .pushToDefaultBranch(defaultBranch)
        .objectId(gitBranchInfo.getLastObjectId())
        .yamlGitConfigId(gitBranchInfo.getYamlGitConfigId())
        .branch(gitBranchInfo.getBranch())
        .folderPath(gitBranchInfo.getFolderPath())
        .commitId(gitBranchInfo.getCommitId())
        .build();
  }

  @VisibleForTesting
  protected void checkForError(PushFileResponse pushFileResponse) {
    if (pushFileResponse.getStatus() != 1) {
      final String errorMessage =
          isNotEmpty(pushFileResponse.getError()) ? pushFileResponse.getError() : "Error in doing git push";
      throw new GitSyncException(errorMessage);
    }
    try {
      ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
          pushFileResponse.getScmResponseCode(), pushFileResponse.getError());
    } catch (WingsException ex) {
      ex.setMetadata(ScmErrorMetadataDTO.builder().conflictCommitId(pushFileResponse.getCommitId()).build());
      throw ex;
    }
  }

  private Principal getPrincipal() {
    final io.harness.security.dto.Principal sourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    if (sourcePrincipal == null) {
      throw new InvalidRequestException("Principal cannot be null");
    }
    final Principal.Builder principalBuilder = Principal.newBuilder();
    switch (sourcePrincipal.getType()) {
      case USER:
        UserPrincipal userPrincipalFromContext = (UserPrincipal) sourcePrincipal;
        return principalBuilder.setUserPrincipal(UserPrincipalMapper.toProto(userPrincipalFromContext)).build();
      case SERVICE:
        final ServicePrincipal servicePrincipalFromContext = (ServicePrincipal) sourcePrincipal;
        final io.harness.security.ServicePrincipal servicePrincipal =
            io.harness.security.ServicePrincipal.newBuilder().setName(servicePrincipalFromContext.getName()).build();
        return principalBuilder.setServicePrincipal(servicePrincipal).build();
      case SERVICE_ACCOUNT:
        final ServiceAccountPrincipal serviceAccountPrincipalFromContext = (ServiceAccountPrincipal) sourcePrincipal;
        final io.harness.security.ServiceAccountPrincipal serviceAccountPrincipal =
            io.harness.security.ServiceAccountPrincipal.newBuilder()
                .setName(StringValue.of(serviceAccountPrincipalFromContext.getName()))
                .setEmail(StringValue.of(serviceAccountPrincipalFromContext.getEmail()))
                .setUserName(StringValue.of(serviceAccountPrincipalFromContext.getUsername()))
                .build();
        return principalBuilder.setServiceAccountPrincipal(serviceAccountPrincipal).build();
      default:
        throw new InvalidRequestException("Principal type not set.");
    }
  }

  private ScmGitMetaData getScmGitMetaDataFromGitProtoResponse(GitMetaData gitMetaData) {
    return ScmGitMetaData.builder()
        .blobId(gitMetaData.getBlobId())
        .branchName(gitMetaData.getBranchName())
        .repoName(gitMetaData.getRepoName())
        .filePath(gitMetaData.getFilePath())
        .commitId(gitMetaData.getCommitId())
        .fileUrl(gitMetaData.getFileUrl())
        .repoUrl(gitMetaData.getRepoUrl())
        .isGitDefaultBranch(gitMetaData.getIsGitDefaultBranch())
        .build();
  }

  private ScmGitMetaData getScmGitMetaData(GetFileResponse getFileResponse) {
    if (getFileResponse.hasCacheResponse()) {
      return getScmGitMetaDataFromGitProtoResponse(
          getFileResponse.getGitMetaData(), getFileResponse.getCacheResponse());
    } else {
      return getScmGitMetaDataFromGitProtoResponse(getFileResponse.getGitMetaData());
    }
  }

  private ScmGitMetaData getScmGitMetaDataFromGitProtoResponse(
      GitMetaData gitMetaData, io.harness.gitsync.CacheResponseParams cacheResponse) {
    return ScmGitMetaData.builder()
        .blobId(gitMetaData.getBlobId())
        .branchName(gitMetaData.getBranchName())
        .repoName(gitMetaData.getRepoName())
        .filePath(gitMetaData.getFilePath())
        .commitId(gitMetaData.getCommitId())
        .fileUrl(gitMetaData.getFileUrl())
        .repoUrl(gitMetaData.getRepoUrl())
        .cacheResponse(getCacheResponseFromGitProtoResponse(cacheResponse))
        .isGitDefaultBranch(gitMetaData.getIsGitDefaultBranch())
        .build();
  }

  private CacheResponse getCacheResponseFromGitProtoResponse(io.harness.gitsync.CacheResponseParams cacheResponse) {
    return CacheResponse.builder()
        .cacheState(getCacheStateFromGitProtoResponse(cacheResponse.getCacheState()))
        .lastUpdatedAt(cacheResponse.getLastUpdateAt())
        .ttlLeft(cacheResponse.getTtlLeft())
        .build();
  }

  private CacheState getCacheStateFromGitProtoResponse(io.harness.gitsync.CacheState cacheState) {
    if (io.harness.gitsync.CacheState.STALE_CACHE.equals(cacheState)) {
      return CacheState.STALE_CACHE;
    } else if (io.harness.gitsync.CacheState.VALID_CACHE.equals(cacheState)) {
      return CacheState.VALID_CACHE;
    }
    return CacheState.UNKNOWN;
  }

  private ScmErrorDetails getScmErrorDetailsFromGitProtoResponse(ErrorDetails errorDetails) {
    return ScmErrorDetails.builder()
        .errorMessage(errorDetails.getErrorMessage())
        .explanationMessage(errorDetails.getExplanationMessage())
        .hintMessage(errorDetails.getHintMessage())
        .build();
  }

  private boolean isFailureResponse(int statusCode) {
    return statusCode >= 300;
  }
}
