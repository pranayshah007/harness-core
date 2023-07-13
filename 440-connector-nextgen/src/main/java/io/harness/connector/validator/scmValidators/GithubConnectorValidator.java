/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.validator.scmValidators;

import static io.harness.remote.client.CGRestUtils.getResponse;

import io.harness.account.AccountClient;
import io.harness.beans.FeatureName;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.helper.GitAuthenticationDecryptionHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.scm.adapter.GithubToGitMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.git.GitCommandParams;
import io.harness.delegate.task.TaskParameters;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.List;

public class GithubConnectorValidator extends AbstractGitConnectorValidator {
  @Inject AccountClient accountClient;
  @Inject GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;

  @Override
  public GitConfigDTO getGitConfigFromConnectorConfig(ConnectorConfigDTO connectorConfig) {
    return GithubToGitMapper.mapToGitConfigDTO((GithubConnectorDTO) connectorConfig);
  }

  @Override
  public <T extends ConnectorConfigDTO> TaskParameters getTaskParameters(
      T connectorConfig, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    GitCommandParams gitCommandParams = (GitCommandParams) super.getTaskParameters(
        connectorConfig, accountIdentifier, orgIdentifier, projectIdentifier);
    List<EncryptedDataDetail> authenticationEncryptedDataDetails = gitCommandParams.getEncryptionDetails();
    GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorConfig;

    boolean isGithubAppAuthentication = GitAuthenticationDecryptionHelper.isGitHubAppAuthentication(githubConnectorDTO)
        && getResponse(
            accountClient.isFeatureFlagEnabled(FeatureName.CDS_GITHUB_APP_AUTHENTICATION.name(), accountIdentifier));
    if (isGithubAppAuthentication) {
      authenticationEncryptedDataDetails.addAll(gitConfigAuthenticationInfoHelper.getGithubAppEncryptedDataDetail(
          githubConnectorDTO, super.getNgAccess(accountIdentifier, orgIdentifier, projectIdentifier)));
      gitCommandParams.setGithubAppAuthentication(true);
      gitCommandParams.setEncryptionDetails(authenticationEncryptedDataDetails);
    }

    return gitCommandParams;
  }

  @Override
  public ConnectorValidationResult validate(ConnectorConfigDTO connectorConfigDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    return super.validate(connectorConfigDTO, accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  @Override
  public ConnectorValidationResult validate(ConnectorResponseDTO connectorResponseDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String identifier) {
    return null;
  }
}
