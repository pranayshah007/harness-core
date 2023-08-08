package io.harness.connector.task.git;

/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.adapter.GitConfigCreater;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.List;

@OwnedBy(CDP)
public class GithubAppToGitMapperDelegate {
  @Inject GitHubAppAuthenticationHelper gitHubAppAuthenticationHelper;

  public GitConfigDTO mapToGitConfigDTO(
      GithubConnectorDTO githubConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    final GitConnectionType connectionType = githubConnectorDTO.getConnectionType();
    final String url = githubConnectorDTO.getUrl();
    final String validationRepo = githubConnectorDTO.getValidationRepo();
    String username = GithubAppDTO.username;
    SecretRefData passwordRef =
        gitHubAppAuthenticationHelper.getGithubAppSecretFromConnector(githubConnectorDTO, encryptedDataDetails);

    GitConfigDTO gitConfigForHttp = GitConfigCreater.getGitConfigForHttp(
        connectionType, url, validationRepo, username, null, passwordRef, githubConnectorDTO.getDelegateSelectors());
    gitConfigForHttp.setExecuteOnDelegate(githubConnectorDTO.getExecuteOnDelegate());
    return gitConfigForHttp;
  }
}
