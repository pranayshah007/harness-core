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
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
import io.harness.encryption.SecretRefHelper;

@OwnedBy(PL)
public class VaultDTOToEntity implements ConnectorDTOToEntityMapper<VaultConnectorDTO, VaultConnector> {
  @Override
  public VaultConnector toConnectorEntity(VaultConnectorDTO connectorDTO) {
    return VaultConnector.builder()
        .authTokenRef(SecretRefHelper.getSecretConfigString(connectorDTO.getAuthToken()))
        .accessType(connectorDTO.getAccessType())
        .isDefault(connectorDTO.isDefault())
        .isReadOnly(connectorDTO.isReadOnly())
        .secretEngineName(connectorDTO.getSecretEngineName())
        .vaultUrl(connectorDTO.getVaultUrl())
        .secretEngineVersion(connectorDTO.getSecretEngineVersion())
        .secretEngineManuallyConfigured(connectorDTO.isSecretEngineManuallyConfigured())
        .renewalIntervalMinutes(connectorDTO.getRenewalIntervalMinutes())
        .renewedAt(System.currentTimeMillis())
        .lastTokenLookupAt(System.currentTimeMillis())
        .appRoleId(connectorDTO.getAppRoleId())
        .basePath(connectorDTO.getBasePath())
        .namespace(connectorDTO.getNamespace())
        .sinkPath(connectorDTO.getSinkPath())
        .useVaultAgent(connectorDTO.isUseVaultAgent())
        .useAwsIam(connectorDTO.isUseAwsIam())
        .awsRegion(connectorDTO.getAwsRegion())
        .vaultAwsIamRoleRef(connectorDTO.getVaultAwsIamRole())
        .xVaultAwsIamServerIdRef(SecretRefHelper.getSecretConfigString(connectorDTO.getHeaderAwsIam()))
        .secretIdRef(SecretRefHelper.getSecretConfigString(connectorDTO.getSecretId()))
        .useK8sAuth(connectorDTO.isUseK8sAuth())
        .vaultK8sAuthRole(connectorDTO.getVaultK8sAuthRole())
        .serviceAccountTokenPath(connectorDTO.getServiceAccountTokenPath())
        .k8sAuthEndpoint(connectorDTO.getK8sAuthEndpoint())
        .renewAppRoleToken(connectorDTO.isRenewAppRoleToken())
        .enableCache(connectorDTO.isEnableCache())
        .build();
  }
}
