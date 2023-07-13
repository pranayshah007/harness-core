/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.heartbeat;

import static io.harness.remote.client.CGRestUtils.getResponse;

import io.harness.account.AccountClient;
import io.harness.beans.FeatureName;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.helper.GitAuthenticationDecryptionHelper;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.ScmValidationParams;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class ScmConnectorValidationParamsProvider implements ConnectorValidationParamsProvider {
  @Inject GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  @Inject private AccountClient accountClient;

  @Override
  public ConnectorValidationParams getConnectorValidationParams(ConnectorInfoDTO connectorInfoDTO, String connectorName,
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    ConnectorConfigDTO connectorConfigDTO = connectorInfoDTO.getConnectorConfig();
    ScmConnector scmConnector = (ScmConnector) connectorConfigDTO;
    final GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(scmConnector);
    SSHKeySpecDTO sshKeySpecDTO =
        gitConfigAuthenticationInfoHelper.getSSHKey(gitConfigDTO, accountIdentifier, orgIdentifier, projectIdentifier);
    NGAccess ngAccess = BaseNGAccess.builder()
                            .accountIdentifier(accountIdentifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .build();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    encryptedDataDetails.addAll(
        gitConfigAuthenticationInfoHelper.getEncryptedDataDetails(gitConfigDTO, sshKeySpecDTO, ngAccess));
    if (GitApiAccessDecryptionHelper.hasApiAccess(scmConnector)) {
      encryptedDataDetails.addAll(
          gitConfigAuthenticationInfoHelper.getApiAccessEncryptedDataDetail(scmConnector, ngAccess));
    }

    final boolean githubAppAuthentication = GitAuthenticationDecryptionHelper.isGitHubAppAuthentication(scmConnector)
        && getResponse(
            accountClient.isFeatureFlagEnabled(FeatureName.CDS_GITHUB_APP_AUTHENTICATION.name(), accountIdentifier));
    if (githubAppAuthentication) {
      encryptedDataDetails.addAll(
          gitConfigAuthenticationInfoHelper.getGithubAppEncryptedDataDetail(scmConnector, ngAccess));
    }
    return ScmValidationParams.builder()
        .scmConnector(scmConnector)
        .encryptedDataDetails(encryptedDataDetails)
        .gitConfigDTO(gitConfigDTO)
        .connectorName(connectorName)
        .githubAppAuthentication(githubAppAuthentication)
        .sshKeySpecDTO(sshKeySpecDTO)
        .build();
  }
}
