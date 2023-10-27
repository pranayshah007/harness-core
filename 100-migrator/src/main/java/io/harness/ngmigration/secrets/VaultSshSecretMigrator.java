/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.secrets;
import static io.harness.secretmanagerclient.SecretType.SecretText;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.EncryptedData;
import io.harness.beans.SecretManagerConfig;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO.VaultConnectorDTOBuilder;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretDTOV2.SecretDTOV2Builder;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.dto.SecretManagerCreatedDTO;
import io.harness.secretmanagerclient.ValueType;

import software.wings.beans.SSHVaultConfig;
import software.wings.ngmigration.CgEntityId;

import java.util.Collections;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
public class VaultSshSecretMigrator implements SecretMigrator {
  @Override
  public SecretDTOV2Builder getSecretDTOBuilder(
      EncryptedData encryptedData, SecretManagerConfig secretManagerConfig, String secretManagerIdentifier) {
    String value;
    if (StringUtils.isNotBlank(encryptedData.getPath())) {
      value = encryptedData.getPath();
    } else {
      value = "/harness/" + encryptedData.getEncryptionKey() + "#value";
    }
    return SecretDTOV2.builder()
        .type(SecretText)
        .spec(SecretTextSpecDTO.builder()
                  .valueType(ValueType.Reference)
                  .value(value)
                  .secretManagerIdentifier(secretManagerIdentifier)
                  .build());
  }

  @Override
  public SecretManagerCreatedDTO getConfigDTO(SecretManagerConfig secretManagerConfig, MigrationInputDTO inputDTO,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    SSHVaultConfig vaultConfig = (SSHVaultConfig) secretManagerConfig;

    VaultConnectorDTOBuilder connectorDTO = VaultConnectorDTO.builder()
                                                .appRoleId(vaultConfig.getAppRoleId())
                                                .basePath("/harness")
                                                .vaultUrl(vaultConfig.getVaultUrl())
                                                .renewalIntervalMinutes(vaultConfig.getRenewalInterval())
                                                .secretEngineManuallyConfigured(vaultConfig.isEngineManuallyEntered())
                                                .secretEngineName(vaultConfig.getSecretEngineName())
                                                .secretEngineVersion(2)
                                                .useVaultAgent(vaultConfig.isUseVaultAgent())
                                                .useAwsIam(false)
                                                .isDefault(vaultConfig.isDefault())
                                                .isReadOnly(false)
                                                .delegateSelectors(vaultConfig.getDelegateSelectors());

    if (StringUtils.isNotBlank(vaultConfig.getSinkPath())) {
      connectorDTO.useK8sAuth(false).sinkPath(vaultConfig.getSinkPath());
    }

    return SecretManagerCreatedDTO.builder().connector(connectorDTO.build()).secrets(Collections.emptyList()).build();
  }
}
