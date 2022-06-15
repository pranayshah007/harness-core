/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.gitsync.common.beans.BranchSyncStatus.UNSYNCED;
import static io.harness.gitsync.common.scmerrorhandling.ScmErrorCodeToHttpStatusCodeMapping.HTTP_200;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.Scope;
import io.harness.common.EntityReference;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ScmException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.gitsync.BranchDetails;
import io.harness.gitsync.ChangeType;
import io.harness.gitsync.CreateFileRequest;
import io.harness.gitsync.CreatePRRequest;
import io.harness.gitsync.CreatePRResponse;
import io.harness.gitsync.ErrorDetails;
import io.harness.gitsync.FileInfo;
import io.harness.gitsync.GetFileRequest;
import io.harness.gitsync.GetFileResponse;
import io.harness.gitsync.GitMetaData;
import io.harness.gitsync.PushFileResponse;
import io.harness.gitsync.PushInfo;
import io.harness.gitsync.RepoDetails;
import io.harness.gitsync.UpdateFileRequest;
import io.harness.gitsync.common.beans.BranchSyncStatus;
import io.harness.gitsync.common.beans.GitBranch;
import io.harness.gitsync.common.beans.GitSyncDirection;
import io.harness.gitsync.common.beans.InfoForGitPush;
import io.harness.gitsync.common.dtos.GitSyncEntityDTO;
import io.harness.gitsync.common.dtos.ScmCommitFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmCreateFileRequestDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRRequestDTO;
import io.harness.gitsync.common.dtos.ScmCreatePRResponseDTO;
import io.harness.gitsync.common.dtos.ScmGetFileByBranchRequestDTO;
import io.harness.gitsync.common.dtos.ScmGetFileResponseDTO;
import io.harness.gitsync.common.dtos.ScmUpdateFileRequestDTO;
import io.harness.gitsync.common.helper.GitFilePathHelper;
import io.harness.gitsync.common.helper.GitSyncConnectorHelper;
import io.harness.gitsync.common.helper.ScmExceptionUtils;
import io.harness.gitsync.common.helper.ScopeIdentifierMapper;
import io.harness.gitsync.common.helper.UserProfileHelper;
import io.harness.gitsync.common.scmerrorhandling.ScmErrorCodeToHttpStatusCodeMapping;
import io.harness.gitsync.common.service.GitBranchService;
import io.harness.gitsync.common.service.GitBranchSyncService;
import io.harness.gitsync.common.service.GitEntityService;
import io.harness.gitsync.common.service.GitSyncSettingsService;
import io.harness.gitsync.common.service.HarnessToGitHelperService;
import io.harness.gitsync.common.service.ScmFacilitatorService;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.gitsync.common.utils.GitSyncFilePathUtils;
import io.harness.gitsync.core.beans.GitCommit.GitCommitProcessingStatus;
import io.harness.gitsync.core.dtos.GitCommitDTO;
import io.harness.gitsync.core.fullsync.entity.GitFullSyncJob;
import io.harness.gitsync.core.fullsync.service.FullSyncJobService;
import io.harness.gitsync.core.service.GitCommitService;
import io.harness.gitsync.gitfileactivity.beans.GitFileProcessingSummary;
import io.harness.gitsync.gitsyncerror.service.GitSyncErrorService;
import io.harness.gitsync.scm.ScmGitUtils;
import io.harness.grpc.utils.StringValueUtils;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.product.ci.scm.proto.DeleteFileResponse;
import io.harness.product.ci.scm.proto.UpdateFileResponse;
import io.harness.remote.client.RestClientUtils;
import io.harness.security.Principal;
import io.harness.security.dto.UserPrincipal;
import io.harness.tasks.DecryptGitApiAccessHelper;
import io.harness.utils.IdentifierRefHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(DX)
public class HarnessToGitHelperServiceImpl implements HarnessToGitHelperService {
  private final ConnectorService connectorService;
  private final DecryptGitApiAccessHelper decryptScmApiAccess;
  private final GitEntityService gitEntityService;
  private final YamlGitConfigService yamlGitConfigService;
  private final EntityDetailProtoToRestMapper entityDetailRestToProtoMapper;
  private final ExecutorService executorService;
  private final GitBranchService gitBranchService;
  private final GitFilePathHelper gitFilePathHelper;
  private final ScmOrchestratorService scmOrchestratorService;
  private final GitBranchSyncService gitBranchSyncService;
  private final GitCommitService gitCommitService;
  private final UserProfileHelper userProfileHelper;
  private final GitSyncErrorService gitSyncErrorService;
  private final GitSyncConnectorHelper gitSyncConnectorHelper;
  private final FullSyncJobService fullSyncJobService;
  private final ScmFacilitatorService scmFacilitatorService;
  private final GitSyncSettingsService gitSyncSettingsService;
  private final AccountClient accountClient;

  @Inject
  public HarnessToGitHelperServiceImpl(@Named("connectorDecoratorService") ConnectorService connectorService,
      DecryptGitApiAccessHelper decryptScmApiAccess, GitEntityService gitEntityService,
      YamlGitConfigService yamlGitConfigService, EntityDetailProtoToRestMapper entityDetailRestToProtoMapper,
      ExecutorService executorService, GitBranchService gitBranchService, GitFilePathHelper gitFilePathHelper,
      ScmOrchestratorService scmOrchestratorService, GitBranchSyncService gitBranchSyncService,
      GitCommitService gitCommitService, UserProfileHelper userProfileHelper, GitSyncErrorService gitSyncErrorService,
      GitSyncConnectorHelper gitSyncConnectorHelper, FullSyncJobService fullSyncJobService,
      ScmFacilitatorService scmFacilitatorService, GitSyncSettingsService gitSyncSettingsService,
      AccountClient accountClient) {
    this.connectorService = connectorService;
    this.decryptScmApiAccess = decryptScmApiAccess;
    this.gitEntityService = gitEntityService;
    this.yamlGitConfigService = yamlGitConfigService;
    this.entityDetailRestToProtoMapper = entityDetailRestToProtoMapper;
    this.executorService = executorService;
    this.gitBranchService = gitBranchService;
    this.gitFilePathHelper = gitFilePathHelper;
    this.scmOrchestratorService = scmOrchestratorService;
    this.gitBranchSyncService = gitBranchSyncService;
    this.gitCommitService = gitCommitService;
    this.userProfileHelper = userProfileHelper;
    this.gitSyncErrorService = gitSyncErrorService;
    this.gitSyncConnectorHelper = gitSyncConnectorHelper;
    this.fullSyncJobService = fullSyncJobService;
    this.scmFacilitatorService = scmFacilitatorService;
    this.gitSyncSettingsService = gitSyncSettingsService;
    this.accountClient = accountClient;
  }

  private Optional<ConnectorResponseDTO> getConnector(
      String accountId, YamlGitConfigDTO yamlGitConfig, Principal principal) {
    final String gitConnectorId = yamlGitConfig.getGitConnectorRef();
    final String connectorRepo = yamlGitConfig.getGitConnectorsRepo();
    final String connectorBranch = yamlGitConfig.getGitConnectorsBranch();
    final IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(gitConnectorId, accountId,
        yamlGitConfig.getOrganizationIdentifier(), yamlGitConfig.getProjectIdentifier(), null);

    final Optional<ConnectorResponseDTO> connectorResponseDTO =
        gitSyncConnectorHelper.getConnectorFromDefaultBranchElseFromGitBranch(accountId,
            identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier(),
            connectorRepo, connectorBranch);
    if (!connectorResponseDTO.isPresent()) {
      throw new InvalidRequestException(String.format("Ref Connector [{}] doesn't exist.", gitConnectorId));
    }
    final ConnectorResponseDTO connector = connectorResponseDTO.get();
    if (principal.hasUserPrincipal()) {
      userProfileHelper.setConnectorDetailsFromUserProfile(yamlGitConfig, connector);
    }
    setRepoUrlInConnector(yamlGitConfig, connector);
    return Optional.of(connector);
  }

  private void setRepoUrlInConnector(YamlGitConfigDTO yamlGitConfig, ConnectorResponseDTO connector) {
    final ScmConnector connectorConfigDTO = (ScmConnector) connector.getConnector().getConnectorConfig();
    connectorConfigDTO.setUrl(yamlGitConfig.getRepo());
  }

  @Override
  public void postPushOperation(PushInfo pushInfo) {
    log.info("Post push info {}", pushInfo);
    final EntityDetail entityDetailDTO =
        entityDetailRestToProtoMapper.createEntityDetailDTO(pushInfo.getEntityDetail());
    final EntityReference entityRef = entityDetailDTO.getEntityRef();
    final EntityDetailProtoDTO entityDetail = pushInfo.getEntityDetail();
    final YamlGitConfigDTO yamlGitConfigDTO = yamlGitConfigService.get(entityRef.getProjectIdentifier(),
        entityRef.getOrgIdentifier(), entityRef.getAccountIdentifier(), pushInfo.getYamlGitConfigId());
    // todo(abhinav): Think about what if something happens in middle of operations.
    saveGitEntity(pushInfo, entityDetail, yamlGitConfigDTO);
    resolveConnectivityErrors(pushInfo, yamlGitConfigDTO);
    if (!pushInfo.getIsSyncFromGit()) {
      saveGitCommit(pushInfo, yamlGitConfigDTO);
      resolveGitToHarnessErrors(pushInfo, yamlGitConfigDTO);
    }
    shortListTheBranch(
        yamlGitConfigDTO, entityRef.getAccountIdentifier(), pushInfo.getBranchName(), pushInfo.getIsNewBranch());

    if (pushInfo.getIsNewBranch()) {
      executorService.submit(
          ()
              -> gitBranchSyncService.createBranchSyncEvent(yamlGitConfigDTO.getAccountIdentifier(),
                  yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(),
                  yamlGitConfigDTO.getIdentifier(), yamlGitConfigDTO.getRepo(), pushInfo.getBranchName(),
                  Arrays.asList(ScmGitUtils.createFilePath(pushInfo.getFolderPath(), pushInfo.getFilePath()))));
    }
  }

  private void resolveConnectivityErrors(PushInfo pushInfo, YamlGitConfigDTO yamlGitConfigDTO) {
    gitSyncErrorService.resolveConnectivityErrors(pushInfo.getAccountId(), yamlGitConfigDTO.getRepo());
  }

  private void resolveGitToHarnessErrors(PushInfo pushInfo, YamlGitConfigDTO yamlGitConfigDTO) {
    String completeFilePath = GitSyncFilePathUtils.createFilePath(pushInfo.getFolderPath(), pushInfo.getFilePath());
    gitSyncErrorService.resolveGitToHarnessErrors(pushInfo.getAccountId(), yamlGitConfigDTO.getRepo(),
        pushInfo.getBranchName(), new HashSet<>(Collections.singleton(completeFilePath)), pushInfo.getCommitId());
  }

  private void saveGitEntity(PushInfo pushInfo, EntityDetailProtoDTO entityDetail, YamlGitConfigDTO yamlGitConfigDTO) {
    gitEntityService.save(pushInfo.getAccountId(), entityDetailRestToProtoMapper.createEntityDetailDTO(entityDetail),
        yamlGitConfigDTO, pushInfo.getFolderPath(), pushInfo.getFilePath(), pushInfo.getCommitId(),
        pushInfo.getBranchName());
  }

  private void saveGitCommit(PushInfo pushInfo, YamlGitConfigDTO yamlGitConfigDTO) {
    gitCommitService.upsertOnCommitIdAndRepoUrlAndGitSyncDirection(
        GitCommitDTO.builder()
            .commitId(pushInfo.getCommitId())
            .accountIdentifier(pushInfo.getAccountId())
            .branchName(pushInfo.getBranchName())
            .gitSyncDirection(GitSyncDirection.HARNESS_TO_GIT)
            .fileProcessingSummary(GitFileProcessingSummary.builder().successCount(1L).build())
            .repoURL(yamlGitConfigDTO.getRepo())
            .status(GitCommitProcessingStatus.COMPLETED)
            .build());
  }

  private void shortListTheBranch(
      YamlGitConfigDTO yamlGitConfigDTO, String accountIdentifier, String branchName, boolean isNewBranch) {
    GitBranch gitBranch = gitBranchService.get(accountIdentifier, yamlGitConfigDTO.getRepo(), branchName);
    if (gitBranch == null) {
      createGitBranch(yamlGitConfigDTO, accountIdentifier, branchName, UNSYNCED);
    }
  }

  @Override
  public Boolean isGitSyncEnabled(EntityScopeInfo entityScopeInfo) {
    return yamlGitConfigService.isGitSyncEnabled(entityScopeInfo.getAccountId(), entityScopeInfo.getOrgId().getValue(),
        entityScopeInfo.getProjectId().getValue());
  }

  @Override
  public Boolean isGitSimplificationEnabled(EntityScopeInfo entityScopeInfo) {
    try {
      if (isEnabled(entityScopeInfo.getAccountId(), FeatureName.NG_GIT_EXPERIENCE)) {
        return true;
      }
      return gitSyncSettingsService.getGitSimplificationStatus(entityScopeInfo.getAccountId(),
          entityScopeInfo.getOrgId().getValue(), entityScopeInfo.getProjectId().getValue());
    } catch (Exception ex) {
      log.error(
          String.format(
              "Exception while checking git Simplification status for accountId: %s , orgId: %s , projectId: %s "),
          entityScopeInfo.getAccountId(), entityScopeInfo.getOrgId().getValue(),
          entityScopeInfo.getProjectId().getValue(), ex);
      return false;
    }
  }

  private void createGitBranch(
      YamlGitConfigDTO yamlGitConfigDTO, String accountId, String branch, BranchSyncStatus synced) {
    GitBranch gitBranch = GitBranch.builder()
                              .accountIdentifier(accountId)
                              .branchName(branch)
                              .branchSyncStatus(synced)
                              .repoURL(yamlGitConfigDTO.getRepo())
                              .build();
    gitBranchService.save(gitBranch);
  }

  @Override
  public BranchDetails getBranchDetails(RepoDetails repoDetails) {
    try {
      final YamlGitConfigDTO yamlGitConfigDTO = yamlGitConfigService.get(repoDetails.getProjectIdentifier().getValue(),
          repoDetails.getOrgIdentifier().getValue(), repoDetails.getAccountId(), repoDetails.getYamlGitConfigId());
      return BranchDetails.newBuilder().setDefaultBranch(yamlGitConfigDTO.getBranch()).build();
    } catch (InvalidRequestException ex) {
      log.error("Error while getting yamlGitConfig", ex);
      String errorMessage = ExceptionUtils.getMessage(ex);
      return BranchDetails.newBuilder().setError(errorMessage).build();
    }
  }

  @Override
  public PushFileResponse pushFile(FileInfo request) {
    final EntityDetail entityDetailDTO = entityDetailRestToProtoMapper.createEntityDetailDTO(request.getEntityDetail());
    final EntityReference entityReference = entityDetailDTO.getEntityRef();
    final String accountId = request.getAccountId();
    final String yamlGitConfigId = request.getYamlGitConfigId();
    final ChangeType changeType = request.getChangeType();
    final YamlGitConfigDTO yamlGitConfig = yamlGitConfigService.get(
        entityReference.getProjectIdentifier(), entityReference.getOrgIdentifier(), accountId, yamlGitConfigId);
    final InfoForGitPush infoForGitPush = getInfoForGitPush(request, entityDetailDTO, accountId, yamlGitConfig);

    switch (changeType) {
      case MODIFY:
        final UpdateFileResponse updateFileResponse =
            scmOrchestratorService.processScmRequest(scmClientFacilitatorService
                -> scmClientFacilitatorService.updateFile(infoForGitPush),
                entityReference.getProjectIdentifier(), entityReference.getOrgIdentifier(), accountId);
        return PushFileResponse.newBuilder()
            .setAccountId(accountId)
            .setError(updateFileResponse.getError())
            .setScmResponseCode(updateFileResponse.getStatus())
            .setStatus(1)
            .setIsDefault(request.getBranch().equals(yamlGitConfig.getBranch()))
            .setDefaultBranchName(yamlGitConfig.getBranch())
            .setCommitId(updateFileResponse.getCommitId())
            .build();
      case ADD:
        final CreateFileResponse createFileResponse =
            scmOrchestratorService.processScmRequest(scmClientFacilitatorService
                -> scmClientFacilitatorService.createFile(infoForGitPush),
                entityReference.getProjectIdentifier(), entityReference.getOrgIdentifier(), accountId);
        return PushFileResponse.newBuilder()
            .setAccountId(accountId)
            .setError(createFileResponse.getError())
            .setScmResponseCode(createFileResponse.getStatus())
            .setStatus(1)
            .setIsDefault(request.getBranch().equals(yamlGitConfig.getBranch()))
            .setDefaultBranchName(yamlGitConfig.getBranch())
            .setCommitId(createFileResponse.getCommitId())
            .build();
      case DELETE:
        final DeleteFileResponse deleteFileResponse =
            scmOrchestratorService.processScmRequest(scmClientFacilitatorService
                -> scmClientFacilitatorService.deleteFile(infoForGitPush),
                entityReference.getProjectIdentifier(), entityReference.getOrgIdentifier(), accountId);
        return PushFileResponse.newBuilder()
            .setAccountId(accountId)
            .setError(deleteFileResponse.getError())
            .setScmResponseCode(deleteFileResponse.getStatus())
            .setStatus(1)
            .setIsDefault(request.getBranch().equals(yamlGitConfig.getBranch()))
            .setDefaultBranchName(yamlGitConfig.getBranch())
            .setCommitId(deleteFileResponse.getCommitId())
            .build();
      default:
        throw new UnexpectedException("Unknown change type encountered.");
    }
  }

  @Override
  public UserPrincipal getFullSyncUser(FileInfo request) {
    final EntityDetail entityDetailDTO = entityDetailRestToProtoMapper.createEntityDetailDTO(request.getEntityDetail());
    final EntityReference entityReference = entityDetailDTO.getEntityRef();
    Optional<GitFullSyncJob> gitFullSyncJob = fullSyncJobService.getRunningJob(
        request.getAccountId(), entityReference.getOrgIdentifier(), entityReference.getProjectIdentifier());
    if (!gitFullSyncJob.isPresent()) {
      throw new InvalidRequestException(
          String.format("There is no running full-sync job for account [%s], orgId [%s], projectId [%s]",
              request.getAccountId(), entityReference.getOrgIdentifier(), entityReference.getProjectIdentifier()));
    }
    return gitFullSyncJob.get().getTriggeredBy();
  }

  @Override
  public GetFileResponse getFileByBranch(GetFileRequest getFileRequest) {
    try {
      Scope scope = ScopeIdentifierMapper.getScopeFromScopeIdentifiers(getFileRequest.getScopeIdentifiers());
      gitFilePathHelper.validateFilePath(getFileRequest.getFilePath());
      ScmGetFileResponseDTO scmGetFileResponseDTO =
          scmFacilitatorService.getFileByBranch(ScmGetFileByBranchRequestDTO.builder()
                                                    .branchName(getFileRequest.getBranchName())
                                                    .connectorRef(getFileRequest.getConnectorRef())
                                                    .filePath(getFileRequest.getFilePath())
                                                    .repoName(getFileRequest.getRepoName())
                                                    .scope(scope)
                                                    .build());
      return prepareGetFileResponse(getFileRequest, scmGetFileResponseDTO, scope);
    } catch (WingsException ex) {
      ScmException scmException = ScmExceptionUtils.getScmException(ex);
      if (scmException == null) {
        return GetFileResponse.newBuilder()
            .setStatusCode(ex.getCode().getStatus().getCode())
            .setError(prepareDefaultErrorDetails(ex))
            .build();
      }
      return GetFileResponse.newBuilder()
          .setStatusCode(ScmErrorCodeToHttpStatusCodeMapping.getHttpStatusCode(scmException.getCode()))
          .setError(prepareErrorDetails(ex))
          .build();
    }
  }

  @Override
  public io.harness.gitsync.CreateFileResponse createFile(CreateFileRequest createFileRequest) {
    try {
      Scope scope = ScopeIdentifierMapper.getScopeFromScopeIdentifiers(createFileRequest.getScopeIdentifiers());
      gitFilePathHelper.validateFilePath(createFileRequest.getFilePath());
      ScmCommitFileResponseDTO scmCommitFileResponseDTO =
          scmFacilitatorService.createFile(ScmCreateFileRequestDTO.builder()
                                               .repoName(createFileRequest.getRepoName())
                                               .branchName(createFileRequest.getBranchName())
                                               .connectorRef(createFileRequest.getConnectorRef())
                                               .fileContent(createFileRequest.getFileContent())
                                               .filePath(createFileRequest.getFilePath())
                                               .commitMessage(createFileRequest.getCommitMessage())
                                               .baseBranch(createFileRequest.getBaseBranchName())
                                               .isCommitToNewBranch(createFileRequest.getIsCommitToNewBranch())
                                               .scope(scope)
                                               .build());
      return prepareCreateFileResponse(createFileRequest, scmCommitFileResponseDTO, scope);
    } catch (WingsException ex) {
      ScmException scmException = ScmExceptionUtils.getScmException(ex);
      if (scmException == null) {
        return io.harness.gitsync.CreateFileResponse.newBuilder()
            .setStatusCode(ex.getCode().getStatus().getCode())
            .setError(prepareDefaultErrorDetails(ex))
            .build();
      }
      return io.harness.gitsync.CreateFileResponse.newBuilder()
          .setStatusCode(ScmErrorCodeToHttpStatusCodeMapping.getHttpStatusCode(scmException.getCode()))
          .setError(prepareErrorDetails(ex))
          .build();
    }
  }

  @Override
  public io.harness.gitsync.UpdateFileResponse updateFile(UpdateFileRequest updateFileRequest) {
    try {
      Scope scope = ScopeIdentifierMapper.getScopeFromScopeIdentifiers(updateFileRequest.getScopeIdentifiers());
      gitFilePathHelper.validateFilePath(updateFileRequest.getFilePath());
      ScmCommitFileResponseDTO scmCommitFileResponseDTO =
          scmFacilitatorService.updateFile(ScmUpdateFileRequestDTO.builder()
                                               .repoName(updateFileRequest.getRepoName())
                                               .branchName(updateFileRequest.getBranchName())
                                               .connectorRef(updateFileRequest.getConnectorRef())
                                               .fileContent(updateFileRequest.getFileContent())
                                               .filePath(updateFileRequest.getFilePath())
                                               .commitMessage(updateFileRequest.getCommitMessage())
                                               .oldCommitId(updateFileRequest.getOldCommitId())
                                               .baseBranch(updateFileRequest.getBaseBranchName())
                                               .oldFileSha(updateFileRequest.getOldFileSha())
                                               .isCommitToNewBranch(updateFileRequest.getIsCommitToNewBranch())
                                               .scope(scope)
                                               .build());
      return prepareUpdateFileResponse(updateFileRequest, scmCommitFileResponseDTO, scope);
    } catch (WingsException ex) {
      ScmException scmException = ScmExceptionUtils.getScmException(ex);
      if (scmException == null) {
        return io.harness.gitsync.UpdateFileResponse.newBuilder()
            .setStatusCode(ex.getCode().getStatus().getCode())
            .setError(prepareDefaultErrorDetails(ex))
            .build();
      }
      return io.harness.gitsync.UpdateFileResponse.newBuilder()
          .setStatusCode(ScmErrorCodeToHttpStatusCodeMapping.getHttpStatusCode(scmException.getCode()))
          .setError(prepareErrorDetails(ex))
          .build();
    }
  }

  @Override
  public CreatePRResponse createPullRequest(CreatePRRequest createPRRequest) {
    ScmCreatePRResponseDTO scmCreatePRResponseDTO = scmFacilitatorService.createPR(
        ScmCreatePRRequestDTO.builder()
            .sourceBranch(createPRRequest.getSourceBranch())
            .targetBranch(createPRRequest.getTargetBranch())
            .scope(ScopeIdentifierMapper.getScopeFromScopeIdentifiers(createPRRequest.getScopeIdentifiers()))
            .repoName(createPRRequest.getRepoName())
            .connectorRef(createPRRequest.getConnectorRef())
            .title(createPRRequest.getTitle())
            .build());

    return CreatePRResponse.newBuilder().setStatusCode(200).setPrNumber(scmCreatePRResponseDTO.getPrNumber()).build();
  }

  private InfoForGitPush getInfoForGitPush(
      FileInfo request, EntityDetail entityDetailDTO, String accountId, YamlGitConfigDTO yamlGitConfig) {
    Principal principal = request.getPrincipal();
    if (request.getIsFullSyncFlow()) {
      principal = Principal.newBuilder().setUserPrincipal(userProfileHelper.getUserPrincipal()).build();
    }
    final Optional<ConnectorResponseDTO> connector = getConnector(accountId, yamlGitConfig, principal);
    if (!connector.isPresent()) {
      throw new InvalidRequestException(
          String.format("Connector with identifier %s deleted", yamlGitConfig.getGitConnectorRef()));
    }
    final ScmConnector connectorConfig = (ScmConnector) connector.get().getConnector().getConnectorConfig();
    connectorConfig.setUrl(yamlGitConfig.getRepo());

    return InfoForGitPush.builder()
        .accountId(accountId)
        .orgIdentifier(entityDetailDTO.getEntityRef().getOrgIdentifier())
        .projectIdentifier(entityDetailDTO.getEntityRef().getProjectIdentifier())
        .branch(request.getBranch())
        .baseBranch(StringValueUtils.getStringFromStringValue(request.getBaseBranch()))
        .isNewBranch(request.getIsNewBranch())
        .commitMsg(StringValueUtils.getStringFromStringValue(request.getCommitMsg()))
        .filePath(request.getFilePath())
        .folderPath(request.getFolderPath())
        .oldFileSha(StringValueUtils.getStringFromStringValue(request.getOldFileSha()))
        .yaml(request.getYaml())
        .scmConnector(connectorConfig)
        .commitId(fetchLastCommitIdForFile(request, entityDetailDTO, connectorConfig))
        .build();
  }

  @VisibleForTesting
  protected String fetchLastCommitIdForFile(FileInfo request, EntityDetail entityDetailDTO) {
    // Incoming commit id could be a conflict resolved commit id for an entity
    String lastCommitIdForFile = request.getCommitId() == null ? "" : request.getCommitId();
    if (isEmpty(lastCommitIdForFile) && request.getChangeType() != ChangeType.ADD) {
      // If its saveToNewBranch use-case, then we choose base branch for existing entity commit
      String branch = request.getIsNewBranch() ? request.getBaseBranch().getValue() : request.getBranch();
      GitSyncEntityDTO gitSyncEntityDTO =
          gitEntityService.get(entityDetailDTO.getEntityRef(), entityDetailDTO.getType(), branch);
      lastCommitIdForFile = gitSyncEntityDTO.getLastCommitId();
    }
    return lastCommitIdForFile;
  }

  private String fetchLastCommitIdForFile(
      FileInfo request, EntityDetail entityDetailDTO, ScmConnector connectorConfig) {
    // Perform fetch commit id ops for only bitbucket for now
    if (ConnectorType.BITBUCKET.equals(connectorConfig.getConnectorType())) {
      return fetchLastCommitIdForFile(request, entityDetailDTO);
    }
    // Return dummy commit id in other cases, will not be used anywhere
    return "";
  }

  private GetFileResponse prepareGetFileResponse(
      GetFileRequest getFileRequest, ScmGetFileResponseDTO scmGetFileResponseDTO, Scope scope) {
    return GetFileResponse.newBuilder()
        .setStatusCode(HTTP_200)
        .setFileContent(scmGetFileResponseDTO.getFileContent())
        .setGitMetaData(
            GitMetaData.newBuilder()
                .setRepoName(getFileRequest.getRepoName())
                .setBranchName(scmGetFileResponseDTO.getBranchName())
                .setCommitId(scmGetFileResponseDTO.getCommitId())
                .setBlobId(scmGetFileResponseDTO.getBlobId())
                .setFilePath(getFileRequest.getFilePath())
                .setFileUrl(gitFilePathHelper.getFileUrl(scope, getFileRequest.getConnectorRef(),
                    getFileRequest.getRepoName(), getFileRequest.getBranchName(), getFileRequest.getFilePath()))
                .build())
        .build();
  }

  private io.harness.gitsync.CreateFileResponse prepareCreateFileResponse(
      CreateFileRequest createFileRequest, ScmCommitFileResponseDTO scmCommitFileResponseDTO, Scope scope) {
    return io.harness.gitsync.CreateFileResponse.newBuilder()
        .setStatusCode(HTTP_200)
        .setGitMetaData(GitMetaData.newBuilder()
                            .setFilePath(createFileRequest.getFilePath())
                            .setRepoName(createFileRequest.getRepoName())
                            .setBranchName(createFileRequest.getBranchName())
                            .setCommitId(scmCommitFileResponseDTO.getCommitId())
                            .setBlobId(scmCommitFileResponseDTO.getBlobId())
                            .setFileUrl(gitFilePathHelper.getFileUrl(scope, createFileRequest.getConnectorRef(),
                                createFileRequest.getRepoName(), createFileRequest.getBranchName(),
                                createFileRequest.getFilePath()))
                            .build())
        .build();
  }

  private io.harness.gitsync.UpdateFileResponse prepareUpdateFileResponse(
      UpdateFileRequest updateFileRequest, ScmCommitFileResponseDTO scmCommitFileResponseDTO, Scope scope) {
    return io.harness.gitsync.UpdateFileResponse.newBuilder()
        .setStatusCode(HTTP_200)
        .setGitMetaData(GitMetaData.newBuilder()
                            .setFilePath(updateFileRequest.getFilePath())
                            .setRepoName(updateFileRequest.getRepoName())
                            .setBranchName(updateFileRequest.getBranchName())
                            .setCommitId(scmCommitFileResponseDTO.getCommitId())
                            .setBlobId(scmCommitFileResponseDTO.getBlobId())
                            .setFileUrl(gitFilePathHelper.getFileUrl(scope, updateFileRequest.getConnectorRef(),
                                updateFileRequest.getRepoName(), updateFileRequest.getBranchName(),
                                updateFileRequest.getFilePath()))
                            .build())
        .build();
  }

  private ErrorDetails prepareErrorDetails(WingsException ex) {
    ScmException scmException = ScmExceptionUtils.getScmException(ex);
    return ErrorDetails.newBuilder()
        .setErrorMessage(scmException.getMessage())
        .setExplanationMessage(ScmExceptionUtils.getExplanationMessage(ex))
        .setHintMessage(ScmExceptionUtils.getHintMessage(ex))
        .build();
  }

  private ErrorDetails prepareDefaultErrorDetails(WingsException ex) {
    return ErrorDetails.newBuilder()
        .setErrorMessage(ScmExceptionUtils.getMessage(ex))
        .setExplanationMessage(ScmExceptionUtils.getExplanationMessage(ex))
        .setHintMessage(ScmExceptionUtils.getHintMessage(ex))
        .build();
  }

  private boolean isEnabled(String accountId, FeatureName featureName) {
    return RestClientUtils.getResponse(accountClient.isFeatureFlagEnabled(featureName.name(), accountId));
  }
}
