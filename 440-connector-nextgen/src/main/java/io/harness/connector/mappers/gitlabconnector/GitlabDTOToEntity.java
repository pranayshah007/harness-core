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
import io.harness.connector.entities.embedded.gitlabconnector.GitlabSshAuthentication;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabTokenApiAccess;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabUsernamePassword;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabUsernameToken;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabKerberosDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernameTokenDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.UnknownEnumTypeException;

@OwnedBy(HarnessTeam.DX)
public class GitlabDTOToEntity implements ConnectorDTOToEntityMapper<GitlabConnectorDTO, GitlabConnector> {
  @Override
  public GitlabConnector toConnectorEntity(GitlabConnectorDTO configDTO) {
    GitAuthType gitAuthType = getAuthType(configDTO.getAuthentication());
    GitlabAuthentication gitlabAuthentication =
        buildAuthenticationDetails(gitAuthType, configDTO.getAuthentication().getCredentials());
    boolean hasApiAccess = hasApiAccess(configDTO.getApiAccess());
    GitlabApiAccessType apiAccessType = null;
    GitlabTokenApiAccess gitlabApiAccess = null;
    if (hasApiAccess) {
      apiAccessType = getApiAccessType(configDTO.getApiAccess());
      gitlabApiAccess = getApiAcessByType(configDTO.getApiAccess().getSpec(), apiAccessType);
    }
    return GitlabConnector.builder()
        .connectionType(configDTO.getConnectionType())
        .authType(gitAuthType)
        .hasApiAccess(hasApiAccess)
        .authenticationDetails(gitlabAuthentication)
        .gitlabApiAccess(gitlabApiAccess)
        .url(configDTO.getUrl())
        .validationRepo(configDTO.getValidationRepo())
        .build();
  }

  public static GitlabAuthentication buildAuthenticationDetails(
      GitAuthType gitAuthType, GitlabCredentialsDTO credentialsDTO) {
    switch (gitAuthType) {
      case SSH:
        final GitlabSshCredentialsDTO sshCredentialsDTO = (GitlabSshCredentialsDTO) credentialsDTO;
        return GitlabSshAuthentication.builder()
            .sshKeyRef(SecretRefHelper.getSecretConfigString(sshCredentialsDTO.getSshKeyRef()))
            .build();
      case HTTP:
        final GitlabHttpCredentialsDTO httpCredentialsDTO = (GitlabHttpCredentialsDTO) credentialsDTO;
        final GitlabHttpAuthenticationType type = httpCredentialsDTO.getType();
        return GitlabHttpAuthentication.builder().type(type).auth(getHttpAuth(type, httpCredentialsDTO)).build();
      default:
        throw new UnknownEnumTypeException(
            "Gitlab Auth Type", gitAuthType == null ? null : gitAuthType.getDisplayName());
    }
  }

  private static GitlabHttpAuth getHttpAuth(
      GitlabHttpAuthenticationType type, GitlabHttpCredentialsDTO httpCredentialsDTO) {
    switch (type) {
      case USERNAME_AND_PASSWORD:
        final GitlabUsernamePasswordDTO usernamePasswordDTO =
            (GitlabUsernamePasswordDTO) httpCredentialsDTO.getHttpCredentialsSpec();
        String usernameRef = getStringSecretForNullableSecret(usernamePasswordDTO.getUsernameRef());
        return GitlabUsernamePassword.builder()
            .passwordRef(SecretRefHelper.getSecretConfigString(usernamePasswordDTO.getPasswordRef()))
            .username(usernamePasswordDTO.getUsername())
            .usernameRef(usernameRef)
            .build();
      case USERNAME_AND_TOKEN:
        final GitlabUsernameTokenDTO gitlabUsernameTokenDTO =
            (GitlabUsernameTokenDTO) httpCredentialsDTO.getHttpCredentialsSpec();
        String usernameReference = getStringSecretForNullableSecret(gitlabUsernameTokenDTO.getUsernameRef());
        return GitlabUsernameToken.builder()
            .tokenRef(SecretRefHelper.getSecretConfigString(gitlabUsernameTokenDTO.getTokenRef()))
            .username(gitlabUsernameTokenDTO.getUsername())
            .usernameRef(usernameReference)
            .build();
      case KERBEROS:
        final GitlabKerberosDTO gitlabKerberosDTO = (GitlabKerberosDTO) httpCredentialsDTO.getHttpCredentialsSpec();
        return GitlabKerberos.builder()
            .kerberosKeyRef(SecretRefHelper.getSecretConfigString(gitlabKerberosDTO.getKerberosKeyRef()))
            .build();
      default:
        throw new UnknownEnumTypeException("Gitlab Http Auth Type", type == null ? null : type.getDisplayName());
    }
  }
  private static String getStringSecretForNullableSecret(SecretRefData secretRefData) {
    String usernameRef = null;
    if (secretRefData != null) {
      usernameRef = SecretRefHelper.getSecretConfigString(secretRefData);
    }
    return usernameRef;
  }

  private GitlabTokenApiAccess getApiAcessByType(GitlabApiAccessSpecDTO spec, GitlabApiAccessType apiAccessType) {
    final GitlabTokenSpecDTO tokenSpec = (GitlabTokenSpecDTO) spec;
    return GitlabTokenApiAccess.builder()
        .tokenRef(SecretRefHelper.getSecretConfigString(tokenSpec.getTokenRef()))
        .build();
  }

  private GitlabApiAccessType getApiAccessType(GitlabApiAccessDTO apiAccess) {
    return apiAccess.getType();
  }

  private boolean hasApiAccess(GitlabApiAccessDTO apiAccess) {
    return apiAccess != null;
  }

  private GitAuthType getAuthType(GitlabAuthenticationDTO authentication) {
    return authentication.getAuthType();
  }
}
