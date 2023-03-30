/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.GetBatchFileRequestIdentifier;
import io.harness.beans.IdentifierRef;
import io.harness.beans.PageRequestDTO;
import io.harness.beans.Scope;
import io.harness.beans.WebhookEncryptedSecretDTO;
import io.harness.beans.gitsync.GitFileDetails;
import io.harness.beans.gitsync.GitFileDetails.GitFileDetailsBuilder;
import io.harness.beans.gitsync.GitFilePathDetails;
import io.harness.beans.gitsync.GitPRCreateRequest;
import io.harness.beans.gitsync.GitWebhookDetails;
import io.harness.beans.request.GitFileBatchRequest;
import io.harness.beans.request.GitFileRequest;
import io.harness.beans.request.GitFileRequestV2;
import io.harness.beans.request.ListFilesInCommitRequest;
import io.harness.beans.response.GitFileBatchResponse;
import io.harness.beans.response.GitFileResponse;
import io.harness.beans.response.ListFilesInCommitResponse;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.impl.ConnectorErrorMessagesHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.scm.ConnectorDecryptionParams;
import io.harness.delegate.task.scm.GetFileTaskParamsPerConnector;
import io.harness.delegate.task.scm.GitFileLocationDetails;
import io.harness.delegate.task.scm.GitFileTaskResponseData;
import io.harness.delegate.task.scm.GitFileTaskType;
import io.harness.delegate.task.scm.GitPRTaskType;
import io.harness.delegate.task.scm.GitRefType;
import io.harness.delegate.task.scm.GitWebhookTaskType;
import io.harness.delegate.task.scm.ScmBatchGetFileTaskParams;
import io.harness.delegate.task.scm.ScmBatchGetFileTaskResponseData;
import io.harness.delegate.task.scm.ScmGitFileTaskParams;
import io.harness.delegate.task.scm.ScmGitRefTaskParams;
import io.harness.delegate.task.scm.ScmGitRefTaskResponseData;
import io.harness.delegate.task.scm.ScmGitWebhookTaskParams;
import io.harness.delegate.task.scm.ScmGitWebhookTaskResponseData;
import io.harness.delegate.task.scm.ScmPRTaskParams;
import io.harness.delegate.task.scm.ScmPRTaskResponseData;
import io.harness.delegate.task.scm.ScmPushTaskParams;
import io.harness.delegate.task.scm.ScmPushTaskResponseData;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.common.beans.ConnectorDetails;
import io.harness.gitsync.common.beans.InfoForGitPush;
import io.harness.gitsync.common.dtos.CreateGitFileRequestDTO;
import io.harness.gitsync.common.dtos.CreatePRDTO;
import io.harness.gitsync.common.dtos.GetLatestCommitOnFileRequestDTO;
import io.harness.gitsync.common.dtos.GitDiffResultFileListDTO;
import io.harness.gitsync.common.dtos.GitFileChangeDTO;
import io.harness.gitsync.common.dtos.GitFileContent;
import io.harness.gitsync.common.dtos.UpdateGitFileRequestDTO;
import io.harness.gitsync.common.helper.FileBatchResponseMapper;
import io.harness.gitsync.common.helper.GitSyncConnectorHelper;
import io.harness.gitsync.common.helper.GitSyncUtils;
import io.harness.gitsync.common.helper.PRFileListMapper;
import io.harness.gitsync.common.helper.UserProfileHelper;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.impl.ScmResponseStatusUtils;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.ng.webhook.UpsertWebhookRequestDTO;
import io.harness.ngtriggers.WebhookSecretData;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.CompareCommitsResponse;
import io.harness.product.ci.scm.proto.CreateBranchResponse;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.product.ci.scm.proto.CreatePRResponse;
import io.harness.product.ci.scm.proto.CreateWebhookResponse;
import io.harness.product.ci.scm.proto.DeleteFileResponse;
import io.harness.product.ci.scm.proto.FileBatchContentResponse;
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.product.ci.scm.proto.FindCommitResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitOnFileResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitResponse;
import io.harness.product.ci.scm.proto.GetUserRepoResponse;
import io.harness.product.ci.scm.proto.GetUserReposResponse;
import io.harness.product.ci.scm.proto.ListBranchesResponse;
import io.harness.product.ci.scm.proto.ListBranchesWithDefaultResponse;
import io.harness.product.ci.scm.proto.ListCommitsResponse;
import io.harness.product.ci.scm.proto.UpdateFileResponse;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.PrincipalContextData;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.utils.IdentifierRefHelper;

import software.wings.beans.TaskType;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

// Don't inject this directly go through ScmClientOrchestrator.
@Slf4j
@OwnedBy(DX)
public class ScmDelegateFacilitatorServiceImpl extends AbstractScmClientFacilitatorServiceImpl {
  private SecretManagerClientService secretManagerClientService;
  private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  private GitSyncConnectorHelper gitSyncConnectorHelper;
  private final String errorFormat =
      "Unexpected error occurred while doing scm operation for %s for accountId [%s], orgId [%s], projectId [%s]";

  @Inject
  public ScmDelegateFacilitatorServiceImpl(@Named("connectorDecoratorService") ConnectorService connectorService,
      ConnectorErrorMessagesHelper connectorErrorMessagesHelper, YamlGitConfigService yamlGitConfigService,
      SecretManagerClientService secretManagerClientService, DelegateGrpcClientWrapper delegateGrpcClientWrapper,
      UserProfileHelper userProfileHelper, GitSyncConnectorHelper gitSyncConnectorHelper) {
    super(connectorService, connectorErrorMessagesHelper, yamlGitConfigService, userProfileHelper,
        gitSyncConnectorHelper);
    this.secretManagerClientService = secretManagerClientService;
    this.delegateGrpcClientWrapper = delegateGrpcClientWrapper;
    this.gitSyncConnectorHelper = gitSyncConnectorHelper;
  }

  @Override
  public List<String> listBranchesForRepoByConnector(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorIdentifierRef, String repoURL, PageRequest pageRequest,
      String searchTerm) {
    final ScmConnector scmConnector = getSCMConnectorUsedInGitSyncConfig(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifierRef, null, null);
    scmConnector.setUrl(repoURL);
    final List<EncryptedDataDetail> encryptionDetails =
        getEncryptedDataDetails(accountIdentifier, orgIdentifier, projectIdentifier, scmConnector);
    final ScmGitRefTaskParams scmGitRefTaskParams =
        getScmGitRefTaskParams(scmConnector, encryptionDetails, GitRefType.BRANCH);
    DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(
        accountIdentifier, orgIdentifier, projectIdentifier, scmGitRefTaskParams, TaskType.SCM_GIT_REF_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) delegateResponseData;
    try {
      return ListBranchesResponse.parseFrom(scmGitRefTaskResponseData.getListBranchesResponse()).getBranchesList();
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation");
    }
  }

  @Override
  public GitFileContent getFileContent(String yamlGitConfigIdentifier, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String filePath, String branch, String commitId) {
    validateFileContentParams(branch, commitId);
    YamlGitConfigDTO yamlGitConfigDTO =
        getYamlGitConfigDTO(accountIdentifier, orgIdentifier, projectIdentifier, yamlGitConfigIdentifier);
    final ScmConnector scmConnector = getSCMConnectorUsedInGitSyncConfig(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(),
        yamlGitConfigDTO.getGitConnectorRef(), yamlGitConfigDTO.getGitConnectorsRepo(),
        yamlGitConfigDTO.getGitConnectorsBranch());
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails =
        getEncryptedDataDetails(accountIdentifier, orgIdentifier, projectIdentifier, scmConnector);
    final GitFilePathDetails gitFilePathDetails = getGitFilePathDetails(filePath, branch, commitId, false);
    final ScmGitFileTaskParams scmGitFileTaskParams = getScmGitFileTaskParams(
        scmConnector, encryptionDetails, gitFilePathDetails, GitFileTaskType.GET_FILE_CONTENT, null, branch, null);
    DelegateTaskRequest delegateTaskRequest =
        getDelegateTaskRequest(accountIdentifier, yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), scmGitFileTaskParams, TaskType.SCM_GIT_FILE_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    GitFileTaskResponseData gitFileTaskResponseData = (GitFileTaskResponseData) delegateResponseData;
    try {
      return validateAndGetGitFileContent(FileContent.parseFrom(gitFileTaskResponseData.getFileContent()));
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation");
    }
  }

  @Override
  public FileContent getFile(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorRef, String repoName, String branchName, String filePath, String commitId) {
    ScmConnector connector = gitSyncConnectorHelper.getScmConnectorForGivenRepo(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, repoName);
    final List<EncryptedDataDetail> encryptionDetails =
        getEncryptedDataDetailsForNewGitX(accountIdentifier, orgIdentifier, projectIdentifier, connector);
    final GitFilePathDetails gitFilePathDetails = getGitFilePathDetails(filePath, branchName, commitId, false);
    final ScmGitFileTaskParams scmGitFileTaskParams = getScmGitFileTaskParams(
        connector, encryptionDetails, gitFilePathDetails, GitFileTaskType.GET_FILE_CONTENT, commitId, branchName, null);
    DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(
        accountIdentifier, orgIdentifier, projectIdentifier, scmGitFileTaskParams, TaskType.SCM_GIT_FILE_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    GitFileTaskResponseData gitFileTaskResponseData = (GitFileTaskResponseData) delegateResponseData;
    try {
      return FileContent.parseFrom(gitFileTaskResponseData.getFileContent());
    } catch (InvalidProtocolBufferException e) {
      log.error("Error while getFile SCM Ops", e);
      throw new UnexpectedException("Unexpected error occurred while doing scm operation", e);
    }
  }

  @Override
  public CreatePRResponse createPullRequest(
      Scope scope, String connectorRef, String repoName, String sourceBranch, String targetBranch, String title) {
    final ScmConnector decryptedConnector = gitSyncConnectorHelper.getScmConnectorForGivenRepo(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), connectorRef, repoName);
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetailsForNewGitX(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), decryptedConnector);
    ScmPRTaskParams scmPRTaskParams = ScmPRTaskParams.builder()
                                          .scmConnector(decryptedConnector)
                                          .sourceBranchName(sourceBranch)
                                          .targetBranchName(targetBranch)
                                          .prTitle(title)
                                          .gitPRTaskType(GitPRTaskType.CREATE_PR_V2)
                                          .encryptedDataDetails(encryptionDetails)
                                          .build();
    final Map<String, String> ngTaskSetupAbstractionsWithOwner = getNGTaskSetupAbstractionsWithOwner(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier());
    DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
                                                  .accountId(scope.getAccountIdentifier())
                                                  .taskSetupAbstractions(ngTaskSetupAbstractionsWithOwner)
                                                  .taskType(TaskType.SCM_PULL_REQUEST_TASK.name())
                                                  .taskParameters(scmPRTaskParams)
                                                  .executionTimeout(Duration.ofMinutes(2))
                                                  .build();
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmPRTaskResponseData scmCreatePRResponse = (ScmPRTaskResponseData) delegateResponseData;
    return scmCreatePRResponse.getCreatePRResponse();
  }

  @Override
  public CreatePRDTO createPullRequest(GitPRCreateRequest gitCreatePRRequest) {
    validateTheCreatePRRequest(gitCreatePRRequest);
    YamlGitConfigDTO yamlGitConfigDTO =
        getYamlGitConfigDTO(gitCreatePRRequest.getAccountIdentifier(), gitCreatePRRequest.getOrgIdentifier(),
            gitCreatePRRequest.getProjectIdentifier(), gitCreatePRRequest.getYamlGitConfigRef());
    final IdentifierRef gitConnectorIdentifierRef =
        getConnectorIdentifierRef(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), yamlGitConfigDTO.getGitConnectorRef());
    ConnectorResponseDTO connectorResponseDTO =
        getConnectorResponseDTO(yamlGitConfigDTO, gitCreatePRRequest.getAccountIdentifier());
    checkAndSetUserFromUserProfile(gitCreatePRRequest.isUseUserFromToken(), yamlGitConfigDTO, connectorResponseDTO);
    final ScmConnector scmConnector = (ScmConnector) connectorResponseDTO.getConnector().getConnectorConfig();
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails =
        getEncryptedDataDetails(gitCreatePRRequest.getAccountIdentifier(), gitCreatePRRequest.getOrgIdentifier(),
            gitCreatePRRequest.getProjectIdentifier(), scmConnector);
    ScmPRTaskParams scmPRTaskParams = ScmPRTaskParams.builder()
                                          .scmConnector(scmConnector)
                                          .gitPRCreateRequest(gitCreatePRRequest)
                                          .gitPRTaskType(GitPRTaskType.CREATE_PR)
                                          .encryptedDataDetails(encryptionDetails)
                                          .build();
    final Map<String, String> ngTaskSetupAbstractionsWithOwner =
        getNGTaskSetupAbstractionsWithOwner(gitConnectorIdentifierRef.getAccountIdentifier(),
            gitConnectorIdentifierRef.getOrgIdentifier(), gitConnectorIdentifierRef.getProjectIdentifier());
    DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.builder()
                                                  .accountId(gitConnectorIdentifierRef.getAccountIdentifier())
                                                  .taskSetupAbstractions(ngTaskSetupAbstractionsWithOwner)
                                                  .taskType(TaskType.SCM_PULL_REQUEST_TASK.name())
                                                  .taskParameters(scmPRTaskParams)
                                                  .executionTimeout(Duration.ofMinutes(2))
                                                  .build();
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmPRTaskResponseData scmCreatePRResponse = (ScmPRTaskResponseData) delegateResponseData;
    final CreatePRResponse createPRResponse = scmCreatePRResponse.getCreatePRResponse();
    try {
      ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
          createPRResponse.getStatus(), createPRResponse.getError());
    } catch (WingsException e) {
      throw new ExplanationException(String.format("Could not create the pull request from %s to %s",
                                         gitCreatePRRequest.getSourceBranch(), gitCreatePRRequest.getTargetBranch()),
          e);
    }
    return CreatePRDTO.builder().prNumber(createPRResponse.getNumber()).build();
  }

  private void validateTheCreatePRRequest(GitPRCreateRequest gitCreatePRRequest) {
    if (gitCreatePRRequest == null) {
      throw new InvalidRequestException("The CreatePR request cannot be null");
    }
    if (gitCreatePRRequest.getSourceBranch().equals(gitCreatePRRequest.getTargetBranch())) {
      throw new InvalidRequestException("The PR cannot be created for the same source and target branch");
    }
  }

  @Override
  public List<GitFileChangeDTO> listFilesOfBranches(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String yamlGitConfigRef, Set<String> foldersList, String branchName) {
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(yamlGitConfigRef, accountIdentifier, orgIdentifier, projectIdentifier);
    YamlGitConfigDTO yamlGitConfigDTO = getYamlGitConfigDTO(identifierRef.getAccountIdentifier(),
        identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
    final ScmConnector scmConnector = getSCMConnectorUsedInGitSyncConfig(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(),
        yamlGitConfigDTO.getGitConnectorRef(), yamlGitConfigDTO.getGitConnectorsRepo(),
        yamlGitConfigDTO.getGitConnectorsBranch());
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails =
        getEncryptedDataDetails(accountIdentifier, orgIdentifier, projectIdentifier, scmConnector);
    ScmGitFileTaskParams scmGitFileTaskParams = ScmGitFileTaskParams.builder()
                                                    .gitFileTaskType(GitFileTaskType.GET_FILE_CONTENT_BATCH)
                                                    .branch(branchName)
                                                    .scmConnector(scmConnector)
                                                    .foldersList(foldersList)
                                                    .encryptedDataDetails(encryptionDetails)
                                                    .build();
    DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(identifierRef.getAccountIdentifier(),
        orgIdentifier, projectIdentifier, scmGitFileTaskParams, TaskType.SCM_GIT_FILE_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    GitFileTaskResponseData gitFileTaskResponseData = (GitFileTaskResponseData) delegateResponseData;
    try {
      return FileBatchResponseMapper.createGitFileChangeList(
          FileBatchContentResponse.parseFrom(gitFileTaskResponseData.getFileBatchContentResponse()),
          gitFileTaskResponseData.getCommitId());
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation");
    }
  }

  @Override
  public List<GitFileChangeDTO> listFilesByFilePaths(
      YamlGitConfigDTO yamlGitConfigDTO, List<String> filePaths, String branchName) {
    final ScmConnector scmConnector = getSCMConnectorUsedInGitSyncConfig(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(),
        yamlGitConfigDTO.getGitConnectorRef(), yamlGitConfigDTO.getGitConnectorsRepo(),
        yamlGitConfigDTO.getGitConnectorsBranch());
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(), scmConnector);
    final ScmGitFileTaskParams scmGitFileTaskParams = getScmGitFileTaskParams(scmConnector, encryptionDetails, null,
        GitFileTaskType.GET_FILE_CONTENT_BATCH_BY_FILE_PATHS, null, branchName, filePaths);
    DelegateTaskRequest delegateTaskRequest =
        getDelegateTaskRequest(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), scmGitFileTaskParams, TaskType.SCM_GIT_FILE_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    GitFileTaskResponseData gitFileTaskResponseData = (GitFileTaskResponseData) delegateResponseData;
    try {
      return FileBatchResponseMapper.createGitFileChangeList(
          FileBatchContentResponse.parseFrom(gitFileTaskResponseData.getFileBatchContentResponse()),
          gitFileTaskResponseData.getCommitId());
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation");
    }
  }

  @Override
  public List<GitFileChangeDTO> listFilesByCommitId(
      YamlGitConfigDTO yamlGitConfigDTO, List<String> filePaths, String commitId) {
    final ScmConnector scmConnector = getSCMConnectorUsedInGitSyncConfig(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(),
        yamlGitConfigDTO.getGitConnectorRef(), yamlGitConfigDTO.getGitConnectorsRepo(),
        yamlGitConfigDTO.getGitConnectorsBranch());
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(), scmConnector);
    final ScmGitFileTaskParams scmGitFileTaskParams = getScmGitFileTaskParams(scmConnector, encryptionDetails, null,
        GitFileTaskType.GET_FILE_CONTENT_BATCH_BY_REF, commitId, null, filePaths);
    DelegateTaskRequest delegateTaskRequest =
        getDelegateTaskRequest(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), scmGitFileTaskParams, TaskType.SCM_GIT_FILE_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    GitFileTaskResponseData gitFileTaskResponseData = (GitFileTaskResponseData) delegateResponseData;
    try {
      return FileBatchResponseMapper.createGitFileChangeList(
          FileBatchContentResponse.parseFrom(gitFileTaskResponseData.getFileBatchContentResponse()),
          gitFileTaskResponseData.getCommitId());
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation");
    }
  }

  @Override
  public GitDiffResultFileListDTO listCommitsDiffFiles(
      YamlGitConfigDTO yamlGitConfigDTO, String initialCommitId, String finalCommitId) {
    final ScmConnector scmConnector = getSCMConnectorUsedInGitSyncConfig(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(),
        yamlGitConfigDTO.getGitConnectorRef(), yamlGitConfigDTO.getGitConnectorsRepo(),
        yamlGitConfigDTO.getGitConnectorsBranch());
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(), scmConnector);
    final ScmGitRefTaskParams scmGitRefTaskParams = ScmGitRefTaskParams.builder()
                                                        .gitRefType(GitRefType.COMPARE_COMMITS)
                                                        .initialCommitId(initialCommitId)
                                                        .finalCommitId(finalCommitId)
                                                        .scmConnector(scmConnector)
                                                        .encryptedDataDetails(encryptionDetails)
                                                        .build();
    DelegateTaskRequest delegateTaskRequest =
        getDelegateTaskRequest(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), scmGitRefTaskParams, TaskType.SCM_GIT_REF_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) delegateResponseData;
    try {
      return PRFileListMapper.toGitDiffResultFileListDTO(
          CompareCommitsResponse.parseFrom(scmGitRefTaskResponseData.getCompareCommitsResponse()).getFilesList());
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation");
    }
  }

  @Override
  public List<String> listCommits(YamlGitConfigDTO yamlGitConfigDTO, String branch) {
    final ScmConnector scmConnector = getSCMConnectorUsedInGitSyncConfig(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(),
        yamlGitConfigDTO.getGitConnectorRef(), yamlGitConfigDTO.getGitConnectorsRepo(),
        yamlGitConfigDTO.getGitConnectorsBranch());
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(), scmConnector);
    final ScmGitRefTaskParams scmGitRefTaskParams = ScmGitRefTaskParams.builder()
                                                        .gitRefType(GitRefType.COMMIT)
                                                        .scmConnector(scmConnector)
                                                        .encryptedDataDetails(encryptionDetails)
                                                        .branch(branch)
                                                        .build();
    DelegateTaskRequest delegateTaskRequest =
        getDelegateTaskRequest(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), scmGitRefTaskParams, TaskType.SCM_GIT_REF_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) delegateResponseData;
    try {
      return new ArrayList<>(
          ListCommitsResponse.parseFrom(scmGitRefTaskResponseData.getListCommitsResponse()).getCommitIdsList());
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation");
    }
  }

  @Override
  public Commit getLatestCommit(YamlGitConfigDTO yamlGitConfigDTO, String branch) {
    final ScmConnector scmConnector = getSCMConnectorUsedInGitSyncConfig(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(),
        yamlGitConfigDTO.getGitConnectorRef(), yamlGitConfigDTO.getGitConnectorsRepo(),
        yamlGitConfigDTO.getGitConnectorsBranch());
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(), scmConnector);
    final ScmGitRefTaskParams scmGitRefTaskParams = ScmGitRefTaskParams.builder()
                                                        .gitRefType(GitRefType.LATEST_COMMIT_ID)
                                                        .scmConnector(scmConnector)
                                                        .encryptedDataDetails(encryptionDetails)
                                                        .branch(branch)
                                                        .build();
    DelegateTaskRequest delegateTaskRequest =
        getDelegateTaskRequest(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), scmGitRefTaskParams, TaskType.SCM_GIT_REF_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) delegateResponseData;
    try {
      return GetLatestCommitResponse.parseFrom(scmGitRefTaskResponseData.getGetLatestCommitResponse()).getCommit();
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation");
    }
  }

  @Override
  public CreateFileResponse createFile(InfoForGitPush infoForPush) {
    GitFileDetailsBuilder gitFileDetails = getGitFileDetails(infoForPush.getAccountId(), infoForPush.getYaml(),
        infoForPush.getFilePath(), infoForPush.getFolderPath(), infoForPush.getCommitMsg(), infoForPush.getBranch(),
        SCMType.fromConnectorType(infoForPush.getScmConnector().getConnectorType()), infoForPush.getCommitId());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(infoForPush.getAccountId(),
        infoForPush.getOrgIdentifier(), infoForPush.getProjectIdentifier(), infoForPush.getScmConnector());
    ScmPushTaskParams scmPushTaskParams = ScmPushTaskParams.builder()
                                              .changeType(ChangeType.ADD)
                                              .scmConnector(infoForPush.getScmConnector())
                                              .gitFileDetails(gitFileDetails.build())
                                              .encryptedDataDetails(encryptionDetails)
                                              .isNewBranch(infoForPush.isNewBranch())
                                              .baseBranch(infoForPush.getBaseBranch())
                                              .build();

    final DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(infoForPush.getAccountId(),
        infoForPush.getOrgIdentifier(), infoForPush.getProjectIdentifier(), scmPushTaskParams, TaskType.SCM_PUSH_TASK);

    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmPushTaskResponseData scmPushTaskResponseData = (ScmPushTaskResponseData) delegateResponseData;
    try {
      return CreateFileResponse.parseFrom(scmPushTaskResponseData.getCreateFileResponse());
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation", e);
    }
  }

  @Override
  public UpdateFileResponse updateFile(InfoForGitPush infoForPush) {
    GitFileDetailsBuilder gitFileDetails = getGitFileDetails(infoForPush.getAccountId(), infoForPush.getYaml(),
        infoForPush.getFilePath(), infoForPush.getFolderPath(), infoForPush.getCommitMsg(), infoForPush.getBranch(),
        SCMType.fromConnectorType(infoForPush.getScmConnector().getConnectorType()), infoForPush.getCommitId());
    gitFileDetails.oldFileSha(infoForPush.getOldFileSha());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(infoForPush.getAccountId(),
        infoForPush.getOrgIdentifier(), infoForPush.getProjectIdentifier(), infoForPush.getScmConnector());
    ScmPushTaskParams scmPushTaskParams = ScmPushTaskParams.builder()
                                              .changeType(ChangeType.MODIFY)
                                              .scmConnector(infoForPush.getScmConnector())
                                              .gitFileDetails(gitFileDetails.build())
                                              .encryptedDataDetails(encryptionDetails)
                                              .isNewBranch(infoForPush.isNewBranch())
                                              .baseBranch(infoForPush.getBaseBranch())
                                              .build();

    final DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(infoForPush.getAccountId(),
        infoForPush.getOrgIdentifier(), infoForPush.getProjectIdentifier(), scmPushTaskParams, TaskType.SCM_PUSH_TASK);

    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmPushTaskResponseData scmPushTaskResponseData = (ScmPushTaskResponseData) delegateResponseData;
    try {
      return UpdateFileResponse.parseFrom(scmPushTaskResponseData.getUpdateFileResponse());
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation", e);
    }
  }

  @Override
  public DeleteFileResponse deleteFile(InfoForGitPush infoForPush) {
    GitFileDetailsBuilder gitFileDetails = getGitFileDetails(infoForPush.getAccountId(), infoForPush.getYaml(),
        infoForPush.getFilePath(), infoForPush.getFolderPath(), infoForPush.getCommitMsg(), infoForPush.getBranch(),
        SCMType.fromConnectorType(infoForPush.getScmConnector().getConnectorType()), infoForPush.getCommitId());
    gitFileDetails.oldFileSha(infoForPush.getOldFileSha());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(infoForPush.getAccountId(),
        infoForPush.getOrgIdentifier(), infoForPush.getProjectIdentifier(), infoForPush.getScmConnector());
    ScmPushTaskParams scmPushTaskParams = ScmPushTaskParams.builder()
                                              .changeType(ChangeType.DELETE)
                                              .scmConnector(infoForPush.getScmConnector())
                                              .gitFileDetails(gitFileDetails.build())
                                              .encryptedDataDetails(encryptionDetails)
                                              .isNewBranch(infoForPush.isNewBranch())
                                              .baseBranch(infoForPush.getBaseBranch())
                                              .build();

    final DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(infoForPush.getAccountId(),
        infoForPush.getOrgIdentifier(), infoForPush.getProjectIdentifier(), scmPushTaskParams, TaskType.SCM_PUSH_TASK);

    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmPushTaskResponseData scmPushTaskResponseData = (ScmPushTaskResponseData) delegateResponseData;
    try {
      return DeleteFileResponse.parseFrom(scmPushTaskResponseData.getDeleteFileResponse());
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation", e);
    }
  }

  @Override
  public Commit findCommitById(YamlGitConfigDTO yamlGitConfigDTO, String commitId) {
    final ScmConnector scmConnector = getSCMConnectorUsedInGitSyncConfig(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(),
        yamlGitConfigDTO.getGitConnectorRef(), yamlGitConfigDTO.getGitConnectorsRepo(),
        yamlGitConfigDTO.getGitConnectorsBranch());
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(yamlGitConfigDTO.getAccountIdentifier(),
        yamlGitConfigDTO.getOrganizationIdentifier(), yamlGitConfigDTO.getProjectIdentifier(), scmConnector);
    final ScmGitRefTaskParams scmGitRefTaskParams = ScmGitRefTaskParams.builder()
                                                        .gitRefType(GitRefType.FIND_COMMIT)
                                                        .scmConnector(scmConnector)
                                                        .encryptedDataDetails(encryptionDetails)
                                                        .ref(commitId)
                                                        .build();
    DelegateTaskRequest delegateTaskRequest =
        getDelegateTaskRequest(yamlGitConfigDTO.getAccountIdentifier(), yamlGitConfigDTO.getOrganizationIdentifier(),
            yamlGitConfigDTO.getProjectIdentifier(), scmGitRefTaskParams, TaskType.SCM_GIT_REF_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) delegateResponseData;
    try {
      return FindCommitResponse.parseFrom(scmGitRefTaskResponseData.getFindCommitResponse()).getCommit();
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation");
    }
  }

  @Override
  public CreateWebhookResponse upsertWebhook(
      UpsertWebhookRequestDTO upsertWebhookRequestDTO, String target, GitWebhookTaskType gitWebhookTaskType) {
    final ScmConnector scmConnector = getSCMConnectorUsedInGitSyncConfig(upsertWebhookRequestDTO.getAccountIdentifier(),
        upsertWebhookRequestDTO.getOrgIdentifier(), upsertWebhookRequestDTO.getProjectIdentifier(),
        upsertWebhookRequestDTO.getConnectorIdentifierRef(), null, null);
    if (!isEmpty(upsertWebhookRequestDTO.getRepoURL())) {
      scmConnector.setUrl(upsertWebhookRequestDTO.getRepoURL());
    }
    final List<EncryptedDataDetail> encryptionDetails =
        getEncryptedDataDetails(upsertWebhookRequestDTO.getAccountIdentifier(),
            upsertWebhookRequestDTO.getOrgIdentifier(), upsertWebhookRequestDTO.getProjectIdentifier(), scmConnector);
    final WebhookSecretData webhookSecretData = getWebhookSecretData(upsertWebhookRequestDTO);
    final ScmGitWebhookTaskParams gitWebhookTaskParams =
        ScmGitWebhookTaskParams.builder()
            .gitWebhookTaskType(gitWebhookTaskType)
            .scmConnector(scmConnector)
            .encryptedDataDetails(encryptionDetails)
            .gitWebhookDetails(GitWebhookDetails.builder()
                                   .hookEventType(upsertWebhookRequestDTO.getHookEventType())
                                   .target(target)
                                   .build())
            .webhookSecretData(webhookSecretData)
            .build();
    DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(upsertWebhookRequestDTO.getAccountIdentifier(),
        upsertWebhookRequestDTO.getOrgIdentifier(), upsertWebhookRequestDTO.getProjectIdentifier(),
        gitWebhookTaskParams, TaskType.SCM_GIT_WEBHOOK_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmGitWebhookTaskResponseData scmGitWebhookTaskResponseData = (ScmGitWebhookTaskResponseData) delegateResponseData;
    try {
      return CreateWebhookResponse.parseFrom(scmGitWebhookTaskResponseData.getCreateWebhookResponse());
    } catch (InvalidProtocolBufferException e) {
      throw new UnexpectedException("Unexpected error occurred while doing scm operation", e);
    }
  }

  @Override
  public CreateBranchResponse createBranch(InfoForGitPush infoForGitPush, String yamlGitConfigIdentifier) {
    YamlGitConfigDTO yamlGitConfigDTO = getYamlGitConfigDTO(infoForGitPush.getAccountId(),
        infoForGitPush.getOrgIdentifier(), infoForGitPush.getProjectIdentifier(), yamlGitConfigIdentifier);
    final ScmConnector scmConnector = getSCMConnectorUsedInGitSyncConfig(infoForGitPush.getAccountId(),
        infoForGitPush.getOrgIdentifier(), infoForGitPush.getProjectIdentifier(), yamlGitConfigDTO.getGitConnectorRef(),
        yamlGitConfigDTO.getGitConnectorsRepo(), yamlGitConfigDTO.getGitConnectorsBranch());
    scmConnector.setUrl(yamlGitConfigDTO.getRepo());
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetails(infoForGitPush.getAccountId(),
        infoForGitPush.getOrgIdentifier(), infoForGitPush.getProjectIdentifier(), scmConnector);
    final ScmGitRefTaskParams scmGitRefTaskParams = ScmGitRefTaskParams.builder()
                                                        .gitRefType(GitRefType.CREATE_BRANCH)
                                                        .scmConnector(scmConnector)
                                                        .encryptedDataDetails(encryptionDetails)
                                                        .branch(infoForGitPush.getBranch())
                                                        .baseBranch(infoForGitPush.getBaseBranch())
                                                        .build();
    DelegateTaskRequest delegateTaskRequest =
        getDelegateTaskRequest(infoForGitPush.getAccountId(), infoForGitPush.getOrgIdentifier(),
            infoForGitPush.getProjectIdentifier(), scmGitRefTaskParams, TaskType.SCM_GIT_REF_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) delegateResponseData;
    try {
      return CreateBranchResponse.parseFrom(scmGitRefTaskResponseData.getCreateBranchResponse());
    } catch (InvalidProtocolBufferException e) {
      String errorMsg = String.format(errorFormat, "create branch", infoForGitPush.getAccountId(),
          infoForGitPush.getOrgIdentifier(), infoForGitPush.getProjectIdentifier());
      log.error(errorMsg, e);
      throw new UnexpectedException(errorMsg);
    }
  }

  @Override
  public GetUserReposResponse listUserRepos(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ScmConnector scmConnector, PageRequestDTO pageRequest) {
    final List<EncryptedDataDetail> encryptionDetails =
        getEncryptedDataDetailsForNewGitX(accountIdentifier, orgIdentifier, projectIdentifier, scmConnector);
    final ScmGitRefTaskParams scmGitRefTaskParams = ScmGitRefTaskParams.builder()
                                                        .gitRefType(GitRefType.REPOSITORY_LIST)
                                                        .scmConnector(scmConnector)
                                                        .encryptedDataDetails(encryptionDetails)
                                                        .pageRequest(pageRequest)
                                                        .build();
    DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(
        accountIdentifier, orgIdentifier, projectIdentifier, scmGitRefTaskParams, TaskType.SCM_GIT_REF_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) delegateResponseData;
    try {
      return GetUserReposResponse.parseFrom(scmGitRefTaskResponseData.getGetUserReposResponse());
    } catch (InvalidProtocolBufferException e) {
      String errorMsg =
          String.format(errorFormat, "listing repos", accountIdentifier, orgIdentifier, projectIdentifier);
      log.error(errorMsg, e);
      throw new UnexpectedException(errorMsg);
    }
  }

  @Override
  public ListBranchesWithDefaultResponse listBranches(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScmConnector scmConnector, PageRequestDTO pageRequest) {
    final List<EncryptedDataDetail> encryptionDetails =
        getEncryptedDataDetailsForNewGitX(accountIdentifier, orgIdentifier, projectIdentifier, scmConnector);
    final ScmGitRefTaskParams scmGitRefTaskParams = ScmGitRefTaskParams.builder()
                                                        .gitRefType(GitRefType.BRANCH_LIST_WITH_DEFAULT_BRANCH)
                                                        .scmConnector(scmConnector)
                                                        .encryptedDataDetails(encryptionDetails)
                                                        .pageRequest(pageRequest)
                                                        .build();
    DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(
        accountIdentifier, orgIdentifier, projectIdentifier, scmGitRefTaskParams, TaskType.SCM_GIT_REF_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) delegateResponseData;
    try {
      return ListBranchesWithDefaultResponse.parseFrom(
          scmGitRefTaskResponseData.getGetListBranchesWithDefaultResponse());
    } catch (InvalidProtocolBufferException e) {
      String errorMsg =
          String.format(errorFormat, "listing branches", accountIdentifier, orgIdentifier, projectIdentifier);
      log.error(errorMsg, e);
      throw new UnexpectedException(errorMsg);
    }
  }

  @Override
  public GetUserRepoResponse getRepoDetails(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, ScmConnector scmConnector) {
    final List<EncryptedDataDetail> encryptionDetails =
        getEncryptedDataDetailsForNewGitX(accountIdentifier, orgIdentifier, projectIdentifier, scmConnector);
    final ScmGitRefTaskParams scmGitRefTaskParams = ScmGitRefTaskParams.builder()
                                                        .gitRefType(GitRefType.REPOSITORY_DETAILS)
                                                        .scmConnector(scmConnector)
                                                        .encryptedDataDetails(encryptionDetails)
                                                        .build();
    DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(
        accountIdentifier, orgIdentifier, projectIdentifier, scmGitRefTaskParams, TaskType.SCM_GIT_REF_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) delegateResponseData;
    try {
      return GetUserRepoResponse.parseFrom(scmGitRefTaskResponseData.getGetUserRepoResponse());
    } catch (InvalidProtocolBufferException e) {
      String errorMsg =
          String.format(errorFormat, "getting repo details", accountIdentifier, orgIdentifier, projectIdentifier);
      log.error(errorMsg, e);
      throw new UnexpectedException(errorMsg);
    }
  }

  @Override
  public CreateBranchResponse createNewBranch(
      Scope scope, ScmConnector scmConnector, String newBranchName, String baseBranchName) {
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetailsForNewGitX(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmConnector);
    final ScmGitRefTaskParams scmGitRefTaskParams = ScmGitRefTaskParams.builder()
                                                        .gitRefType(GitRefType.CREATE_BRANCH_V2)
                                                        .scmConnector(scmConnector)
                                                        .encryptedDataDetails(encryptionDetails)
                                                        .branch(newBranchName)
                                                        .baseBranch(baseBranchName)
                                                        .build();
    DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmGitRefTaskParams, TaskType.SCM_GIT_REF_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) delegateResponseData;
    try {
      return CreateBranchResponse.parseFrom(scmGitRefTaskResponseData.getCreateBranchResponse());
    } catch (InvalidProtocolBufferException e) {
      String errorMsg = String.format(errorFormat, "create branch", scope.getAccountIdentifier(),
          scope.getOrgIdentifier(), scope.getProjectIdentifier());
      log.error(errorMsg, e);
      throw new UnexpectedException(errorMsg);
    }
  }

  @Override
  public CreateFileResponse createFile(CreateGitFileRequestDTO createGitFileRequestDTO) {
    GitFileDetails gitFileDetails = getGitFileDetails(createGitFileRequestDTO);
    Scope scope = createGitFileRequestDTO.getScope();
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetailsForNewGitX(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), createGitFileRequestDTO.getScmConnector());
    ScmPushTaskParams scmPushTaskParams = ScmPushTaskParams.builder()
                                              .useGitClient(createGitFileRequestDTO.isUseGitClient())
                                              .changeType(ChangeType.ADD_V2)
                                              .scmConnector(createGitFileRequestDTO.getScmConnector())
                                              .gitFileDetails(gitFileDetails)
                                              .encryptedDataDetails(encryptionDetails)
                                              .build();

    final DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmPushTaskParams, TaskType.SCM_PUSH_TASK);

    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmPushTaskResponseData scmPushTaskResponseData = (ScmPushTaskResponseData) delegateResponseData;
    try {
      return CreateFileResponse.parseFrom(scmPushTaskResponseData.getCreateFileResponse());
    } catch (InvalidProtocolBufferException e) {
      String errorMsg = String.format(errorFormat, "create File", scope.getAccountIdentifier(),
          scope.getOrgIdentifier(), scope.getProjectIdentifier());
      log.error(errorMsg, e);
      throw new UnexpectedException(errorMsg);
    }
  }

  @Override
  public UpdateFileResponse updateFile(UpdateGitFileRequestDTO updateGitFileRequestDTO) {
    GitFileDetails gitFileDetails = getGitFileDetails(updateGitFileRequestDTO);
    Scope scope = updateGitFileRequestDTO.getScope();
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetailsForNewGitX(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), updateGitFileRequestDTO.getScmConnector());
    ScmPushTaskParams scmPushTaskParams = ScmPushTaskParams.builder()
                                              .useGitClient(updateGitFileRequestDTO.isUseGitClient())
                                              .changeType(ChangeType.UPDATE_V2)
                                              .scmConnector(updateGitFileRequestDTO.getScmConnector())
                                              .gitFileDetails(gitFileDetails)
                                              .encryptedDataDetails(encryptionDetails)
                                              .build();

    final DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmPushTaskParams, TaskType.SCM_PUSH_TASK);

    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmPushTaskResponseData scmPushTaskResponseData = (ScmPushTaskResponseData) delegateResponseData;
    try {
      return UpdateFileResponse.parseFrom(scmPushTaskResponseData.getUpdateFileResponse());
    } catch (InvalidProtocolBufferException e) {
      String errorMsg = String.format(errorFormat, "update File", scope.getAccountIdentifier(),
          scope.getOrgIdentifier(), scope.getProjectIdentifier());
      log.error(errorMsg, e);
      throw new UnexpectedException(errorMsg);
    }
  }

  @Override
  public GetLatestCommitOnFileResponse getLatestCommitOnFile(
      GetLatestCommitOnFileRequestDTO getLatestCommitOnFileRequestDTO) {
    Scope scope = getLatestCommitOnFileRequestDTO.getScope();
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetailsForNewGitX(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), getLatestCommitOnFileRequestDTO.getScmConnector());
    ScmGitRefTaskParams taskParams = ScmGitRefTaskParams.builder()
                                         .gitRefType(GitRefType.GET_LATEST_COMMIT_ON_FILE)
                                         .scmConnector(getLatestCommitOnFileRequestDTO.getScmConnector())
                                         .encryptedDataDetails(encryptionDetails)
                                         .filePath(getLatestCommitOnFileRequestDTO.getFilePath())
                                         .branch(getLatestCommitOnFileRequestDTO.getBranchName())
                                         .build();

    final DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), taskParams, TaskType.SCM_GIT_REF_TASK);

    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmGitRefTaskResponseData taskResponseData = (ScmGitRefTaskResponseData) delegateResponseData;
    try {
      return GetLatestCommitOnFileResponse.parseFrom(taskResponseData.getGetLatestCommitOnFileResponse());
    } catch (InvalidProtocolBufferException e) {
      String errorMsg = String.format(errorFormat, "getLatestCommitOnFile", scope.getAccountIdentifier(),
          scope.getOrgIdentifier(), scope.getProjectIdentifier());
      log.error(errorMsg, e);
      throw new UnexpectedException(errorMsg);
    }
  }

  @Override
  public GitFileResponse getFile(Scope scope, ScmConnector scmConnector, GitFileRequest gitFileContentRequest) {
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetailsForNewGitX(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmConnector);
    final GitFilePathDetails gitFilePathDetails =
        getGitFilePathDetails(gitFileContentRequest.getFilepath(), gitFileContentRequest.getBranch(),
            gitFileContentRequest.getCommitId(), gitFileContentRequest.isGetOnlyFileContent());
    final ScmGitFileTaskParams scmGitFileTaskParams =
        getScmGitFileTaskParams(scmConnector, encryptionDetails, gitFilePathDetails, GitFileTaskType.GET_FILE,
            gitFileContentRequest.getCommitId(), gitFileContentRequest.getBranch(), null);
    DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmGitFileTaskParams, TaskType.SCM_GIT_FILE_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    GitFileTaskResponseData gitFileTaskResponseData = (GitFileTaskResponseData) delegateResponseData;
    return gitFileTaskResponseData.getGitFileResponse();
  }

  @Override
  public GetLatestCommitResponse getBranchHeadCommitDetails(Scope scope, ScmConnector scmConnector, String branch) {
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetailsForNewGitX(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmConnector);

    final ScmGitRefTaskParams scmGitRefTaskParams = ScmGitRefTaskParams.builder()
                                                        .gitRefType(GitRefType.LATEST_COMMIT_V2)
                                                        .scmConnector(scmConnector)
                                                        .encryptedDataDetails(encryptionDetails)
                                                        .branch(branch)
                                                        .build();

    DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmGitRefTaskParams, TaskType.SCM_GIT_REF_TASK);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);

    ScmGitRefTaskResponseData scmGitRefTaskResponseData = (ScmGitRefTaskResponseData) delegateResponseData;
    try {
      return GetLatestCommitResponse.parseFrom(scmGitRefTaskResponseData.getGetLatestCommitResponse());
    } catch (InvalidProtocolBufferException e) {
      String errorMsg = String.format(errorFormat, "show-branch", scope.getAccountIdentifier(),
          scope.getOrgIdentifier(), scope.getProjectIdentifier());
      log.error(errorMsg, e);
      throw new UnexpectedException(errorMsg);
    }
  }

  @Override
  public ListFilesInCommitResponse listFiles(Scope scope, ScmConnector scmConnector, ListFilesInCommitRequest request) {
    final List<EncryptedDataDetail> encryptionDetails = getEncryptedDataDetailsForNewGitX(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmConnector);

    final ScmGitFileTaskParams scmGitFileTaskParams =
        ScmGitFileTaskParams.builder()
            .gitFileTaskType(GitFileTaskType.GET_FILE_GIT_DETAILS_LIST_IN_COMMIT)
            .ref(request.getRef())
            .scmConnector(scmConnector)
            .encryptedDataDetails(encryptionDetails)
            .filePathsList(Collections.singletonList(request.getFileDirectoryPath()))
            .build();
    DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(scope.getAccountIdentifier(),
        scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmGitFileTaskParams, TaskType.SCM_GIT_FILE_TASK);

    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    GitFileTaskResponseData gitFileTaskResponseData = (GitFileTaskResponseData) delegateResponseData;
    return gitFileTaskResponseData.getListFilesInCommitResponse();
  }

  @Override
  public GitFileBatchResponse getFileBatch(GitFileBatchRequest gitFileBatchRequest) {
    ScmBatchGetFileTaskParams scmBatchGetFileTaskParams = getScmBatchGetFileTaskParams(gitFileBatchRequest);
    Scope eligibleDelegatesScope = getEligibleScopeOfDelegates(gitFileBatchRequest);
    DelegateTaskRequest delegateTaskRequest = getDelegateTaskRequest(eligibleDelegatesScope.getAccountIdentifier(),
        eligibleDelegatesScope.getOrgIdentifier(), eligibleDelegatesScope.getProjectIdentifier(),
        scmBatchGetFileTaskParams, TaskType.SCM_BATCH_GET_FILE_TASK, 5);
    final DelegateResponseData delegateResponseData = executeDelegateSyncTask(delegateTaskRequest);
    ScmBatchGetFileTaskResponseData scmBatchGetFileTaskResponseData =
        (ScmBatchGetFileTaskResponseData) delegateResponseData;
    return GitFileBatchResponse.builder()
        .getBatchFileRequestIdentifierGitFileResponseMap(
            scmBatchGetFileTaskResponseData.getGetBatchFileRequestIdentifierGitFileResponseMap())
        .build();
  }

  // ------------------------------- PRIVATE METHODS -------------------------------

  private List<EncryptedDataDetail> getEncryptedDataDetails(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, ScmConnector scmConnector) {
    final BaseNGAccess baseNGAccess = getBaseNGAccess(accountIdentifier, orgIdentifier, projectIdentifier);
    final DecryptableEntity apiAccessDecryptableEntity =
        GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(scmConnector);
    return secretManagerClientService.getEncryptionDetails(baseNGAccess, apiAccessDecryptableEntity);
  }

  private List<EncryptedDataDetail> getEncryptedDataDetailsForNewGitX(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, ScmConnector scmConnector) {
    final BaseNGAccess baseNGAccess = getBaseNGAccess(accountIdentifier, orgIdentifier, projectIdentifier);
    final DecryptableEntity apiAccessDecryptableEntity =
        GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(scmConnector);
    PrincipalContextData currentPrincipal = GlobalContextManager.get(PrincipalContextData.PRINCIPAL_CONTEXT);
    // setting service principal for connector encryption in case of Git Connector
    GitSyncUtils.setGitSyncServicePrincipal();
    List<EncryptedDataDetail> encryptedDataDetails =
        secretManagerClientService.getEncryptionDetails(baseNGAccess, apiAccessDecryptableEntity);
    // setting back current principal for all other operations
    GitSyncUtils.setCurrentPrincipalContext(currentPrincipal);
    return encryptedDataDetails;
  }

  private WebhookSecretData getWebhookSecretData(UpsertWebhookRequestDTO upsertWebhookRequestDTO) {
    WebhookSecretData webhookSecretData = null;
    String secretIdentifierRef = upsertWebhookRequestDTO.getWebhookSecretIdentifierRef();
    if (isNotEmpty(secretIdentifierRef)) {
      final BaseNGAccess baseNGAccess = getBaseNGAccess(upsertWebhookRequestDTO.getAccountIdentifier(),
          upsertWebhookRequestDTO.getOrgIdentifier(), upsertWebhookRequestDTO.getProjectIdentifier());
      SecretRefData secretRefData = SecretRefHelper.createSecretRef(secretIdentifierRef);
      WebhookEncryptedSecretDTO webhookEncryptedSecretDTO =
          WebhookEncryptedSecretDTO.builder().secretRef(secretRefData).build();
      List<EncryptedDataDetail> encryptedDataDetail =
          secretManagerClientService.getEncryptionDetails(baseNGAccess, webhookEncryptedSecretDTO);
      webhookSecretData = WebhookSecretData.builder()
                              .webhookEncryptedSecretDTO(webhookEncryptedSecretDTO)
                              .encryptedDataDetails(encryptedDataDetail)
                              .build();
    }
    return webhookSecretData;
  }

  private DelegateTaskRequest getDelegateTaskRequest(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, TaskParameters taskParameters, TaskType taskType) {
    final Map<String, String> ngTaskSetupAbstractionsWithOwner =
        getNGTaskSetupAbstractionsWithOwner(accountIdentifier, orgIdentifier, projectIdentifier);
    return DelegateTaskRequest.builder()
        .accountId(accountIdentifier)
        .taskSetupAbstractions(ngTaskSetupAbstractionsWithOwner)
        .taskParameters(taskParameters)
        .taskType(taskType.name())
        .executionTimeout(Duration.ofMinutes(2))
        .build();
  }

  private DelegateTaskRequest getDelegateTaskRequest(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, TaskParameters taskParameters, TaskType taskType, long timeoutInMinutes) {
    final Map<String, String> ngTaskSetupAbstractionsWithOwner =
        getNGTaskSetupAbstractionsWithOwner(accountIdentifier, orgIdentifier, projectIdentifier);
    return DelegateTaskRequest.builder()
        .accountId(accountIdentifier)
        .taskSetupAbstractions(ngTaskSetupAbstractionsWithOwner)
        .taskParameters(taskParameters)
        .taskType(taskType.name())
        .executionTimeout(Duration.ofMinutes(timeoutInMinutes))
        .build();
  }

  private BaseNGAccess getBaseNGAccess(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .build();
  }

  private ScmGitRefTaskParams getScmGitRefTaskParams(
      ScmConnector scmConnector, List<EncryptedDataDetail> encryptionDetails, GitRefType gitRefType) {
    return ScmGitRefTaskParams.builder()
        .scmConnector(scmConnector)
        .gitRefType(gitRefType)
        .encryptedDataDetails(encryptionDetails)
        .build();
  }

  private ScmGitFileTaskParams getScmGitFileTaskParams(ScmConnector scmConnector,
      List<EncryptedDataDetail> encryptionDetails, GitFilePathDetails gitFilePathDetails,
      GitFileTaskType gitFileTaskType, String ref, String branch, List<String> filePathLists) {
    return ScmGitFileTaskParams.builder()
        .gitFileTaskType(gitFileTaskType)
        .scmConnector(scmConnector)
        .gitFilePathDetails(gitFilePathDetails)
        .filePathsList(filePathLists)
        .encryptedDataDetails(encryptionDetails)
        .ref(ref)
        .branch(branch)
        .build();
  }

  private DelegateResponseData executeDelegateSyncTask(DelegateTaskRequest delegateTaskRequest) {
    final DelegateResponseData delegateResponseData;
    String delegateDownErrorMessage = "Delegates are not available for performing operation.";
    try {
      delegateResponseData = delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      log.error("Error occurred while executing delegate task.", ex);
      throw new HintException(String.format(HintException.DELEGATE_NOT_AVAILABLE_FOR_GIT_SYNC,
                                  DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(delegateDownErrorMessage, WingsException.USER));
    }

    if (delegateResponseData instanceof ErrorNotifyResponseData) {
      throw new HintException(String.format(HintException.DELEGATE_NOT_AVAILABLE_FOR_GIT_SYNC,
                                  DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
          new DelegateNotAvailableException(delegateDownErrorMessage, WingsException.USER));
    }
    return delegateResponseData;
  }

  // Group files per connector to optimize on payload and pass it as task params to delegate task
  @VisibleForTesting
  ScmBatchGetFileTaskParams getScmBatchGetFileTaskParams(GitFileBatchRequest gitFileBatchRequest) {
    Map<ConnectorDetails, Pair<ScmConnector, List<EncryptedDataDetail>>> connectorDetailsToEncryptedDataDetailsMapping =
        new HashMap<>();
    Map<ConnectorDetails, Map<GetBatchFileRequestIdentifier, GitFileLocationDetails>>
        connectorDetailsToFileLocationMapping = new HashMap<>();

    gitFileBatchRequest.getGetBatchFileRequestIdentifierGitFileRequestV2Map().forEach((identifier, request) -> {
      ConnectorDetails key =
          ConnectorDetails.builder().scope(request.getScope()).connectorRef(request.getConnectorRef()).build();
      populateScmConnectorEncryptionDetailsMap(
          key, connectorDetailsToEncryptedDataDetailsMapping, request.getScope(), request.getScmConnector());
      GitFileLocationDetails gitFileLocationDetails = getGitFileLocationDetails(request);
      populateGitFileLocationDetailsMap(key, connectorDetailsToFileLocationMapping, gitFileLocationDetails, identifier);
    });

    List<GetFileTaskParamsPerConnector> getFileTaskParamsPerConnectorList = prepareGetFileTaskParamsPerConnectorList(
        connectorDetailsToEncryptedDataDetailsMapping, connectorDetailsToFileLocationMapping);
    return ScmBatchGetFileTaskParams.builder()
        .getFileTaskParamsPerConnectorList(getFileTaskParamsPerConnectorList)
        .build();
  }

  private GitFileLocationDetails getGitFileLocationDetails(GitFileRequestV2 gitFileRequest) {
    return GitFileLocationDetails.builder()
        .branch(gitFileRequest.getBranch())
        .commitId(gitFileRequest.getCommitId())
        .filepath(gitFileRequest.getFilepath())
        .repo(gitFileRequest.getRepo())
        .getOnlyFileContent(gitFileRequest.isGetOnlyFileContent())
        .build();
  }

  private List<EncryptedDataDetail> populateScmConnectorEncryptionDetailsMap(ConnectorDetails key,
      Map<ConnectorDetails, Pair<ScmConnector, List<EncryptedDataDetail>>> encryptedDataDetailsMap, Scope scope,
      ScmConnector scmConnector) {
    Pair<ScmConnector, List<EncryptedDataDetail>> scmConnectorEncryptionDetails = encryptedDataDetailsMap.get(key);
    List<EncryptedDataDetail> encryptedDataDetails = null;
    if (scmConnectorEncryptionDetails != null) {
      encryptedDataDetails = scmConnectorEncryptionDetails.getValue();
    }
    if (encryptedDataDetails == null) {
      encryptedDataDetails = getEncryptedDataDetailsForNewGitX(
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmConnector);
      encryptedDataDetailsMap.put(key, Pair.of(scmConnector, encryptedDataDetails));
      return encryptedDataDetails;
    }
    return encryptedDataDetails;
  }

  private void populateGitFileLocationDetailsMap(ConnectorDetails key,
      Map<ConnectorDetails, Map<GetBatchFileRequestIdentifier, GitFileLocationDetails>>
          connectorDetailsToFileLocationMapping,
      GitFileLocationDetails gitFileLocationDetails, GetBatchFileRequestIdentifier requestIdentifier) {
    Map<GetBatchFileRequestIdentifier, GitFileLocationDetails> gitFileLocationDetailsMap =
        connectorDetailsToFileLocationMapping.get(key);
    if (gitFileLocationDetailsMap == null) {
      gitFileLocationDetailsMap = new HashMap<>();
    }
    gitFileLocationDetailsMap.put(requestIdentifier, gitFileLocationDetails);
    connectorDetailsToFileLocationMapping.put(key, gitFileLocationDetailsMap);
  }

  private List<GetFileTaskParamsPerConnector> prepareGetFileTaskParamsPerConnectorList(
      Map<ConnectorDetails, Pair<ScmConnector, List<EncryptedDataDetail>>>
          connectorDetailsToEncryptedDataDetailsMapping,
      Map<ConnectorDetails, Map<GetBatchFileRequestIdentifier, GitFileLocationDetails>>
          connectorDetailsToFileLocationMapping) {
    List<GetFileTaskParamsPerConnector> getFileTaskParamsPerConnectorList = new ArrayList<>();

    connectorDetailsToEncryptedDataDetailsMapping.forEach((connectorDetails, scmConnectorEncryptionDetails) -> {
      GetFileTaskParamsPerConnector getFileTaskParamsPerConnector =
          GetFileTaskParamsPerConnector.builder()
              .connectorDecryptionParams(ConnectorDecryptionParams.builder()
                                             .encryptedDataDetails(scmConnectorEncryptionDetails.getValue())
                                             .scmConnector(scmConnectorEncryptionDetails.getKey())
                                             .build())
              .gitFileLocationDetailsMap(connectorDetailsToFileLocationMapping.get(connectorDetails))
              .build();
      getFileTaskParamsPerConnectorList.add(getFileTaskParamsPerConnector);
    });
    return getFileTaskParamsPerConnectorList;
  }

  @VisibleForTesting
  // Decides what all scope of delegates are eligible to pick up this task
  Scope getEligibleScopeOfDelegates(GitFileBatchRequest gitFileBatchRequest) {
    String orgIdentifier = null, projectIdentifier = null;
    boolean isThereAnyTaskNotProjectScoped = false;
    for (var entry : gitFileBatchRequest.getGetBatchFileRequestIdentifierGitFileRequestV2Map().entrySet()) {
      String requestOrgIdentifier = entry.getValue().getScope().getOrgIdentifier();
      if (requestOrgIdentifier == null) {
        orgIdentifier = null;
        projectIdentifier = null;
        break;
      } else {
        orgIdentifier = requestOrgIdentifier;
      }
      String requestProjectIdentifier = entry.getValue().getScope().getProjectIdentifier();
      if (isThereAnyTaskNotProjectScoped == false) {
        if (requestProjectIdentifier == null) {
          isThereAnyTaskNotProjectScoped = true;
          projectIdentifier = null;
        } else {
          projectIdentifier = requestProjectIdentifier;
        }
      }
    }
    return Scope.builder()
        .projectIdentifier(projectIdentifier)
        .orgIdentifier(orgIdentifier)
        .accountIdentifier(gitFileBatchRequest.getAccountIdentifier())
        .build();
  }
}
