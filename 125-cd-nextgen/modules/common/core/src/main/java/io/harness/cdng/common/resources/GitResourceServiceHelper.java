/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.common.resources;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.CDS_GITHUB_APP_AUTHENTICATION;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notEmptyCheck;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.helper.GithubAppDTOToGithubAppSpecDTOMapper;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.task.git.GitAuthenticationDecryptionHelper;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAppDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;

@Singleton
@OwnedBy(CDP)
public class GitResourceServiceHelper {
  private final ConnectorService connectorService;
  private final GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  private final CDFeatureFlagHelper cdFeatureFlagHelper;
  private final SecretManagerClientService ngSecretService;

  @Inject
  public GitResourceServiceHelper(@Named(DEFAULT_CONNECTOR_SERVICE) ConnectorService connectorService,
      GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper, CDFeatureFlagHelper cdFeatureFlagHelper,
      SecretManagerClientService ngSecretService) {
    this.connectorService = connectorService;
    this.gitConfigAuthenticationInfoHelper = gitConfigAuthenticationInfoHelper;
    this.cdFeatureFlagHelper = cdFeatureFlagHelper;
    this.ngSecretService = ngSecretService;
  }

  public ConnectorInfoDTO getConnectorInfoDTO(String connectorId, NGAccess ngAccess) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        connectorId, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(identifierRef.getAccountIdentifier(),
        identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
    if (!connectorDTO.isPresent()) {
      throw new InvalidRequestException(format("Connector not found for identifier : [%s]", connectorId), USER);
    }
    return connectorDTO.get().getConnector();
  }

  public GitStoreDelegateConfig getGitStoreDelegateConfig(ConnectorInfoDTO connectorDTO, NGAccess ngAccess,
      FetchType fetchType, String branch, String commitId, String path, String repoName) {
    GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO((ScmConnector) connectorDTO.getConnectorConfig());
    if (gitConfigDTO.getGitConnectionType() == GitConnectionType.ACCOUNT) {
      String repoUrl = getGitRepoUrl(gitConfigDTO, repoName);
      gitConfigDTO.setUrl(repoUrl);
      gitConfigDTO.setGitConnectionType(GitConnectionType.REPO);
    }
    SSHKeySpecDTO sshKeySpecDTO = getSshKeySpecDTO(gitConfigDTO, ngAccess);
    List<EncryptedDataDetail> encryptedDataDetails =
        gitConfigAuthenticationInfoHelper.getEncryptedDataDetails(gitConfigDTO, sshKeySpecDTO, ngAccess);

    GitStoreDelegateConfig.GitStoreDelegateConfigBuilder gitStoreDelegateConfigBuilder =
        GitStoreDelegateConfig.builder()
            .gitConfigDTO(gitConfigDTO)
            .sshKeySpecDTO(sshKeySpecDTO)
            .encryptedDataDetails(encryptedDataDetails)
            .fetchType(fetchType)
            .branch(branch)
            .commitId(commitId)
            .path(path)
            .connectorName(connectorDTO.getName());

    boolean githubAppAuthentication =
        cdFeatureFlagHelper.isEnabled(ngAccess.getAccountIdentifier(), CDS_GITHUB_APP_AUTHENTICATION)
        && GitAuthenticationDecryptionHelper.isGitHubAppAuthentication(
            (ScmConnector) connectorDTO.getConnectorConfig());

    if (githubAppAuthentication) {
      GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorDTO.getConnectorConfig();
      gitStoreDelegateConfigBuilder.gitConfigDTO(githubConnectorDTO);
      gitStoreDelegateConfigBuilder.encryptedDataDetails(
          gitConfigAuthenticationInfoHelper.getGithubAppEncryptedDataDetail(githubConnectorDTO, ngAccess));
      gitStoreDelegateConfigBuilder.isGithubAppAuthentication(true);
    }

    return gitStoreDelegateConfigBuilder.build();
  }

  public SSHKeySpecDTO getSshKeySpecDTO(GitConfigDTO gitConfigDTO, NGAccess ngAccess) {
    return gitConfigAuthenticationInfoHelper.getSSHKey(
        gitConfigDTO, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
  }

  // This method needs to be here because if the repo was defined as an account repo, the repo url can be
  // https://github.com/orgname/ or https://github.com/orgname and both are validated as correct by the connector
  // validator Same thing for repo name and /nameoftherepo and nameoftherepo
  private String getGitRepoUrl(ScmConnector scmConnector, String repoName) {
    repoName = trimToEmpty(repoName);
    notEmptyCheck("Repo name cannot be empty for Account level git connector", repoName);
    String purgedRepoUrl = scmConnector.getUrl().replaceAll("/*$", "");
    String purgedRepoName = repoName.replaceAll("^/*", "");
    return purgedRepoUrl + "/" + purgedRepoName;
  }

  private GithubApiAccessDTO getGitAppAccessFromGithubAppAuth(GithubConnectorDTO githubConnectorDTO) {
    GithubAppDTO githubAppDTO =
        (GithubAppDTO) ((GithubHttpCredentialsDTO) githubConnectorDTO.getAuthentication().getCredentials())
            .getHttpCredentialsSpec();
    return GithubApiAccessDTO.builder()
        .type(GithubApiAccessType.GITHUB_APP)
        .spec(GithubAppDTOToGithubAppSpecDTOMapper.toGitHubSpec(githubAppDTO))
        .build();
  }
}