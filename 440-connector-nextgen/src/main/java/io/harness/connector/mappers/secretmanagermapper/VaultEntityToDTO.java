/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.secretmanagermapper;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.vaultconnector.VaultConnector;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
import io.harness.encryption.SecretRefHelper;

@OwnedBy(PL)
public class VaultEntityToDTO implements ConnectorEntityToDTOMapper<VaultConnectorDTO, VaultConnector> {
  @Override
  public VaultConnectorDTO createConnectorDTO(VaultConnector connector) {
    return VaultConnectorDTO.builder()
        .authToken(SecretRefHelper.createSecretRef(connector.getAuthTokenRef()))
        .isDefault(connector.isDefault())
        .isReadOnly(connector.isReadOnly())
        .vaultUrl(connector.getVaultUrl())
        .secretEngineName(connector.getSecretEngineName())
        .secretEngineVersion(connector.getSecretEngineVersion())
        .renewalIntervalMinutes(connector.getRenewalIntervalMinutes())
        .basePath(connector.getBasePath())
        .namespace(connector.getNamespace())
        .sinkPath(connector.getSinkPath())
        .useVaultAgent(connector.isUseVaultAgent())
        .useAwsIam(connector.getUseAwsIam())
        .awsRegion(connector.getAwsRegion())
        .vaultAwsIamRole(connector.getVaultAwsIamRoleRef())
        .headerAwsIam(SecretRefHelper.createSecretRef(connector.getXVaultAwsIamServerIdRef()))
        .useK8sAuth(connector.getUseK8sAuth())
        .vaultK8sAuthRole(connector.getVaultK8sAuthRole())
        .serviceAccountTokenPath(connector.getServiceAccountTokenPath())
        .k8sAuthEndpoint(connector.getK8sAuthEndpoint())
        .secretEngineManuallyConfigured(connector.isSecretEngineManuallyConfigured())
        .appRoleId(connector.getAppRoleId())
        .secretId(SecretRefHelper.createSecretRef(connector.getSecretIdRef()))
        .delegateSelectors(connector.getDelegateSelectors())
        .renewAppRoleToken(connector.getRenewAppRoleToken())
        .enableCache(connector.getEnableCache())
        .build();
  }
}
