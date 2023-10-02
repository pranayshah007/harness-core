/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.task.git;

import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class ScmConnectorMapperDelegate {
  @Inject GithubAppToGitMapperDelegate githubAppToGitMapperDelegate;

  public GitConfigDTO toGitConfigDTO(ScmConnector scmConnector, List<EncryptedDataDetail> encryptedDataDetails) {
    if (isGitHubAppAuthentication(scmConnector)) {
      return githubAppToGitMapperDelegate.mapToGitConfigDTO((GithubConnectorDTO) scmConnector, encryptedDataDetails);
    } else {
      return ScmConnectorMapper.toGitConfigDTO(scmConnector);
    }
  }

  private boolean isGitHubAppAuthentication(ScmConnector scmConnector) {
    if (scmConnector instanceof GithubConnectorDTO) {
      GithubConnectorDTO gitHubConnector = (GithubConnectorDTO) scmConnector;
      return gitHubConnector.getAuthentication().getAuthType() == GitAuthType.HTTP
          && ((GithubHttpCredentialsDTO) gitHubConnector.getAuthentication().getCredentials()).getHttpCredentialsSpec()
                 instanceof GithubAppDTO;
    }
    return false;
  }
}
