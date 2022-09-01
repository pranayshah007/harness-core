/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.secrets;

import io.harness.beans.EncryptedData;
import io.harness.beans.SecretManagerConfig;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.secretmanagerclient.ValueType;

import software.wings.beans.VaultConfig;
import software.wings.ngmigration.CgEntityId;

import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class VaultSecretMigrator implements SecretMigrator {
  @Override
  public SecretTextSpecDTO getSecretSpec(
      EncryptedData encryptedData, SecretManagerConfig secretManagerConfig, String secretManagerIdentifier) {
    VaultConfig vaultConfig = (VaultConfig) secretManagerConfig;
    String value;
    if (StringUtils.isNotBlank(encryptedData.getPath())) {
      value = encryptedData.getPath();
    } else {
      // TODO: Handle nulls for basePath & trailing slashes
      value = vaultConfig.getBasePath() + encryptedData.getEncryptionKey() + "#value";
    }
    return SecretTextSpecDTO.builder()
        .valueType(ValueType.Reference)
        .value(value)
        .secretManagerIdentifier(secretManagerIdentifier)
        .build();
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SecretManagerConfig secretManagerConfig, Map<CgEntityId, NgEntityDetail> migratedEntities) {
    VaultConfig vaultConfig = (VaultConfig) secretManagerConfig;
    // TODO: Handle all cases
    return VaultConnectorDTO.builder()
        .authToken(SecretRefData.builder().scope(Scope.ACCOUNT).identifier("__harness_please_fix_me__").build())
        .basePath(vaultConfig.getBasePath())
        .vaultUrl(vaultConfig.getVaultUrl())
        .renewalIntervalMinutes(vaultConfig.getRenewalInterval())
        .secretEngineManuallyConfigured(vaultConfig.isEngineManuallyEntered())
        .secretEngineName(vaultConfig.getSecretEngineName())
        .secretEngineVersion(vaultConfig.getSecretEngineVersion())
        .useVaultAgent(vaultConfig.isUseVaultAgent())
        .useAwsIam(false)
        .useK8sAuth(false)
        .isDefault(vaultConfig.isDefault())
        .isReadOnly(vaultConfig.isReadOnly())
        .delegateSelectors(null)
        .build();
  }
}
