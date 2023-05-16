/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.gitintegration.processor.impl;

import static io.harness.idp.gitintegration.utils.GitIntegrationConstants.CATALOG_INFRA_CONNECTOR_TYPE_DIRECT;
import static io.harness.idp.gitintegration.utils.GitIntegrationConstants.CATALOG_INFRA_CONNECTOR_TYPE_PROXY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.outcome.BitbucketHttpCredentialsOutcomeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.Constants;
import io.harness.idp.gitintegration.processor.base.ConnectorProcessor;
import io.harness.idp.gitintegration.utils.GitIntegrationConstants;
import io.harness.idp.gitintegration.utils.GitIntegrationUtils;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.CatalogConnectorInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class BitbucketConnectorProcessor extends ConnectorProcessor {
  @Override
  public String getInfraConnectorType(ConnectorInfoDTO connectorInfoDTO) {
    BitbucketConnectorDTO config = (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();
    return config.getExecuteOnDelegate() ? CATALOG_INFRA_CONNECTOR_TYPE_PROXY : CATALOG_INFRA_CONNECTOR_TYPE_DIRECT;
  }

  @Override
  public Map<String, BackstageEnvVariable> getConnectorAndSecretsInfo(
      String accountIdentifier, ConnectorInfoDTO connectorInfoDTO) {
    String connectorIdentifier = connectorInfoDTO.getIdentifier();
    String host = GitIntegrationUtils.getHostForConnector(connectorInfoDTO);
    if (!connectorInfoDTO.getConnectorType().toString().equals(GitIntegrationConstants.BITBUCKET_CONNECTOR_TYPE)) {
      throw new InvalidRequestException(
          String.format("Connector with id - [%s] is not bitbucket connector for accountId: [%s]", connectorIdentifier,
              accountIdentifier));
    }

    BitbucketConnectorDTO config = (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();
    BitbucketApiAccessDTO apiAccess = config.getApiAccess();
    BitbucketHttpCredentialsOutcomeDTO outcome =
        (BitbucketHttpCredentialsOutcomeDTO) config.getAuthentication().getCredentials().toOutcome();
    if (!outcome.getType().toString().equals(GitIntegrationConstants.USERNAME_PASSWORD_AUTH_TYPE)) {
      throw new InvalidRequestException(String.format(
          " Authentication is not Username and Password for Bitbucket Connector with id - [%s], accountId: [%s]",
          connectorIdentifier, accountIdentifier));
    }

    Map<String, BackstageEnvVariable> secrets = new HashMap<>();

    BitbucketUsernamePasswordDTO spec = (BitbucketUsernamePasswordDTO) outcome.getSpec();
    String pwdSecretIdentifier = spec.getPasswordRef().getIdentifier();
    if (pwdSecretIdentifier.isEmpty()) {
      throw new InvalidRequestException(String.format(
          "Secret identifier not found for connector: [%s], accountId: [%s]", connectorIdentifier, accountIdentifier));
    }

    if (spec.getUsernameRef() == null) {
      secrets.put(Constants.BITBUCKET_USERNAME,
          new BackstageEnvConfigVariable()
              .value(spec.getUsername())
              .envName(Constants.BITBUCKET_USERNAME)
              .type(BackstageEnvVariable.TypeEnum.CONFIG));
    } else {
      secrets.put(Constants.BITBUCKET_USERNAME,
          GitIntegrationUtils.getBackstageEnvSecretVariable(
              spec.getUsernameRef().getIdentifier(), Constants.BITBUCKET_USERNAME));
    }

    secrets.put(Constants.BITBUCKET_TOKEN,
        GitIntegrationUtils.getBackstageEnvSecretVariable(pwdSecretIdentifier, Constants.BITBUCKET_TOKEN));

    if (apiAccess != null && apiAccess.getType().toString().equals(GitIntegrationConstants.BITBUCKET_API_ACCESS_TYPE)
        && !host.equals(GitIntegrationConstants.HOST_FOR_BITBUCKET_CLOUD)) {
      BitbucketUsernameTokenApiAccessDTO bitbucketUsernameTokenApiAccessDTO =
          (BitbucketUsernameTokenApiAccessDTO) apiAccess.getSpec();
      if (bitbucketUsernameTokenApiAccessDTO.getUsernameRef() == null) {
        secrets.put(Constants.BITBUCKET_USERNAME_API_ACCESS,
            new BackstageEnvConfigVariable()
                .value(bitbucketUsernameTokenApiAccessDTO.getUsername())
                .envName(Constants.BITBUCKET_USERNAME_API_ACCESS)
                .type(BackstageEnvVariable.TypeEnum.CONFIG));
      } else {
        secrets.put(Constants.BITBUCKET_USERNAME_API_ACCESS,
            GitIntegrationUtils.getBackstageEnvSecretVariable(
                bitbucketUsernameTokenApiAccessDTO.getUsernameRef().getIdentifier(),
                Constants.BITBUCKET_USERNAME_API_ACCESS));
      }
      secrets.put(Constants.BITBUCKET_API_ACCESS_TOKEN,
          GitIntegrationUtils.getBackstageEnvSecretVariable(
              bitbucketUsernameTokenApiAccessDTO.getTokenRef().getIdentifier(), Constants.BITBUCKET_API_ACCESS_TOKEN));
    }
    return secrets;
  }

  @Override
  public void performPushOperation(String accountIdentifier, CatalogConnectorInfo catalogConnectorInfo,
      String locationParentPath, List<String> filesToPush, boolean throughGrpc) {
    ConnectorInfoDTO connectorInfoDTO =
        getConnectorInfo(accountIdentifier, catalogConnectorInfo.getConnector().getIdentifier());
    Map<String, BackstageEnvVariable> connectorSecretsInfo =
        getConnectorAndSecretsInfo(accountIdentifier, connectorInfoDTO);
    BackstageEnvSecretVariable envSecretVariable =
        (BackstageEnvSecretVariable) connectorSecretsInfo.get(Constants.BITBUCKET_TOKEN);
    String bitbucketConnectorSecret = GitIntegrationUtils.decryptSecret(ngSecretService, accountIdentifier, null, null,
        envSecretVariable.getHarnessSecretIdentifier(), catalogConnectorInfo.getConnector().getIdentifier());

    BitbucketConnectorDTO config = (BitbucketConnectorDTO) connectorInfoDTO.getConnectorConfig();
    BitbucketHttpCredentialsOutcomeDTO outcome =
        (BitbucketHttpCredentialsOutcomeDTO) config.getAuthentication().getCredentials().toOutcome();
    BitbucketUsernamePasswordDTO spec = (BitbucketUsernamePasswordDTO) outcome.getSpec();

    performPushOperationInternal(accountIdentifier, catalogConnectorInfo, locationParentPath, filesToPush,
        spec.getUsername(), bitbucketConnectorSecret, throughGrpc);
  }
}
