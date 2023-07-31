/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.PR_CREATION_ERROR;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.BranchFilterParamsDTO;
import io.harness.beans.FileContentBatchResponse;
import io.harness.beans.GetBatchFileRequestIdentifier;
import io.harness.beans.IdentifierRef;
import io.harness.beans.PageRequestDTO;
import io.harness.beans.RepoFilterParamsDTO;
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
import io.harness.connector.impl.ConnectorErrorMessagesHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.delegate.task.scm.GitWebhookTaskType;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.ExplanationException;
import io.harness.exception.ScmException;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.beans.ConnectorDetails;
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
import io.harness.gitsync.common.helper.FileBatchResponseMapper;
import io.harness.gitsync.common.helper.GitSyncConnectorHelper;
import io.harness.gitsync.common.helper.PRFileListMapper;
import io.harness.gitsync.common.helper.UserProfileHelper;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.impl.ScmResponseStatusUtils;
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
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.product.ci.scm.proto.GetLatestCommitOnFileResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitResponse;
import io.harness.product.ci.scm.proto.GetUserRepoResponse;
import io.harness.product.ci.scm.proto.GetUserReposResponse;
import io.harness.product.ci.scm.proto.ListBranchesWithDefaultResponse;
import io.harness.product.ci.scm.proto.UpdateFileResponse;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.ScmClient;
import io.harness.tasks.DecryptGitApiAccessHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

// Don't inject this directly go through ScmClientOrchestrator.

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@Slf4j
@OwnedBy(DX)
public class ScmManagerFacilitatorServiceImpl extends AbstractScmClientFacilitatorServiceImpl {
  private ScmClient scmClient;
  private GitSyncConnectorHelper gitSyncConnectorHelper;
  private DecryptGitApiAccessHelper decryptGitApiAccessHelper;
  private SecretManagerClientService secretManagerClientService;

  @Inject
  public ScmManagerFacilitatorServiceImpl(ScmClient scmClient,
      @Named("connectorDecoratorService") ConnectorService connectorService,
      ConnectorErrorMessagesHelper connectorErrorMessagesHelper, YamlGitConfigService yamlGitConfigService,
      DecryptGitApiAccessHelper decryptGitApiAccessHelper, GitSyncConnectorHelper gitSyncConnectorHelper,
      UserProfileHelper userProfileHelper, SecretManagerClientService secretManagerClientService) {
    super(connectorService, connectorErrorMessagesHelper, yamlGitConfigService, userProfileHelper,
        gitSyncConnectorHelper);
    this.scmClient = scmClient;
    this.gitSyncConnectorHelper = gitSyncConnectorHelper;
    this.decryptGitApiAccessHelper = decryptGitApiAccessHelper;
    this.secretManagerClientService = secretManagerClientService;
  }

  @Override
  public List<String> listBranchesForRepoByConnector(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String connectorIdentifierRef, String repoURL,
      io.harness.ng.beans.PageRequest pageRequest, String searchTerm) {
    final IdentifierRef gitConnectorIdentifierRef =
        getConnectorIdentifierRef(accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifierRef);
    final ScmConnector connector = getScmConnector(gitConnectorIdentifierRef);
    ScmConnector decryptScmConnector =
        decryptGitApiAccessHelper.decryptScmApiAccess(connector, accountIdentifier, projectIdentifier, orgIdentifier);
    decryptScmConnector.setUrl(repoURL);
    return scmClient.listBranches(decryptScmConnector).getBranchesList();
  }

  @Override
  public GitFileContent getFileContent(String yamlGitConfigIdentifier, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String filePath, String branch, String commitId) {
    validateFileContentParams(branch, commitId);
    final ScmConnector decryptedConnector = gitSyncConnectorHelper.getDecryptedConnector(
        yamlGitConfigIdentifier, projectIdentifier, orgIdentifier, accountIdentifier);
    final GitFilePathDetails gitFilePathDetails = getGitFilePathDetails(filePath, branch, commitId, false);
    final FileContent fileContent = scmClient.getFileContent(decryptedConnector, gitFilePathDetails);
    return validateAndGetGitFileContent(fileContent);
  }

  @Override
  public FileContent getFile(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorRef, String repoName, String branchName, String filePath, String commitId) {
    ScmConnector connector = gitSyncConnectorHelper.getDecryptedConnectorForGivenRepo(
        accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, repoName);
    final GitFilePathDetails gitFilePathDetails = getGitFilePathDetails(filePath, branchName, commitId, false);
    return scmClient.getFileContent(connector, gitFilePathDetails);
  }

  @Override
  public CreatePRResponse createPullRequest(
      Scope scope, String connectorRef, String repoName, String sourceBranch, String targetBranch, String title) {
    ScmConnector decryptedConnector = gitSyncConnectorHelper.getDecryptedConnectorForGivenRepo(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), connectorRef, repoName);
    return scmClient.createPullRequestV2(decryptedConnector, sourceBranch, targetBranch, title);
  }

  @Override
  public CreatePRDTO createPullRequest(GitPRCreateRequest gitCreatePRRequest) {
    // since project level ref = ref
    YamlGitConfigDTO yamlGitConfigDTO =
        getYamlGitConfigDTO(gitCreatePRRequest.getAccountIdentifier(), gitCreatePRRequest.getOrgIdentifier(),
            gitCreatePRRequest.getProjectIdentifier(), gitCreatePRRequest.getYamlGitConfigRef());
    ConnectorResponseDTO connectorResponseDTO =
        getConnectorResponseDTO(yamlGitConfigDTO, gitCreatePRRequest.getAccountIdentifier());
    checkAndSetUserFromUserProfile(gitCreatePRRequest.isUseUserFromToken(), yamlGitConfigDTO, connectorResponseDTO);
    ScmConnector decryptScmConnector = gitSyncConnectorHelper.getDecryptedConnector(
        yamlGitConfigDTO, gitCreatePRRequest.getAccountIdentifier(), connectorResponseDTO);
    CreatePRResponse createPRResponse;
    try {
      createPRResponse = scmClient.createPullRequest(decryptScmConnector, gitCreatePRRequest);
      try {
        ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
            createPRResponse.getStatus(), createPRResponse.getError());
      } catch (WingsException e) {
        throw new ExplanationException(String.format("Could not create the pull request from %s to %s",
                                           gitCreatePRRequest.getSourceBranch(), gitCreatePRRequest.getTargetBranch()),
            e);
      }
    } catch (Exception ex) {
      throw new ScmException(PR_CREATION_ERROR);
    }
    return CreatePRDTO.builder().prNumber(createPRResponse.getNumber()).build();
  }

  @Override
  public List<GitFileChangeDTO> listFilesOfBranches(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String yamlGitConfigIdentifier, Set<String> foldersList, String branchName) {
    final ScmConnector decryptedConnector = gitSyncConnectorHelper.getDecryptedConnector(
        yamlGitConfigIdentifier, projectIdentifier, orgIdentifier, accountIdentifier);
    final FileContentBatchResponse fileContentBatchResponse =
        scmClient.listFiles(decryptedConnector, foldersList, branchName);
    return FileBatchResponseMapper.createGitFileChangeList(
        fileContentBatchResponse.getFileBatchContentResponse(), fileContentBatchResponse.getCommitId());
  }

  @Override
  public List<GitFileChangeDTO> listFilesByFilePaths(
      YamlGitConfigDTO yamlGitConfigDTO, List<String> filePaths, String branchName) {
    final ScmConnector decryptedConnector =
        gitSyncConnectorHelper.getDecryptedConnector(yamlGitConfigDTO, yamlGitConfigDTO.getAccountIdentifier());
    // todo @mohit: pick commit id from here.
    final FileContentBatchResponse fileContentBatchResponse =
        scmClient.listFilesByFilePaths(decryptedConnector, filePaths, branchName);
    return FileBatchResponseMapper.createGitFileChangeList(
        fileContentBatchResponse.getFileBatchContentResponse(), fileContentBatchResponse.getCommitId());
  }

  @Override
  public List<GitFileChangeDTO> listFilesByCommitId(
      YamlGitConfigDTO yamlGitConfigDTO, List<String> filePaths, String commitId) {
    final ScmConnector decryptedConnector =
        gitSyncConnectorHelper.getDecryptedConnector(yamlGitConfigDTO, yamlGitConfigDTO.getAccountIdentifier());
    // todo @mohit: pick commit id from here.
    final FileContentBatchResponse fileContentBatchResponse =
        scmClient.listFilesByCommitId(decryptedConnector, filePaths, commitId);
    return FileBatchResponseMapper.createGitFileChangeList(
        fileContentBatchResponse.getFileBatchContentResponse(), fileContentBatchResponse.getCommitId());
  }

  @Override
  public GitDiffResultFileListDTO listCommitsDiffFiles(
      YamlGitConfigDTO yamlGitConfigDTO, String initialCommitId, String finalCommitId) {
    final ScmConnector decryptedConnector =
        gitSyncConnectorHelper.getDecryptedConnector(yamlGitConfigDTO, yamlGitConfigDTO.getAccountIdentifier());
    CompareCommitsResponse compareCommitsResponse =
        scmClient.compareCommits(decryptedConnector, initialCommitId, finalCommitId);
    return PRFileListMapper.toGitDiffResultFileListDTO(compareCommitsResponse.getFilesList());
  }

  @Override
  public List<String> listCommits(YamlGitConfigDTO yamlGitConfigDTO, String branch) {
    final ScmConnector decryptedConnector =
        gitSyncConnectorHelper.getDecryptedConnector(yamlGitConfigDTO, yamlGitConfigDTO.getAccountIdentifier());
    return scmClient.listCommits(decryptedConnector, yamlGitConfigDTO.getBranch()).getCommitIdsList();
  }

  @Override
  public Commit getLatestCommit(YamlGitConfigDTO yamlGitConfigDTO, String branch) {
    final ScmConnector decryptedConnector =
        gitSyncConnectorHelper.getDecryptedConnector(yamlGitConfigDTO, yamlGitConfigDTO.getAccountIdentifier());
    final GetLatestCommitResponse latestCommit = scmClient.getLatestCommit(decryptedConnector, branch, null);
    ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(latestCommit.getStatus(), latestCommit.getError());
    return latestCommit.getCommit();
  }

  @Override
  public CreateFileResponse createFile(InfoForGitPush infoForPush) {
    ScmConnector decryptedConnector = gitSyncConnectorHelper.getDecryptedConnector(infoForPush.getAccountId(),
        infoForPush.getOrgIdentifier(), infoForPush.getProjectIdentifier(), infoForPush.getScmConnector());
    if (infoForPush.isNewBranch()) {
      createBranch(infoForPush.getBranch(), infoForPush.getBaseBranch(), decryptedConnector);
    }
    final GitFileDetailsBuilder gitFileDetails = getGitFileDetails(infoForPush.getAccountId(), infoForPush.getYaml(),
        infoForPush.getFilePath(), infoForPush.getFolderPath(), infoForPush.getCommitMsg(), infoForPush.getBranch(),
        SCMType.fromConnectorType(infoForPush.getScmConnector().getConnectorType()), infoForPush.getCommitId());
    return scmClient.createFile(decryptedConnector, gitFileDetails.build(), false);
  }

  @Override
  public UpdateFileResponse updateFile(InfoForGitPush infoForPush) {
    ScmConnector decryptedConnector = gitSyncConnectorHelper.getDecryptedConnector(infoForPush.getAccountId(),
        infoForPush.getOrgIdentifier(), infoForPush.getProjectIdentifier(), infoForPush.getScmConnector());
    if (infoForPush.isNewBranch()) {
      createBranch(infoForPush.getBranch(), infoForPush.getBaseBranch(), decryptedConnector);
    }
    final GitFileDetailsBuilder gitFileDetails = getGitFileDetails(infoForPush.getAccountId(), infoForPush.getYaml(),
        infoForPush.getFilePath(), infoForPush.getFolderPath(), infoForPush.getCommitMsg(), infoForPush.getBranch(),
        SCMType.fromConnectorType(infoForPush.getScmConnector().getConnectorType()), infoForPush.getCommitId());
    gitFileDetails.oldFileSha(infoForPush.getOldFileSha());
    return scmClient.updateFile(decryptedConnector, gitFileDetails.build(), false);
  }

  @Override
  public DeleteFileResponse deleteFile(InfoForGitPush infoForPush) {
    ScmConnector decryptedConnector = gitSyncConnectorHelper.getDecryptedConnector(infoForPush.getAccountId(),
        infoForPush.getOrgIdentifier(), infoForPush.getProjectIdentifier(), infoForPush.getScmConnector());
    if (infoForPush.isNewBranch()) {
      createBranch(infoForPush.getBranch(), infoForPush.getBaseBranch(), decryptedConnector);
    }
    final GitFileDetailsBuilder gitFileDetails = getGitFileDetails(infoForPush.getAccountId(), infoForPush.getYaml(),
        infoForPush.getFilePath(), infoForPush.getFolderPath(), infoForPush.getCommitMsg(), infoForPush.getBranch(),
        SCMType.fromConnectorType(infoForPush.getScmConnector().getConnectorType()), infoForPush.getCommitId());
    gitFileDetails.oldFileSha(infoForPush.getOldFileSha());
    return scmClient.deleteFile(decryptedConnector, gitFileDetails.build());
  }

  @Override
  public Commit findCommitById(YamlGitConfigDTO yamlGitConfigDTO, String commitId) {
    final ScmConnector decryptedConnector =
        gitSyncConnectorHelper.getDecryptedConnector(yamlGitConfigDTO, yamlGitConfigDTO.getAccountIdentifier());
    return scmClient.findCommit(decryptedConnector, commitId).getCommit();
  }

  @Override
  public CreateWebhookResponse upsertWebhook(
      UpsertWebhookRequestDTO upsertWebhookRequestDTO, String target, GitWebhookTaskType gitWebhookTaskType) {
    final ScmConnector decryptedConnector =
        gitSyncConnectorHelper.getDecryptedConnector(upsertWebhookRequestDTO.getAccountIdentifier(),
            upsertWebhookRequestDTO.getOrgIdentifier(), upsertWebhookRequestDTO.getProjectIdentifier(),
            upsertWebhookRequestDTO.getConnectorIdentifierRef(), upsertWebhookRequestDTO.getRepoURL());
    final String webhookSecret = getWebhookSecret(upsertWebhookRequestDTO);
    GitWebhookDetails gitWebhookDetails = GitWebhookDetails.builder()
                                              .hookEventType(upsertWebhookRequestDTO.getHookEventType())
                                              .target(target)
                                              .secret(webhookSecret)
                                              .build();

    if (gitWebhookTaskType.equals(GitWebhookTaskType.CREATE)) {
      return scmClient.createWebhook(decryptedConnector, gitWebhookDetails);
    } else {
      return scmClient.upsertWebhook(decryptedConnector, gitWebhookDetails);
    }
  }

  @Override
  public CreateBranchResponse createBranch(InfoForGitPush infoForGitPush, String yamlGitConfigIdentifier) {
    final ScmConnector decryptedConnector = gitSyncConnectorHelper.getDecryptedConnector(yamlGitConfigIdentifier,
        infoForGitPush.getProjectIdentifier(), infoForGitPush.getOrgIdentifier(), infoForGitPush.getAccountId());
    return createBranch(infoForGitPush.getBranch(), infoForGitPush.getBaseBranch(), decryptedConnector);
  }

  @Override
  public GetUserReposResponse listUserRepos(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      ScmConnector scmConnector, PageRequestDTO pageRequest, RepoFilterParamsDTO repoFilterParamsDTO) {
    ScmConnector decryptedConnector = gitSyncConnectorHelper.getDecryptedConnectorForNewGitX(
        accountIdentifier, orgIdentifier, projectIdentifier, scmConnector);
    return scmClient.getUserRepos(decryptedConnector, pageRequest, repoFilterParamsDTO);
  }

  @Override
  public ListBranchesWithDefaultResponse listBranches(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, ScmConnector scmConnector, PageRequestDTO pageRequest,
      BranchFilterParamsDTO branchFilterParamsDTO) {
    final ScmConnector decryptedConnector = gitSyncConnectorHelper.getDecryptedConnectorForNewGitX(
        accountIdentifier, orgIdentifier, projectIdentifier, scmConnector);
    return scmClient.listBranchesWithDefault(decryptedConnector, pageRequest, branchFilterParamsDTO);
  }

  private CreateBranchResponse createBranch(String branch, String baseBranch, ScmConnector scmConnector) {
    return scmClient.createNewBranch(scmConnector, branch, baseBranch);
  }

  @Override
  public GetUserRepoResponse getRepoDetails(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, ScmConnector scmConnector) {
    final ScmConnector decryptedConnector = gitSyncConnectorHelper.getDecryptedConnectorForNewGitX(
        accountIdentifier, orgIdentifier, projectIdentifier, scmConnector);
    return scmClient.getRepoDetails(decryptedConnector);
  }

  @Override
  public CreateBranchResponse createNewBranch(
      Scope scope, ScmConnector scmConnector, String newBranchName, String baseBranchName) {
    final ScmConnector decryptedConnector = gitSyncConnectorHelper.getDecryptedConnectorForNewGitX(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmConnector);
    return scmClient.createNewBranchV2(decryptedConnector, newBranchName, baseBranchName);
  }

  @Override
  public CreateFileResponse createFile(CreateGitFileRequestDTO createGitFileRequestDTO) {
    Scope scope = createGitFileRequestDTO.getScope();
    ScmConnector scmConnector = createGitFileRequestDTO.getScmConnector();
    gitSyncConnectorHelper.setUserGitCredsInConnectorIfPresent(scope.getAccountIdentifier(), scmConnector);
    final ScmConnector decryptedConnector = gitSyncConnectorHelper.getDecryptedConnectorForNewGitX(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmConnector);
    GitFileDetails gitFileDetails = getGitFileDetails(
        createGitFileRequestDTO, gitSyncConnectorHelper.getUserDetails(scope.getAccountIdentifier(), scmConnector));
    return scmClient.createFile(decryptedConnector, gitFileDetails, createGitFileRequestDTO.isUseGitClient());
  }

  @Override
  public UpdateFileResponse updateFile(UpdateGitFileRequestDTO updateGitFileRequestDTO) {
    Scope scope = updateGitFileRequestDTO.getScope();
    ScmConnector scmConnector = updateGitFileRequestDTO.getScmConnector();
    gitSyncConnectorHelper.setUserGitCredsInConnectorIfPresent(scope.getAccountIdentifier(), scmConnector);
    final ScmConnector decryptedConnector = gitSyncConnectorHelper.getDecryptedConnectorForNewGitX(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmConnector);
    GitFileDetails gitFileDetails = getGitFileDetails(
        updateGitFileRequestDTO, gitSyncConnectorHelper.getUserDetails(scope.getAccountIdentifier(), scmConnector));
    return scmClient.updateFile(decryptedConnector, gitFileDetails, updateGitFileRequestDTO.isUseGitClient());
  }

  @Override
  public GetLatestCommitOnFileResponse getLatestCommitOnFile(
      GetLatestCommitOnFileRequestDTO getLatestCommitOnFileRequestDTO) {
    Scope scope = getLatestCommitOnFileRequestDTO.getScope();
    final ScmConnector decryptedConnector =
        gitSyncConnectorHelper.getDecryptedConnectorForNewGitX(scope.getAccountIdentifier(), scope.getOrgIdentifier(),
            scope.getProjectIdentifier(), getLatestCommitOnFileRequestDTO.getScmConnector());
    return scmClient.getLatestCommitOnFile(decryptedConnector, getLatestCommitOnFileRequestDTO.getBranchName(),
        getLatestCommitOnFileRequestDTO.getFilePath());
  }

  @Override
  public GitFileResponse getFile(Scope scope, ScmConnector scmConnector, GitFileRequest gitFileRequest) {
    final ScmConnector decryptedConnector = gitSyncConnectorHelper.getDecryptedConnectorForNewGitX(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmConnector);
    return scmClient.getFile(decryptedConnector, gitFileRequest);
  }

  @Override
  public GetLatestCommitResponse getBranchHeadCommitDetails(Scope scope, ScmConnector scmConnector, String branch) {
    final ScmConnector decryptedConnector = gitSyncConnectorHelper.getDecryptedConnectorForNewGitX(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmConnector);
    return scmClient.getLatestCommit(decryptedConnector, branch, null);
  }

  @Override
  public UserDetailsResponseDTO getUserDetails(UserDetailsRequestDTO userDetailsRequestDTO) {
    gitSyncConnectorHelper.decryptGitAccessDTO(userDetailsRequestDTO.getGitAccessDTO());
    return scmClient.getUserDetails(userDetailsRequestDTO);
  }

  public ListFilesInCommitResponse listFiles(Scope scope, ScmConnector scmConnector, ListFilesInCommitRequest request) {
    final ScmConnector decryptedConnector = gitSyncConnectorHelper.getDecryptedConnectorForNewGitX(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmConnector);
    return scmClient.listFilesInCommit(decryptedConnector, request);
  }

  private String getWebhookSecret(UpsertWebhookRequestDTO upsertWebhookRequestDTO) {
    String webhookSecret = null;
    String secretIdentifierRef = upsertWebhookRequestDTO.getWebhookSecretIdentifierRef();
    if (isNotEmpty(secretIdentifierRef)) {
      final BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                            .accountIdentifier(upsertWebhookRequestDTO.getAccountIdentifier())
                                            .orgIdentifier(upsertWebhookRequestDTO.getOrgIdentifier())
                                            .projectIdentifier(upsertWebhookRequestDTO.getProjectIdentifier())
                                            .build();
      SecretRefData secretRefData = SecretRefHelper.createSecretRef(secretIdentifierRef);
      WebhookEncryptedSecretDTO webhookEncryptedSecretDTO =
          WebhookEncryptedSecretDTO.builder().secretRef(secretRefData).build();
      List<EncryptedDataDetail> encryptedDataDetail =
          secretManagerClientService.getEncryptionDetails(baseNGAccess, webhookEncryptedSecretDTO);
      WebhookSecretData webhookSecretData = WebhookSecretData.builder()
                                                .webhookEncryptedSecretDTO(webhookEncryptedSecretDTO)
                                                .encryptedDataDetails(encryptedDataDetail)
                                                .build();
      try {
        decryptGitApiAccessHelper.decryptEncryptionDetails(webhookSecretData.getWebhookEncryptedSecretDTO(),
            webhookSecretData.getEncryptedDataDetails(), upsertWebhookRequestDTO.getAccountIdentifier());
        webhookSecret =
            String.valueOf(webhookSecretData.getWebhookEncryptedSecretDTO().getSecretRef().getDecryptedValue());
      } catch (Exception ex) {
        throw new ExplanationException(
            String.format("Error finding webhook secret key in secret manager: %s", secretIdentifierRef), ex);
      }
    }
    return webhookSecret;
  }

  @Override
  public GitFileBatchResponse getFileBatch(GitFileBatchRequest gitFileBatchRequest) {
    Map<ConnectorDetails, ScmConnector> decryptedConnectorMap = new HashMap<>();
    Map<GetBatchFileRequestIdentifier, GitFileRequestV2> getBatchFileRequestIdentifierGitFileRequestV2Map =
        new HashMap<>();

    gitFileBatchRequest.getGetBatchFileRequestIdentifierGitFileRequestV2Map().forEach((identifier, request) -> {
      ScmConnector decryptedScmConnector = getDecryptedScmConnector(decryptedConnectorMap, request.getScope(),
          request.getConnectorRef(), request.getScmConnector(), request.getRepo());
      getBatchFileRequestIdentifierGitFileRequestV2Map.put(identifier,
          GitFileRequestV2.builder()
              .scope(request.getScope())
              .connectorRef(request.getConnectorRef())
              .scmConnector(decryptedScmConnector)
              .repo(request.getRepo())
              .filepath(request.getFilepath())
              .commitId(request.getCommitId())
              .branch(request.getBranch())
              .getOnlyFileContent(request.isGetOnlyFileContent())
              .build());
    });

    return scmClient.getBatchFile(
        GitFileBatchRequest.builder()
            .getBatchFileRequestIdentifierGitFileRequestV2Map(getBatchFileRequestIdentifierGitFileRequestV2Map)
            .build());
  }

  @VisibleForTesting
  ScmConnector getDecryptedScmConnector(Map<ConnectorDetails, ScmConnector> decryptedConnectorMap, Scope scope,
      String connectorRef, ScmConnector scmConnector, String repo) {
    ConnectorDetails key = ConnectorDetails.builder().scope(scope).connectorRef(connectorRef).repo(repo).build();
    ScmConnector decryptedScmConnector = decryptedConnectorMap.get(key);
    if (decryptedScmConnector == null) {
      decryptedScmConnector = gitSyncConnectorHelper.getDecryptedConnectorForNewGitX(
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), scmConnector);
      decryptedConnectorMap.put(key, decryptedScmConnector);
      return decryptedScmConnector;
    }
    return decryptedScmConnector;
  }
}
