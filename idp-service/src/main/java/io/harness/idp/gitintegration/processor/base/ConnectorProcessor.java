/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.processor.base;

import static io.harness.connector.helper.GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity;
import static io.harness.connector.helper.GitApiAccessDecryptionHelper.hasApiAccess;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.idp.gitintegration.utils.GitIntegrationConstants.ACCOUNT_SCOPED;
import static io.harness.idp.gitintegration.utils.GitIntegrationConstants.HARNESS_ENTITIES_IMPORT_COMMIT_MESSAGE;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import static software.wings.beans.TaskType.NG_GIT_COMMAND;
import static software.wings.beans.TaskType.SCM_PUSH_TASK;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.gitsync.GitFileDetails;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorTaskParams;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitSSHAuthenticationDTO;
import io.harness.delegate.beans.git.GitCommandExecutionResponse;
import io.harness.delegate.beans.git.GitCommandParams;
import io.harness.delegate.beans.git.GitCommandType;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.scm.ScmPushTaskParams;
import io.harness.delegate.task.scm.ScmPushTaskResponseData;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.git.GitClientV2Impl;
import io.harness.git.UsernamePasswordAuthRequest;
import io.harness.git.model.ChangeType;
import io.harness.git.model.CommitAndPushRequest;
import io.harness.git.model.GitBaseRequest;
import io.harness.git.model.GitFileChange;
import io.harness.git.model.GitRepositoryType;
import io.harness.gitsync.CreateFileRequest;
import io.harness.gitsync.CreateFileResponse;
import io.harness.gitsync.HarnessToGitPushInfoServiceGrpc;
import io.harness.gitsync.common.beans.GitOperation;
import io.harness.gitsync.common.helper.GitSyncGrpcClientUtils;
import io.harness.gitsync.common.helper.GitSyncLogContextHelper;
import io.harness.gitsync.common.helper.ScopeIdentifierMapper;
import io.harness.gitsync.common.helper.UserPrincipalMapper;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.remote.client.NGRestUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.Principal;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.CatalogConnectorInfo;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.Ref;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public abstract class ConnectorProcessor {
  @Inject public ConnectorResourceClient connectorResourceClient;
  @Inject NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Inject @Named("PRIVILEGED") public SecretManagerClientService ngSecretService;
  @Inject SecretManagerClientService ngSecretServiceNonPrivileged;
  @Inject DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject ExceptionManager exceptionManager;
  @Inject public GitClientV2Impl gitClientV2;
  @Inject public HarnessToGitPushInfoServiceGrpc.HarnessToGitPushInfoServiceBlockingStub harnessToGitPushInfoService;

  public abstract String getInfraConnectorType(ConnectorInfoDTO connectorInfoDTO);

  public abstract Map<String, BackstageEnvVariable> getConnectorAndSecretsInfo(
      String accountIdentifier, ConnectorInfoDTO connectorInfoDTO);

  public abstract void performPushOperation(String accountIdentifier, CatalogConnectorInfo catalogConnectorInfo,
      String locationParentPath, List<String> filesToPush, boolean throughGrpc);

  public abstract GitConfigDTO getGitConfigFromConnectorConfig(ConnectorConfigDTO connectorConfig);

  public ConnectorInfoDTO getConnectorInfo(String accountIdentifier, String connectorIdentifier) {
    Optional<ConnectorDTO> connectorDTO =
        NGRestUtils.getResponse(connectorResourceClient.get(connectorIdentifier, accountIdentifier, null, null));
    if (connectorDTO.isEmpty()) {
      throw new InvalidRequestException(String.format(
          "Connector not found for identifier: [%s], accountIdentifier: [%s]", connectorIdentifier, accountIdentifier));
    }
    return connectorDTO.get().getConnectorInfo();
  }

  protected void performPushOperationInternal(String accountIdentifier, CatalogConnectorInfo catalogConnectorInfo,
      String locationParentPath, List<String> filesToPush, String username, String password,
      ConnectorConfigDTO connectorConfigDTO, boolean throughGrpc) {
    UserPrincipal userPrincipalFromContext = (UserPrincipal) SourcePrincipalContextBuilder.getSourcePrincipal();

    GitConfigDTO gitConfigDTO = getGitConfigFromConnectorConfig(connectorConfigDTO);

    if (throughGrpc) {
      performPushGitServiceGrpc(gitConfigDTO, connectorConfigDTO, accountIdentifier, catalogConnectorInfo,
          locationParentPath, filesToPush, username, password, userPrincipalFromContext);
    } else {
      CommitAndPushRequest commitAndPushRequest = getCommitAndPushRequest(accountIdentifier, catalogConnectorInfo,
          locationParentPath, filesToPush, username, password, userPrincipalFromContext.getEmail());
      performPushJGit(gitConfigDTO, connectorConfigDTO, accountIdentifier, commitAndPushRequest);
    }
  }

  private void performPushGitServiceGrpc(GitConfigDTO gitConfigDTO, ConnectorConfigDTO connectorConfigDTO,
      String accountIdentifier, CatalogConnectorInfo catalogConnectorInfo, String locationParentPath,
      List<String> filesToPush, String username, String password, UserPrincipal userPrincipalFromContext) {
    GitBaseRequest gitBaseRequest =
        GitBaseRequest.builder()
            .repoUrl(catalogConnectorInfo.getRepo())
            .authRequest(
                UsernamePasswordAuthRequest.builder().username(username).password(password.toCharArray()).build())
            .connectorId(catalogConnectorInfo.getConnector().getIdentifier())
            .accountId(accountIdentifier)
            .build();
    Map<String, Ref> remoteList = gitClientV2.listRemote(gitBaseRequest);
    boolean commitToNewBranch;
    String baseBranchName;
    if (remoteList.containsKey("refs/heads/" + catalogConnectorInfo.getBranch())) {
      commitToNewBranch = false;
      baseBranchName = catalogConnectorInfo.getBranch();
    } else {
      commitToNewBranch = true;
      baseBranchName = remoteList.get("HEAD").getTarget().getName();
    }

    Scope scope = Scope.of(accountIdentifier, null, null);
    String repoName =
        Objects.equals(catalogConnectorInfo.getRepo().substring(catalogConnectorInfo.getRepo().length() - 1), "/")
        ? catalogConnectorInfo.getRepo()
              .substring(0, catalogConnectorInfo.getRepo().length() - 1)
              .substring(catalogConnectorInfo.getRepo().lastIndexOf('/') + 1)
        : catalogConnectorInfo.getRepo().substring(catalogConnectorInfo.getRepo().lastIndexOf('/') + 1);

    try {
      for (String fileToPush : filesToPush) {
        String filePathInRepo = fileToPush.replace(locationParentPath + "/", "");
        Map<String, String> contextMap = new HashMap<>();
        contextMap = GitSyncLogContextHelper.setContextMap(scope, repoName, catalogConnectorInfo.getBranch(), "",
            filePathInRepo, GitOperation.CREATE_FILE, contextMap);
        Path filePath = Path.of(fileToPush);
        final CreateFileRequest createFileRequest =
            CreateFileRequest.newBuilder()
                .setRepoName(repoName)
                .setBranchName(catalogConnectorInfo.getBranch())
                .setFilePath(filePathInRepo)
                .setConnectorRef(ACCOUNT_SCOPED + catalogConnectorInfo.getConnector().getIdentifier())
                .setFileContent(Files.readString(filePath))
                .setIsCommitToNewBranch(commitToNewBranch)
                .setBaseBranchName(baseBranchName)
                .setCommitMessage(HARNESS_ENTITIES_IMPORT_COMMIT_MESSAGE)
                .setScopeIdentifiers(ScopeIdentifierMapper.getScopeIdentifiersFromScope(scope))
                .putAllContextMap(contextMap)
                .setPrincipal(Principal.newBuilder()
                                  .setUserPrincipal(UserPrincipalMapper.toProto(userPrincipalFromContext))
                                  .build())
                .build();
        if (gitConfigDTO.getExecuteOnDelegate()) {
          ScmPushTaskResponseData scmPushTaskResponseData = commitAndPushGitService(gitConfigDTO, connectorConfigDTO,
              accountIdentifier, commitToNewBranch, baseBranchName,
              GitFileDetails.builder()
                  .branch(catalogConnectorInfo.getBranch())
                  .commitMessage(HARNESS_ENTITIES_IMPORT_COMMIT_MESSAGE)
                  .fileContent(Files.readString(filePath))
                  .filePath(filePathInRepo)
                  .userEmail(userPrincipalFromContext.getEmail())
                  .commitId("")
                  .userName(userPrincipalFromContext.getUsername())
                  .build());
          try {
            CreateFileResponse createFileResponse =
                CreateFileResponse.parseFrom(scmPushTaskResponseData.getCreateFileResponse());
            verifyCreateFileResponse(createFileResponse);
          } catch (InvalidProtocolBufferException e) {
            String errorMsg =
                String.format("Unexpected error occurred while doing scm operation for %s for accountId [%s]",
                    "create File", scope.getAccountIdentifier());
            log.error(errorMsg, e);
            throw new UnexpectedException(errorMsg);
          }
        } else {
          final CreateFileResponse createFileResponse = GitSyncGrpcClientUtils.retryAndProcessException(
              harnessToGitPushInfoService::createFile, createFileRequest);
          verifyCreateFileResponse(createFileResponse);
        }
      }
    } catch (Exception ex) {
      log.error("Exception while pushing files to source in IDP catalog onboarding flow, ex = {}", ex.getMessage(), ex);
      throw new UnexpectedException("Error response while pushing files to source in IDP catalog onboarding flow");
    }
  }

  public ScmPushTaskResponseData commitAndPushGitService(GitConfigDTO gitConfigDTO,
      ConnectorConfigDTO connectorConfigDTO, String accountIdentifier, boolean commitToNewBranch, String baseBranchName,
      GitFileDetails gitFileDetails) {
    validateFieldsPresent(gitConfigDTO);
    return (ScmPushTaskResponseData) commitAndPushConnectorGitService(
        gitConfigDTO, connectorConfigDTO, accountIdentifier, commitToNewBranch, baseBranchName, gitFileDetails);
  }

  public DelegateResponseData commitAndPushConnectorGitService(GitConfigDTO gitConfigDTO,
      ConnectorConfigDTO connectorConfigDto, String accountIdentifier, boolean commitToNewBranch, String baseBranchName,
      GitFileDetails gitFileDetails) {
    TaskParameters taskParameters = getTaskParametersGitService(
        gitConfigDTO, connectorConfigDto, accountIdentifier, commitToNewBranch, baseBranchName, gitFileDetails);

    DelegateTaskRequest delegateTaskRequest =
        buildDelegateTask(taskParameters, connectorConfigDto, getTaskType("scm"), accountIdentifier);

    return executeDelegateSyncTask(delegateTaskRequest);
  }

  public TaskParameters getTaskParametersGitService(GitConfigDTO gitConfigDTO, ConnectorConfigDTO connectorConfigDto,
      String accountIdentifier, boolean commitToNewBranch, String baseBranchName, GitFileDetails gitFileDetails) {
    ScmConnector scmConnector = (ScmConnector) connectorConfigDto;
    List<EncryptedDataDetail> encryptedDataDetails =
        getEncryptedDataDetail(gitConfigDTO, scmConnector, accountIdentifier);

    return ScmPushTaskParams.builder()
        .changeType(ChangeType.ADD_V2)
        .scmConnector(scmConnector)
        .gitFileDetails(gitFileDetails)
        .encryptedDataDetails(encryptedDataDetails)
        .isNewBranch(commitToNewBranch)
        .baseBranch(baseBranchName)
        .build();
  }

  private List<EncryptedDataDetail> getEncryptedDataDetail(
      GitConfigDTO gitConfigDTO, ScmConnector scmConnector, String accountIdentifier) {
    NGAccess ngAccess = getNgAccess(accountIdentifier);

    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();

    List<EncryptedDataDetail> authenticationEncryptedDataDetails = getEncryptedDataDetails(gitConfigDTO, ngAccess);
    if (isNotEmpty(authenticationEncryptedDataDetails)) {
      encryptedDataDetails.addAll(authenticationEncryptedDataDetails);
    }

    if (hasApiAccess(scmConnector)) {
      List<EncryptedDataDetail> apiAccessEncryptedDataDetail = getApiAccessEncryptedDataDetail(scmConnector, ngAccess);
      if (isNotEmpty(apiAccessEncryptedDataDetail)) {
        encryptedDataDetails.addAll(apiAccessEncryptedDataDetail);
      }
    }

    return encryptedDataDetails;
  }

  private CommitAndPushRequest getCommitAndPushRequest(String accountIdentifier,
      CatalogConnectorInfo catalogConnectorInfo, String locationParentPath, List<String> filesToPush, String username,
      String password, String email) {
    List<GitFileChange> gitFileChanges = new ArrayList<>();
    filesToPush.forEach((String fileToPush) -> {
      GitFileChange gitFileChange;
      try {
        gitFileChange = GitFileChange.builder()
                            .filePath(fileToPush.replace(locationParentPath, ""))
                            .fileContent(Files.readString(Path.of(fileToPush)))
                            .changeType(ChangeType.ADD)
                            .accountId(accountIdentifier)
                            .build();
      } catch (IOException e) {
        log.error("Error while doing git add on files. Exception = {}", e.getMessage(), e);
        throw new UnexpectedException("Error in preparing git files for commit.");
      }
      gitFileChanges.add(gitFileChange);
    });
    log.info("Prepared git files for push");
    return CommitAndPushRequest.builder()
        .repoUrl(catalogConnectorInfo.getRepo())
        .branch(catalogConnectorInfo.getBranch())
        .unsureOrNonExistentBranch(true)
        .connectorId(catalogConnectorInfo.getConnector().getIdentifier())
        .accountId(accountIdentifier)
        .authRequest(UsernamePasswordAuthRequest.builder().username(username).password(password.toCharArray()).build())
        .repoType(GitRepositoryType.YAML)
        .gitFileChanges(gitFileChanges)
        .authorName(username)
        .authorEmail(email)
        .commitMessage(HARNESS_ENTITIES_IMPORT_COMMIT_MESSAGE)
        .build();
  }

  private void performPushJGit(GitConfigDTO gitConfigDTO, ConnectorConfigDTO connectorConfigDTO,
      String accountIdentifier, CommitAndPushRequest commitAndPushRequest) {
    if (gitConfigDTO.getExecuteOnDelegate()) {
      log.info("Connector is configured through delegate, push operation will be submitted as task to delegate");
      commitAndPush(gitConfigDTO, connectorConfigDTO, accountIdentifier, commitAndPushRequest);
      return;
    }
    log.info("Connector is configured through harness, push operation will be performed on the fly");
    gitClientV2.commitAndPush(commitAndPushRequest);
    log.info("Git commit and push done");
  }

  public GitCommandExecutionResponse commitAndPush(GitConfigDTO gitConfigDTO, ConnectorConfigDTO connectorConfigDTO,
      String accountIdentifier, CommitAndPushRequest commitAndPushRequest) {
    validateFieldsPresent(gitConfigDTO);
    return (GitCommandExecutionResponse) commitAndPushConnector(
        gitConfigDTO, connectorConfigDTO, accountIdentifier, commitAndPushRequest);
  }

  public void validateFieldsPresent(GitConfigDTO gitConfig) {
    switch (gitConfig.getGitAuthType()) {
      case HTTP:
        GitHTTPAuthenticationDTO gitAuthenticationDTO = (GitHTTPAuthenticationDTO) gitConfig.getGitAuth();
        validateRequiredFieldsPresent(
            gitAuthenticationDTO.getPasswordRef(), gitConfig.getUrl(), gitConfig.getGitConnectionType());
        break;
      case SSH:
        GitSSHAuthenticationDTO gitSSHAuthenticationDTO = (GitSSHAuthenticationDTO) gitConfig.getGitAuth();
        validateRequiredFieldsPresent(gitSSHAuthenticationDTO.getEncryptedSshKey());
        break;
      default:
        throw new UnknownEnumTypeException("Git Authentication Type", gitConfig.getGitAuthType().getDisplayName());
    }
  }

  private void validateRequiredFieldsPresent(Object... fields) {
    Lists.newArrayList(fields).forEach(field -> Objects.requireNonNull(field, "One of the required field is empty."));
  }

  public DelegateResponseData commitAndPushConnector(GitConfigDTO gitConfigDTO, ConnectorConfigDTO connectorConfigDto,
      String accountIdentifier, CommitAndPushRequest commitAndPushRequest) {
    TaskParameters taskParameters = getTaskParameters(
        gitConfigDTO, connectorConfigDto, accountIdentifier, GitCommandType.COMMIT_AND_PUSH, commitAndPushRequest);

    DelegateTaskRequest delegateTaskRequest =
        buildDelegateTask(taskParameters, connectorConfigDto, getTaskType("ngGit"), accountIdentifier);

    return executeDelegateSyncTask(delegateTaskRequest);
  }

  public TaskParameters getTaskParameters(GitConfigDTO gitConfigDTO, ConnectorConfigDTO connectorConfigDto,
      String accountIdentifier, GitCommandType gitCommandType, GitBaseRequest gitBaseRequest) {
    ScmConnector scmConnector = (ScmConnector) connectorConfigDto;
    List<EncryptedDataDetail> encryptedDataDetails =
        getEncryptedDataDetail(gitConfigDTO, scmConnector, accountIdentifier);

    return GitCommandParams.builder()
        .gitConfig(gitConfigDTO)
        .gitCommandRequest(gitBaseRequest)
        .scmConnector(scmConnector)
        .sshKeySpecDTO(null)
        .gitCommandType(gitCommandType)
        .encryptionDetails(encryptedDataDetails)
        .build();
  }

  private NGAccess getNgAccess(String accountIdentifier) {
    return BaseNGAccess.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(null)
        .projectIdentifier(null)
        .build();
  }

  public List<EncryptedDataDetail> getEncryptedDataDetails(GitConfigDTO gitConfig, NGAccess ngAccess) {
    if (Objects.requireNonNull(gitConfig.getGitAuthType()) == GitAuthType.HTTP) {
      return getEncryptionDetail(gitConfig.getGitAuth(), ngAccess);
    }
    throw new UnknownEnumTypeException("Git Authentication Type", gitConfig.getGitAuthType().getDisplayName());
  }

  private List<EncryptedDataDetail> getEncryptionDetail(DecryptableEntity decryptableEntity, NGAccess ngAccess) {
    return getEncryptionDetail(decryptableEntity, ngAccess.getAccountIdentifier());
  }

  private List<EncryptedDataDetail> getEncryptionDetail(DecryptableEntity decryptableEntity, String accountIdentifier) {
    if (decryptableEntity == null) {
      return null;
    }
    NGAccess basicNGAccessObject =
        BaseNGAccess.builder().accountIdentifier(accountIdentifier).orgIdentifier(null).projectIdentifier(null).build();
    if (ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_CONNECTOR_ENCRYPTION_PRIVILEGED_CALL)) {
      return ngSecretService.getEncryptionDetails(basicNGAccessObject, decryptableEntity);
    }
    return ngSecretServiceNonPrivileged.getEncryptionDetails(basicNGAccessObject, decryptableEntity);
  }

  private List<EncryptedDataDetail> getApiAccessEncryptedDataDetail(ScmConnector scmConnector, NGAccess ngAccess) {
    if (hasApiAccess(scmConnector)) {
      return getEncryptionDetail(getAPIAccessDecryptableEntity(scmConnector), ngAccess);
    }
    return emptyList();
  }

  private DelegateTaskRequest buildDelegateTask(
      TaskParameters taskParameters, ConnectorConfigDTO connectorConfig, String taskType, String accountIdentifier) {
    if (taskParameters instanceof ConnectorTaskParams && connectorConfig instanceof DelegateSelectable) {
      ((ConnectorTaskParams) taskParameters)
          .setDelegateSelectors(((DelegateSelectable) connectorConfig).getDelegateSelectors());
    }

    final Map<String, String> ngTaskSetupAbstractionsWithOwner =
        getNGTaskSetupAbstractionsWithOwner(accountIdentifier, null, null);

    return DelegateTaskRequest.builder()
        .accountId(accountIdentifier)
        .taskType(taskType)
        .taskParameters(taskParameters)
        .taskSetupAbstractions(ngTaskSetupAbstractionsWithOwner)
        .executionTimeout(Duration.ofMinutes(10))
        .forceExecute(true)
        .build();
  }

  public String getTaskType(String flow) {
    if (Objects.equals(flow, "ngGit")) {
      return NG_GIT_COMMAND.name();
    }
    if (Objects.equals(flow, "scm")) {
      return SCM_PUSH_TASK.name();
    }
    throw new InvalidRequestException("Invalid flow type " + flow + " for GitConnectorGitOperations");
  }

  private DelegateResponseData executeDelegateSyncTask(DelegateTaskRequest delegateTaskRequest) {
    DelegateResponseData responseData;
    try {
      responseData = delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }

    if (responseData instanceof ErrorNotifyResponseData) {
      ErrorNotifyResponseData errorNotifyResponseData = (ErrorNotifyResponseData) responseData;
      log.info("Error in git push task for connector : [{}] with failure types [{}]",
          errorNotifyResponseData.getErrorMessage(), errorNotifyResponseData.getFailureTypes());
      throw new UnexpectedException(errorNotifyResponseData.getErrorMessage());
    } else if (responseData instanceof RemoteMethodReturnValueData
        && (((RemoteMethodReturnValueData) responseData).getException() instanceof InvalidRequestException)) {
      String errorMessage = ((RemoteMethodReturnValueData) responseData).getException().getMessage();
      throw new UnexpectedException(errorMessage);
    }
    return responseData;
  }

  private void verifyCreateFileResponse(CreateFileResponse createFileResponse) {
    if (createFileResponse.getStatusCode() >= 300) {
      log.error("Error response from git sync grpc while pushing files to source in IDP catalog onboarding flow = {}",
          createFileResponse);
      throw new UnexpectedException("Error response while pushing files to source in IDP catalog onboarding flow");
    }
  }
}
