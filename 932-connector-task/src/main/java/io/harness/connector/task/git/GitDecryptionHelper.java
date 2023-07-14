/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.task.git;

import static io.harness.connector.helper.GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity;
import static io.harness.connector.helper.GitApiAccessDecryptionHelper.hasApiAccess;

import io.harness.beans.DecryptableEntity;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.helper.GitAuthenticationDecryptionHelper;
import io.harness.connector.task.shell.SshSessionConfigMapper;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.SshSessionConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
public class GitDecryptionHelper {
  @Inject DecryptionHelper decryptionHelper;
  @Inject SshSessionConfigMapper sshSessionConfigMapper;

  public void decryptGitConfig(GitConfigDTO gitConfig, List<EncryptedDataDetail> encryptionDetails) {
    final DecryptableEntity decryptedGitAuth = decryptionHelper.decrypt(gitConfig.getGitAuth(), encryptionDetails);
    gitConfig.setGitAuth((GitAuthenticationDTO) decryptedGitAuth);
  }

  public SshSessionConfig getSSHSessionConfig(
      SSHKeySpecDTO sshKeySpecDTO, List<EncryptedDataDetail> encryptionDetails) {
    if (sshKeySpecDTO == null) {
      return null;
    }
    return sshSessionConfigMapper.getSSHSessionConfig(sshKeySpecDTO, encryptionDetails);
  }

  public void decryptApiAccessConfig(ScmConnector scmConnector, List<EncryptedDataDetail> encryptionDetails) {
    if (hasApiAccess(scmConnector)) {
      DecryptableEntity apiAccessDecryptableEntity = getAPIAccessDecryptableEntity(scmConnector);
      final DecryptableEntity decryptedScmSpec =
          decryptionHelper.decrypt(apiAccessDecryptableEntity, encryptionDetails);
      GitApiAccessDecryptionHelper.setAPIAccessDecryptableEntity(scmConnector, decryptedScmSpec);
    }
  }

  public GithubHttpCredentialsDTO decryptGitHubAppAuthenticationConfig(
      GithubConnectorDTO githubConnectorDTO, List<EncryptedDataDetail> encryptionDetails) {
    GithubCredentialsDTO githubCredentialsDTO = githubConnectorDTO.getAuthentication().getCredentials();
    DecryptableEntity decryptableEntity = ((GithubHttpCredentialsDTO) githubCredentialsDTO).getHttpCredentialsSpec();
    final DecryptableEntity decryptedScmSpec = decryptionHelper.decrypt(decryptableEntity, encryptionDetails);
    return GitAuthenticationDecryptionHelper.getGitHubAppAuthenticationDecryptableEntity(
        githubConnectorDTO, decryptedScmSpec);
  }
}
