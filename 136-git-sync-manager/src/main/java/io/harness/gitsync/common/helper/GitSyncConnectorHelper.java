/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.Scope;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.ManagerExecutable;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.gitsync.beans.GitRepositoryDTO;
import io.harness.gitsync.common.dtos.AzureRepoSCMDTO;
import io.harness.gitsync.common.dtos.GithubSCMDTO;
import io.harness.gitsync.common.dtos.GitlabSCMDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerDTO;
import io.harness.gitsync.common.dtos.gitAccess.GitAccessDTO;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.manage.GlobalContextManager;
import io.harness.security.PrincipalContextData;
import io.harness.tasks.DecryptGitApiAccessHelper;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Singleton
@OwnedBy(DX)
@Slf4j
public class GitSyncConnectorHelper {
  ConnectorService connectorService;
  DecryptGitApiAccessHelper decryptGitApiAccessHelper;
  YamlGitConfigService yamlGitConfigService;
  UserSourceCodeManagerHelper userSourceCodeManagerHelper;

  @Inject
  public GitSyncConnectorHelper(@Named("connectorDecoratorService") ConnectorService connectorService,
      DecryptGitApiAccessHelper decryptGitApiAccessHelper, YamlGitConfigService yamlGitConfigService,
      UserSourceCodeManagerHelper userSourceCodeManagerHelper) {
    this.connectorService = connectorService;
    this.decryptGitApiAccessHelper = decryptGitApiAccessHelper;
    this.yamlGitConfigService = yamlGitConfigService;
    this.userSourceCodeManagerHelper = userSourceCodeManagerHelper;
  }

  public ScmConnector getDecryptedConnector(
      String yamlGitConfigIdentifier, String projectIdentifier, String orgIdentifier, String accountId) {
    final YamlGitConfigDTO yamlGitConfigDTO =
        yamlGitConfigService.get(projectIdentifier, orgIdentifier, accountId, yamlGitConfigIdentifier);
    if (yamlGitConfigDTO == null) {
      throw new InvalidRequestException(String.format(
          "Git sync configuration not found for identifier: [%s], projectIdentifier: [%s], orgIdentifier: [%s]",
          projectIdentifier, orgIdentifier, yamlGitConfigIdentifier));
    }
    return getDecryptedConnector(yamlGitConfigDTO, accountId);
  }

  public ScmConnector getDecryptedConnector(YamlGitConfigDTO gitSyncConfigDTO, String accountId) {
    final String connectorRef = gitSyncConfigDTO.getGitConnectorRef();
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        connectorRef, accountId, gitSyncConfigDTO.getOrganizationIdentifier(), gitSyncConfigDTO.getProjectIdentifier());
    Optional<ConnectorResponseDTO> connectorDTO = getConnectorFromDefaultBranchElseFromGitBranch(accountId,
        identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier(),
        gitSyncConfigDTO.getGitConnectorsRepo(), gitSyncConfigDTO.getGitConnectorsBranch());
    if (connectorDTO.isPresent()) {
      ConnectorInfoDTO connector = connectorDTO.get().getConnector();
      ConnectorConfigDTO connectorConfig = connector.getConnectorConfig();
      if (connectorConfig instanceof ScmConnector) {
        ScmConnector gitConnectorConfig = (ScmConnector) connector.getConnectorConfig();
        final ScmConnector scmConnector = getDecryptedConnector(
            accountId, connector.getOrgIdentifier(), connector.getProjectIdentifier(), gitConnectorConfig);
        scmConnector.setUrl(gitSyncConfigDTO.getRepo());
        return scmConnector;
      } else {
        throw new UnexpectedException(
            String.format("The connector with the  id %s, accountId %s, orgId %s, projectId %s is not a scm connector",
                gitSyncConfigDTO.getIdentifier(), accountId, gitSyncConfigDTO.getOrganizationIdentifier(),
                gitSyncConfigDTO.getProjectIdentifier()));
      }
    } else {
      throw new UnexpectedException(String.format(
          "No connector found with the id %s, accountId %s, orgId %s, projectId %s", gitSyncConfigDTO.getIdentifier(),
          accountId, gitSyncConfigDTO.getOrganizationIdentifier(), gitSyncConfigDTO.getProjectIdentifier()));
    }
  }

  public ScmConnector getDecryptedConnector(
      String accountId, String orgIdentifier, String projectIdentifier, ScmConnector connectorDTO) {
    return decryptGitApiAccessHelper.decryptScmApiAccess(connectorDTO, accountId, projectIdentifier, orgIdentifier);
  }

  public ScmConnector getDecryptedConnectorForNewGitX(
      String accountId, String orgIdentifier, String projectIdentifier, ScmConnector connectorDTO) {
    PrincipalContextData currentPrincipal = GlobalContextManager.get(PrincipalContextData.PRINCIPAL_CONTEXT);
    // setting service principal for connector decryption in case of Git Connector
    GitSyncUtils.setGitSyncServicePrincipal();
    ScmConnector scmConnector =
        decryptGitApiAccessHelper.decryptScmApiAccess(connectorDTO, accountId, projectIdentifier, orgIdentifier);
    // setting back current principal for all other operations
    GitSyncUtils.setCurrentPrincipalContext(currentPrincipal);
    return scmConnector;
  }

  public ScmConnector getDecryptedConnector(
      YamlGitConfigDTO gitSyncConfigDTO, String accountId, ConnectorResponseDTO connectorDTO) {
    ConnectorInfoDTO connector = connectorDTO.getConnector();
    ConnectorConfigDTO connectorConfig = connector.getConnectorConfig();
    if (connectorConfig instanceof ScmConnector) {
      ScmConnector gitConnectorConfig = (ScmConnector) connector.getConnectorConfig();
      final ScmConnector scmConnector = decryptGitApiAccessHelper.decryptScmApiAccess(gitConnectorConfig, accountId,
          gitSyncConfigDTO.getProjectIdentifier(), gitSyncConfigDTO.getOrganizationIdentifier());
      scmConnector.setUrl(gitSyncConfigDTO.getRepo());
      return scmConnector;
    } else {
      throw new UnexpectedException(
          String.format("The connector with the  id %s, accountId %s, orgId %s, projectId %s is not a scm connector",
              gitSyncConfigDTO.getIdentifier(), accountId, gitSyncConfigDTO.getOrganizationIdentifier(),
              gitSyncConfigDTO.getProjectIdentifier()));
    }
  }

  public void getDecryptedGitAccessDTO(GitAccessDTO gitAccessDTO) {
    decryptGitApiAccessHelper.decryptGitAccessDTO(gitAccessDTO);
  }

  public void validateTheAPIAccessPresence(ScmConnector scmConnector) {
    if (scmConnector instanceof GithubConnectorDTO) {
      checkAPIAccessFieldPresence((GithubConnectorDTO) scmConnector);
    } else if (scmConnector instanceof GitlabConnectorDTO) {
      checkAPIAccessFieldPresence((GitlabConnectorDTO) scmConnector);
    } else if (scmConnector instanceof BitbucketConnectorDTO) {
      checkAPIAccessFieldPresence((BitbucketConnectorDTO) scmConnector);
    } else if (scmConnector instanceof AzureRepoConnectorDTO) {
      checkAPIAccessFieldPresence((AzureRepoConnectorDTO) scmConnector);
    } else {
      throw new NotImplementedException(
          String.format("The scm apis for the provider type %s is not supported", scmConnector.getClass()));
    }
  }

  private void checkAPIAccessFieldPresence(GithubConnectorDTO githubConnectorDTO) {
    GithubApiAccessDTO apiAccess = githubConnectorDTO.getApiAccess();
    if (apiAccess == null) {
      throw new InvalidRequestException(
          "The connector doesn't contain api access field which is required for the git sync ");
    }
  }

  private void checkAPIAccessFieldPresence(GitlabConnectorDTO gitlabConnectorDTO) {
    GitlabApiAccessDTO apiAccess = gitlabConnectorDTO.getApiAccess();
    if (apiAccess == null) {
      throw new InvalidRequestException(
          "The connector doesn't contain api access field which is required for the git sync ");
    }
  }

  private void checkAPIAccessFieldPresence(BitbucketConnectorDTO bitbucketConnectorDTO) {
    BitbucketApiAccessDTO apiAccess = bitbucketConnectorDTO.getApiAccess();
    if (apiAccess == null) {
      throw new InvalidRequestException(
          "The connector doesn't contain api access field which is required for the git sync ");
    }
  }

  private void checkAPIAccessFieldPresence(AzureRepoConnectorDTO azureRepoConnectorDTO) {
    AzureRepoApiAccessDTO apiAccess = azureRepoConnectorDTO.getApiAccess();
    if (apiAccess == null) {
      throw new InvalidRequestException(
          "The connector doesn't contain api access field which is required for the git sync ");
    }
  }

  public ScmConnector getScmConnector(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String connectorRef, String connectorRepo, String connectorBranch) {
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(connectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    final Optional<ConnectorResponseDTO> connectorResponseDTO = getConnectorFromDefaultBranchElseFromGitBranch(
        identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(),
        identifierRef.getIdentifier(), connectorRepo, connectorBranch);
    if (!connectorResponseDTO.isPresent()) {
      throw new InvalidRequestException(String.format("Ref Connector [{}] doesn't exist.", connectorRef));
    }
    return (ScmConnector) connectorResponseDTO.get().getConnector().getConnectorConfig();
  }

  public ScmConnector getDecryptedConnector(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef, String repoUrl) {
    Optional<YamlGitConfigDTO> yamlGitConfigDTO = yamlGitConfigService.getByProjectIdAndRepoOptional(
        accountIdentifier, orgIdentifier, projectIdentifier, repoUrl);
    String repo = null;
    String connectorBranch = null;

    if (yamlGitConfigDTO.isPresent()) {
      repo = yamlGitConfigDTO.get().getGitConnectorsRepo();
      connectorBranch = yamlGitConfigDTO.get().getGitConnectorsBranch();
    }

    try {
      ScmConnector connector =
          getScmConnector(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef, repo, connectorBranch);
      final ScmConnector scmConnector =
          decryptGitApiAccessHelper.decryptScmApiAccess(connector, accountIdentifier, projectIdentifier, orgIdentifier);
      scmConnector.setUrl(repoUrl);
      return scmConnector;
    } catch (Exception ex) {
      throw new UnexpectedException(
          String.format("The connector with the  id %s, accountId %s, orgId %s, projectId %s is not a scm connector",
              connectorRef, accountIdentifier, orgIdentifier, projectIdentifier));
    }
  }

  public Optional<ConnectorResponseDTO> getConnectorFromDefaultBranchElseFromGitBranch(String accountId,
      String orgIdentifier, String projectIdentifier, String identifier, String connectorRepo, String connectorBranch) {
    Optional<ConnectorResponseDTO> connectorResponseDTO = Optional.empty();
    GitEntityInfo oldGitEntityInfo = GitContextHelper.getGitEntityInfo();
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      final GitEntityInfo emptyInfo = GitEntityInfo.builder().build();
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(emptyInfo).build());
      connectorResponseDTO = connectorService.get(accountId, orgIdentifier, projectIdentifier, identifier);
    } finally {
      GlobalContextManager.upsertGlobalContextRecord(
          GitSyncBranchContext.builder().gitBranchInfo(oldGitEntityInfo).build());
    }
    if (connectorResponseDTO.isPresent()) {
      return connectorResponseDTO;
    }
    return getConnectorFromRepoBranch(
        accountId, orgIdentifier, projectIdentifier, identifier, connectorRepo, connectorBranch);
  }

  private Optional<ConnectorResponseDTO> getConnectorFromRepoBranch(String accountId, String orgIdentifier,
      String projectIdentifier, String identifier, String connectorRepo, String connectorBranch) {
    GitEntityInfo oldGitEntityInfo = GitContextHelper.getGitEntityInfo();
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      final GitEntityInfo repoBranchInfo =
          GitEntityInfo.builder().yamlGitConfigId(connectorRepo).branch(connectorBranch).build();
      GlobalContextManager.upsertGlobalContextRecord(
          GitSyncBranchContext.builder().gitBranchInfo(repoBranchInfo).build());
      return connectorService.get(accountId, orgIdentifier, projectIdentifier, identifier);
    } finally {
      GlobalContextManager.upsertGlobalContextRecord(
          GitSyncBranchContext.builder().gitBranchInfo(oldGitEntityInfo).build());
    }
  }

  // ----------------------- GIT-SIMPLIFICATION METHODS ---------------------------

  public ScmConnector getScmConnector(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef) {
    Optional<ConnectorResponseDTO> connectorDTO =
        connectorService.getByRef(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef);
    if (connectorDTO.isPresent()) {
      ConnectorInfoDTO connectorInfoDTO = connectorDTO.get().getConnector();
      ConnectorConfigDTO connectorConfigDTO = connectorInfoDTO.getConnectorConfig();
      if (connectorConfigDTO instanceof ScmConnector) {
        return (ScmConnector) connectorInfoDTO.getConnectorConfig();
      } else {
        throw new UnexpectedException(String.format(
            "The connector with the  identifier [%s], accountIdentifier [%s], orgIdentifier [%s], projectIdentifier [%s] is not an scm connector",
            connectorInfoDTO.getIdentifier(), accountIdentifier, orgIdentifier, projectIdentifier));
      }
    }
    throw new ConnectorNotFoundException(
        String.format(
            "No connector found for accountIdentifier: [%s], orgIdentifier : [%s], projectIdentifier : [%s], connectorRef : [%s]",
            accountIdentifier, orgIdentifier, projectIdentifier, connectorRef),
        USER);
  }

  public ScmConnector getDecryptedConnectorByRef(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef) {
    ScmConnector gitConnectorConfig =
        getScmConnector(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef);
    return getDecryptedConnectorForNewGitX(accountIdentifier, orgIdentifier, projectIdentifier, gitConnectorConfig);
  }

  public ScmConnector getScmConnectorForGivenRepo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef, String repoName) {
    ScmConnector scmConnector = getScmConnector(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef);
    scmConnector.setGitConnectionUrl(
        scmConnector.getGitConnectionUrl(GitRepositoryDTO.builder().name(repoName).build()));
    return scmConnector;
  }

  public ScmConnector getScmConnectorForGivenRepo(Scope scope, String connectorRef, String repoName) {
    ScmConnector scmConnector = getScmConnector(
        scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), connectorRef);
    scmConnector.setGitConnectionUrl(
        scmConnector.getGitConnectionUrl(GitRepositoryDTO.builder().name(repoName).build()));
    return scmConnector;
  }

  public boolean isScmConnectorManagerExecutable(ScmConnector scmConnector) {
    if (scmConnector instanceof ManagerExecutable) {
      final Boolean executeOnDelegate = ((ManagerExecutable) scmConnector).getExecuteOnDelegate();
      return executeOnDelegate == Boolean.FALSE;
    }
    return false;
  }

  public ScmConnector getDecryptedConnectorForGivenRepo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef, String repoName) {
    ScmConnector scmConnector =
        getDecryptedConnectorByRef(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef);
    scmConnector.setGitConnectionUrl(
        scmConnector.getGitConnectionUrl(GitRepositoryDTO.builder().name(repoName).build()));
    return scmConnector;
  }

  public void testConnectionAsync(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorRef) {
    CompletableFuture.runAsync(() -> {
      try {
        ConnectorValidationResult testConnectionResult =
            connectorService.testConnection(accountIdentifier, orgIdentifier, projectIdentifier, connectorRef);
        log.info(format("testConnectionResult for %s Connector: %s, project %s, org %s, account %s", connectorRef,
            testConnectionResult.getStatus(), projectIdentifier, orgIdentifier, accountIdentifier));
      } catch (Exception ex) {
        log.error(format("failed to test connection for %s Connector for project %s, org %s, account %s", connectorRef,
                      projectIdentifier, orgIdentifier, accountIdentifier),
            ex);
      }
    });
  }

  public void setUserGitCredsInConnectorIfPresent(String accountIdentifier, ScmConnector connectorDTO) {
    try {
      Optional<UserSourceCodeManagerDTO> userSourceCodeManagerDTO =
          userSourceCodeManagerHelper.fetchUserSourceCodeManagerDTO(accountIdentifier, connectorDTO);
      if (userSourceCodeManagerDTO.isPresent()) {
        switch (connectorDTO.getConnectorType()) {
          case GITHUB:
            GithubSCMDTO githubSCMDTO = (GithubSCMDTO) userSourceCodeManagerDTO.get();
            GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorDTO;
            githubConnectorDTO.setApiAccess(githubSCMDTO.getApiAccess());
            break;
          case GITLAB:
            GitlabSCMDTO gitlabSCMDTO = (GitlabSCMDTO) userSourceCodeManagerDTO.get();
            GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) connectorDTO;
            gitlabConnectorDTO.setApiAccess(gitlabSCMDTO.getApiAccess());
            break;
          case AZURE_REPO:
            AzureRepoSCMDTO azureRepoSCMDTO = (AzureRepoSCMDTO) userSourceCodeManagerDTO.get();
            AzureRepoConnectorDTO azureRepoConnectorDTO = (AzureRepoConnectorDTO) connectorDTO;
            azureRepoConnectorDTO.setApiAccess(azureRepoSCMDTO.getApiAccess());
            break;
          default:
            log.info("OAUTH not supported for connector type: {}", connectorDTO.getConnectorType());
        }
      }
    } catch (Exception ex) {
      log.error("Invalid type of connector: {}", connectorDTO.getConnectorType(), ex);
    }
  }
}
