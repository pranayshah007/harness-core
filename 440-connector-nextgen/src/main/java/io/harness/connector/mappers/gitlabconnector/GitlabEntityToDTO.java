/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.gitlabconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabAuthentication;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabConnector;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabHttpAuth;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabHttpAuthentication;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabKerberos;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabOauth;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabSshAuthentication;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabTokenApiAccess;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabUsernamePassword;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabUsernameToken;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabKerberosDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabOauthDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernameTokenDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.govern.Switch;

@OwnedBy(HarnessTeam.DX)
public class GitlabEntityToDTO implements ConnectorEntityToDTOMapper<GitlabConnectorDTO, GitlabConnector> {
  @Override
  public GitlabConnectorDTO createConnectorDTO(GitlabConnector connector) {
    GitlabAuthenticationDTO gitlabAuthenticationDTO =
        buildGitlabAuthentication(connector.getAuthType(), connector.getAuthenticationDetails());
    GitlabApiAccessDTO gitlabApiAccess = null;
    if (connector.isHasApiAccess()) {
      gitlabApiAccess = buildApiAccess(connector);
    }
    return GitlabConnectorDTO.builder()
        .apiAccess(gitlabApiAccess)
        .connectionType(connector.getConnectionType())
        .authentication(gitlabAuthenticationDTO)
        .url(connector.getUrl())
        .validationRepo(connector.getValidationRepo())
        .build();
  }

  public static GitlabAuthenticationDTO buildGitlabAuthentication(
      GitAuthType authType, GitlabAuthentication authenticationDetails) {
    GitlabCredentialsDTO gitlabCredentialsDTO = null;
    switch (authType) {
      case SSH:
        final GitlabSshAuthentication gitlabSshAuthentication = (GitlabSshAuthentication) authenticationDetails;
        gitlabCredentialsDTO = GitlabSshCredentialsDTO.builder()
                                   .sshKeyRef(SecretRefHelper.createSecretRef(gitlabSshAuthentication.getSshKeyRef()))
                                   .build();
        break;
      case HTTP:
        final GitlabHttpAuthentication gitlabHttpAuthentication = (GitlabHttpAuthentication) authenticationDetails;
        final GitlabHttpAuthenticationType type = gitlabHttpAuthentication.getType();
        final GitlabHttpAuth auth = gitlabHttpAuthentication.getAuth();
        GitlabHttpCredentialsSpecDTO gitlabHttpCredentialsSpecDTO = getHttpCredentialsSpecDTO(type, auth);
        gitlabCredentialsDTO =
            GitlabHttpCredentialsDTO.builder().type(type).httpCredentialsSpec(gitlabHttpCredentialsSpecDTO).build();
        break;
      default:
        Switch.unhandled(authType);
    }
    return GitlabAuthenticationDTO.builder().authType(authType).credentials(gitlabCredentialsDTO).build();
  }

  private static GitlabHttpCredentialsSpecDTO getHttpCredentialsSpecDTO(
      GitlabHttpAuthenticationType type, Object auth) {
    GitlabHttpCredentialsSpecDTO gitlabHttpCredentialsSpecDTO = null;
    switch (type) {
      case USERNAME_AND_TOKEN:
        final GitlabUsernameToken usernameToken = (GitlabUsernameToken) auth;
        SecretRefData usernameReference = null;
        if (usernameToken.getUsernameRef() != null) {
          usernameReference = SecretRefHelper.createSecretRef(usernameToken.getUsernameRef());
        }
        gitlabHttpCredentialsSpecDTO = GitlabUsernameTokenDTO.builder()
                                           .username(usernameToken.getUsername())
                                           .usernameRef(usernameReference)
                                           .tokenRef(SecretRefHelper.createSecretRef(usernameToken.getTokenRef()))
                                           .build();
        break;
      case USERNAME_AND_PASSWORD:
        final GitlabUsernamePassword gitlabUsernamePassword = (GitlabUsernamePassword) auth;
        SecretRefData usernameRef = null;
        if (gitlabUsernamePassword.getUsernameRef() != null) {
          usernameRef = SecretRefHelper.createSecretRef(gitlabUsernamePassword.getUsernameRef());
        }
        gitlabHttpCredentialsSpecDTO =
            GitlabUsernamePasswordDTO.builder()
                .passwordRef(SecretRefHelper.createSecretRef(gitlabUsernamePassword.getPasswordRef()))
                .username(gitlabUsernamePassword.getUsername())
                .usernameRef(usernameRef)
                .build();
        break;
      case KERBEROS:
        final GitlabKerberos gitlabKerberos = (GitlabKerberos) auth;
        gitlabHttpCredentialsSpecDTO =
            GitlabKerberosDTO.builder()
                .kerberosKeyRef(SecretRefHelper.createSecretRef(gitlabKerberos.getKerberosKeyRef()))
                .build();
        break;
      case OAUTH:
        final GitlabOauth gitlabOauth = (GitlabOauth) auth;
        gitlabHttpCredentialsSpecDTO =
            GitlabOauthDTO.builder()
                .tokenRef(SecretRefHelper.createSecretRef(gitlabOauth.getTokenRef()))
                .refreshTokenRef(SecretRefHelper.createSecretRef(gitlabOauth.getRefreshTokenRef()))
                .build();
        break;
      default:
        Switch.unhandled(type);
    }
    return gitlabHttpCredentialsSpecDTO;
  }

  private GitlabApiAccessDTO buildApiAccess(GitlabConnector connector) {
    switch (connector.getApiAccessType()) {
      case TOKEN:
        final GitlabTokenApiAccess gitlabTokenApiAccess = (GitlabTokenApiAccess) connector.getGitlabApiAccess();
        final GitlabTokenSpecDTO gitlabTokenSpecDTO =
            GitlabTokenSpecDTO.builder()
                .tokenRef(SecretRefHelper.createSecretRef(gitlabTokenApiAccess.getTokenRef()))
                .apiUrl(gitlabTokenApiAccess.getApiUrl())
                .build();
        return GitlabApiAccessDTO.builder().type(GitlabApiAccessType.TOKEN).spec(gitlabTokenSpecDTO).build();
      case OAUTH:
        final GitlabOauth gitlabOauth = (GitlabOauth) connector.getGitlabApiAccess();
        final GitlabOauthDTO gitlabOauthDTO =
            GitlabOauthDTO.builder()
                .tokenRef(SecretRefHelper.createSecretRef(gitlabOauth.getTokenRef()))
                .refreshTokenRef(SecretRefHelper.createSecretRef(gitlabOauth.getRefreshTokenRef()))
                .build();
        return GitlabApiAccessDTO.builder().type(GitlabApiAccessType.OAUTH).spec(gitlabOauthDTO).build();
      default:
        Switch.unhandled(connector.getApiAccessType());
        return null;
    }
  }
}
