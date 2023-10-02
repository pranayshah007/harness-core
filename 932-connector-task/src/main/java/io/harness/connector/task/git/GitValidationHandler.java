/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.task.git;

import static io.harness.connector.helper.GitApiAccessDecryptionHelper.hasApiAccess;

import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.scm.ScmValidationParams;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.shell.SshSessionConfig;

import com.google.inject.Inject;

public class GitValidationHandler implements ConnectorValidationHandler {
  @Inject private GitCommandTaskHandler gitCommandTaskHandler;
  @Inject private GitDecryptionHelper gitDecryptionHelper;

  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    final ScmValidationParams scmValidationParams = (ScmValidationParams) connectorValidationParams;
    GitConfigDTO gitConfig = ScmConnectorMapper.toGitConfigDTO(scmValidationParams.getGitConfigDTO());

    gitDecryptionHelper.decryptGitConfig(gitConfig, scmValidationParams.getEncryptedDataDetails());
    final SshSessionConfig sshSessionConfig = gitDecryptionHelper.getSSHSessionConfig(
        scmValidationParams.getSshKeySpecDTO(), scmValidationParams.getEncryptedDataDetails());

    if (hasApiAccess(scmValidationParams.getScmConnector())) {
      gitDecryptionHelper.decryptApiAccessConfig(
          scmValidationParams.getScmConnector(), scmValidationParams.getEncryptedDataDetails());
    }

    if (scmValidationParams.isGithubAppAuthentication()) {
      return gitCommandTaskHandler.validateGitCredentialsForGithubAppAuth(
          (GithubConnectorDTO) scmValidationParams.getScmConnector(), scmValidationParams.getEncryptedDataDetails());
    }
    return gitCommandTaskHandler.validateGitCredentials(
        gitConfig, scmValidationParams.getScmConnector(), accountIdentifier, sshSessionConfig);
  }
}
