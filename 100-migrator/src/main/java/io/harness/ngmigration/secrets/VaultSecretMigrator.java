/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.secrets;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.SecretManagerConfig;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.dto.SecretManagerCreatedDTO;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;

import software.wings.beans.VaultConfig;
import software.wings.ngmigration.CgEntityId;

import java.util.Collections;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
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
  public SecretManagerCreatedDTO getConfigDTO(SecretManagerConfig secretManagerConfig, MigrationInputDTO inputDTO,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    VaultConfig vaultConfig = (VaultConfig) secretManagerConfig;
    // TODO: Handle all cases
    String secretId =
        String.format("migratedHarnessSecret_%s", MigratorUtility.generateIdentifier(vaultConfig.getName()));
    NgEntityDetail secretEntityDetail = NgEntityDetail.builder()
                                            .identifier(secretId)
                                            .orgIdentifier(inputDTO.getOrgIdentifier())
                                            .projectIdentifier(inputDTO.getProjectIdentifier())
                                            .build();
    SecretDTOV2 secretDTOV2 =
        SecretDTOV2.builder()
            .identifier(secretId)
            .name(String.format("Auto Generated Secret for Secret Manager - %s", vaultConfig.getName()))
            .description("Auto generated secret by Harness.")
            .orgIdentifier(inputDTO.getOrgIdentifier())
            .projectIdentifier(inputDTO.getProjectIdentifier())
            .type(SecretType.SecretText)
            .spec(SecretTextSpecDTO.builder()
                      .secretManagerIdentifier(
                          MigratorUtility.getIdentifierWithScope(NgEntityDetail.builder()
                                                                     .projectIdentifier(inputDTO.getProjectIdentifier())
                                                                     .orgIdentifier(inputDTO.getOrgIdentifier())
                                                                     .identifier("harnessSecretManager")
                                                                     .build()))
                      .value(vaultConfig.getAuthToken())
                      .valueType(ValueType.Inline)
                      .build())
            .build();
    VaultConnectorDTO connectorDTO = VaultConnectorDTO.builder()
                                         .authToken(SecretRefData.builder()
                                                        .scope(MigratorUtility.getScope(secretEntityDetail))
                                                        .identifier(secretId)
                                                        .build())
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
    return SecretManagerCreatedDTO.builder()
        .connector(connectorDTO)
        .secrets(Collections.singletonList(secretDTOV2))
        .build();
  }
}
