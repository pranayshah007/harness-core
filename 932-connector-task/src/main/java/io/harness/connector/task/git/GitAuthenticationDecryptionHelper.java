/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.task.git;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.connector.scm.github.GithubConnectorConstants.GITHUB_APP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsSpecDTO;
import io.harness.exception.InvalidRequestException;

import java.util.Arrays;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDP)
public class GitAuthenticationDecryptionHelper {
  public boolean isGitHubAppAuthentication(ScmConnector scmConnector) {
    if (scmConnector instanceof GithubConnectorDTO) {
      GithubConnectorDTO gitHubConnector = (GithubConnectorDTO) scmConnector;
      return gitHubConnector.getAuthentication().getAuthType() == GitAuthType.HTTP
          && ((GithubHttpCredentialsDTO) gitHubConnector.getAuthentication().getCredentials()).getHttpCredentialsSpec()
                 instanceof GithubAppDTO;
    } else if (scmConnector instanceof GitConfigDTO) {
      GitConfigDTO gitConfigDTO = (GitConfigDTO) scmConnector;
      return gitConfigDTO.getGitAuth() instanceof GitHTTPAuthenticationDTO
          && (GITHUB_APP.equals(
              ((GitHTTPAuthenticationDTO) gitConfigDTO.getGitAuth()).getPasswordRef().getIdentifier()))
          && (Arrays.equals(GITHUB_APP.toCharArray(),
              ((GitHTTPAuthenticationDTO) gitConfigDTO.getGitAuth()).getPasswordRef().getDecryptedValue()));
    }
    return false;
  }

  public GithubHttpCredentialsDTO getGitHubAppAuthenticationDecryptableEntity(
      GithubConnectorDTO githubConnectorDTO, DecryptableEntity decryptableEntity) {
    if (githubConnectorDTO == null) {
      throw new InvalidRequestException("The given connector can not be null");
    }
    GithubHttpCredentialsDTO githubHttpCredentialsDTO =
        (GithubHttpCredentialsDTO) githubConnectorDTO.getAuthentication().getCredentials();
    githubHttpCredentialsDTO.setHttpCredentialsSpec((GithubHttpCredentialsSpecDTO) decryptableEntity);
    return githubHttpCredentialsDTO;
  }
}
