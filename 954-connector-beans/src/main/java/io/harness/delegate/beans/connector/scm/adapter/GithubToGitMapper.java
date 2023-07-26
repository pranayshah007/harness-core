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
import io.harness.delegate.beans.connector.scm.github.GithubAppDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubOauthDTO;
import io.harness.delegate.beans.connector.scm.github.GithubSshCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(DX)
public class GithubToGitMapper {
  public static GitConfigDTO mapToGitConfigDTO(GithubConnectorDTO githubConnectorDTO) {
    final GitAuthType authType = githubConnectorDTO.getAuthentication().getAuthType();
    final GitConnectionType connectionType = githubConnectorDTO.getConnectionType();
    final String url = githubConnectorDTO.getUrl();
    final String validationRepo = githubConnectorDTO.getValidationRepo();
    if (authType == GitAuthType.HTTP) {
      final GithubHttpCredentialsDTO credentials =
          (GithubHttpCredentialsDTO) githubConnectorDTO.getAuthentication().getCredentials();
      String username;
      SecretRefData usernameRef, passwordRef;
      if (credentials.getType() == GithubHttpAuthenticationType.USERNAME_AND_PASSWORD) {
        final GithubUsernamePasswordDTO httpCredentialsSpec =
            (GithubUsernamePasswordDTO) credentials.getHttpCredentialsSpec();
        username = httpCredentialsSpec.getUsername();
        usernameRef = httpCredentialsSpec.getUsernameRef();
        passwordRef = httpCredentialsSpec.getPasswordRef();
      } else if (credentials.getType() == GithubHttpAuthenticationType.USERNAME_AND_TOKEN) {
        final GithubUsernameTokenDTO githubUsernameTokenDTO =
            (GithubUsernameTokenDTO) credentials.getHttpCredentialsSpec();
        username = githubUsernameTokenDTO.getUsername();
        usernameRef = githubUsernameTokenDTO.getUsernameRef();
        passwordRef = githubUsernameTokenDTO.getTokenRef();
      } else if (credentials.getType() == GithubHttpAuthenticationType.GITHUB_APP) {
        final GithubAppDTO githubAppDTO = (GithubAppDTO) credentials.getHttpCredentialsSpec();
        username = githubAppDTO.username;
        usernameRef = null;
        passwordRef = githubAppDTO.getPrivateKeyRef();
      } else {
        final GithubOauthDTO githubOauthDTO = (GithubOauthDTO) credentials.getHttpCredentialsSpec();
        username = GithubOauthDTO.userName;
        usernameRef = null;
        passwordRef = githubOauthDTO.getTokenRef();
      }
      GitConfigDTO gitConfigForHttp = GitConfigCreater.getGitConfigForHttp(connectionType, url, validationRepo,
          username, usernameRef, passwordRef, githubConnectorDTO.getDelegateSelectors());
      gitConfigForHttp.setExecuteOnDelegate(githubConnectorDTO.getExecuteOnDelegate());
      return gitConfigForHttp;

    } else if (authType == GitAuthType.SSH) {
      final GithubSshCredentialsDTO credentials =
          (GithubSshCredentialsDTO) githubConnectorDTO.getAuthentication().getCredentials();
      final SecretRefData sshKeyRef = credentials.getSshKeyRef();
      GitConfigDTO gitConfigForSsh = GitConfigCreater.getGitConfigForSsh(
          connectionType, url, validationRepo, sshKeyRef, githubConnectorDTO.getDelegateSelectors());
      gitConfigForSsh.setExecuteOnDelegate(githubConnectorDTO.getExecuteOnDelegate());
      return gitConfigForSsh;
    }
    throw new InvalidRequestException("Unknown auth type: " + authType);
  }
}
