/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.adapter;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabUsernameTokenDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(DX)
public class GitlabToGitMapper {
  public static GitConfigDTO mapToGitConfigDTO(GitlabConnectorDTO gitlabConnectorDTO) {
    final GitAuthType authType = gitlabConnectorDTO.getAuthentication().getAuthType();
    final GitConnectionType connectionType = gitlabConnectorDTO.getConnectionType();
    final String url = gitlabConnectorDTO.getUrl();
    final String validationRepo = gitlabConnectorDTO.getValidationRepo();
    if (authType == GitAuthType.HTTP) {
      final GitlabHttpCredentialsDTO gitlabHttpCredentialsDTO =
          (GitlabHttpCredentialsDTO) gitlabConnectorDTO.getAuthentication().getCredentials();
      if (gitlabHttpCredentialsDTO.getType() == GitlabHttpAuthenticationType.KERBEROS) {
        // todo(Deepak): please add when we add kerboros support in generic git.
        throw new InvalidRequestException(
            "Git connector doesn't have configuration for " + gitlabHttpCredentialsDTO.getType());
      }
      String username;
      SecretRefData usernameRef, passwordRef;
      if (gitlabHttpCredentialsDTO.getType() == GitlabHttpAuthenticationType.USERNAME_AND_PASSWORD) {
        final GitlabUsernamePasswordDTO httpCredentialsSpec =
            (GitlabUsernamePasswordDTO) gitlabHttpCredentialsDTO.getHttpCredentialsSpec();
        username = httpCredentialsSpec.getUsername();
        usernameRef = httpCredentialsSpec.getUsernameRef();
        passwordRef = httpCredentialsSpec.getPasswordRef();
      } else {
        final GitlabUsernameTokenDTO httpCredentialsSpec =
            (GitlabUsernameTokenDTO) gitlabHttpCredentialsDTO.getHttpCredentialsSpec();
        username = httpCredentialsSpec.getUsername();
        usernameRef = httpCredentialsSpec.getUsernameRef();
        passwordRef = httpCredentialsSpec.getTokenRef();
      }
      return GitConfigCreater.getGitConfigForHttp(connectionType, url, validationRepo, username, usernameRef,
          passwordRef, gitlabConnectorDTO.getDelegateSelectors());
    } else if (authType == GitAuthType.SSH) {
      final GitlabSshCredentialsDTO credentials =
          (GitlabSshCredentialsDTO) gitlabConnectorDTO.getAuthentication().getCredentials();
      final SecretRefData sshKeyRef = credentials.getSshKeyRef();
      return GitConfigCreater.getGitConfigForSsh(
          connectionType, url, validationRepo, sshKeyRef, gitlabConnectorDTO.getDelegateSelectors());
    }
    throw new InvalidRequestException("Unknown auth type: " + authType);
  }
}
