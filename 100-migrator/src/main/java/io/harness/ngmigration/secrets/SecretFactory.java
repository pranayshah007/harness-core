/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.secrets;

import static io.harness.secretmanagerclient.SecretType.SecretText;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.SecretManagerConfig;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretRequestWrapper;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.dto.SecretManagerCreatedDTO;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.secretmanagerclient.ValueType;

import software.wings.beans.GcpKmsConfig;
import software.wings.beans.LocalEncryptionConfig;
import software.wings.beans.VaultConfig;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.NGMigrationEntityType;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class SecretFactory {
  @Inject private VaultSecretMigrator vaultSecretMigrator;
  @Inject private HarnessSecretMigrator harnessSecretMigrator;

  public static ConnectorType getConnectorType(SecretManagerConfig secretManagerConfig) {
    if (secretManagerConfig instanceof GcpKmsConfig) {
      return ConnectorType.GCP_KMS;
    }
    if (secretManagerConfig instanceof LocalEncryptionConfig) {
      return ConnectorType.LOCAL;
    }
    if (secretManagerConfig instanceof VaultConfig) {
      return ConnectorType.VAULT;
    }
    throw new InvalidRequestException("Unsupported secret manager");
  }

  public SecretMigrator getSecretMigrator(SecretManagerConfig secretManagerConfig) {
    if (secretManagerConfig instanceof VaultConfig) {
      return vaultSecretMigrator;
    }
    if (secretManagerConfig instanceof LocalEncryptionConfig) {
      return harnessSecretMigrator;
    }
    // Handle special case for Harness Secret managers
    if (secretManagerConfig instanceof GcpKmsConfig
        && "Harness Secrets Manager".equals(secretManagerConfig.getName().trim())) {
      return harnessSecretMigrator;
    }
    throw new InvalidRequestException("Unsupported secret manager");
  }

  public SecretDTOV2 getSecret(MigrationInputDTO inputDTO, String identifier, EncryptedData encryptedData,
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    SecretSpecDTO secretSpecDTO = getSecretSpec(encryptedData, entities, migratedEntities);
    if (secretSpecDTO == null) {
      return null;
    }
    return SecretDTOV2.builder()
        .type(SecretText)
        .name(encryptedData.getName())
        .identifier(identifier)
        .description(null)
        .orgIdentifier(inputDTO.getOrgIdentifier())
        .projectIdentifier(inputDTO.getProjectIdentifier())
        .spec(secretSpecDTO)
        .build();
  }

  private SecretSpecDTO getSecretSpec(EncryptedData encryptedData, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    CgEntityId secretManagerId =
        CgEntityId.builder().type(NGMigrationEntityType.SECRET_MANAGER).id(encryptedData.getKmsId()).build();
    if (!entities.containsKey(secretManagerId)) {
      return null;
    }
    SecretManagerConfig secretManagerConfig = (SecretManagerConfig) entities.get(secretManagerId).getEntity();

    return getSecretMigrator(secretManagerConfig)
        .getSecretSpec(encryptedData, secretManagerConfig,
            MigratorUtility.getIdentifierWithScope(migratedEntities.get(secretManagerId).getNgEntityDetail()));
  }

  public static SecretDTOV2 getHarnessSecretManagerSpec(
      NgEntityDetail entityDetail, String secretName, String secretValue) {
    SecretSpecDTO secretSpecDTO = SecretTextSpecDTO.builder()
                                      .valueType(ValueType.Inline)
                                      .value(secretValue)
                                      .secretManagerIdentifier(MigratorUtility.getIdentifierWithScope(
                                          NgEntityDetail.builder()
                                              .identifier("harnessSecretManager")
                                              .orgIdentifier(entityDetail.getOrgIdentifier())
                                              .projectIdentifier(entityDetail.getProjectIdentifier())
                                              .build()))
                                      .build();
    return SecretDTOV2.builder()
        .type(SecretText)
        .name(secretName)
        .identifier(entityDetail.getIdentifier())
        .description(null)
        .orgIdentifier(entityDetail.getOrgIdentifier())
        .projectIdentifier(entityDetail.getProjectIdentifier())
        .spec(secretSpecDTO)
        .build();
  }

  public SecretManagerCreatedDTO getConfigDTO(SecretManagerConfig secretManagerConfig, MigrationInputDTO inputDTO,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    return getSecretMigrator(secretManagerConfig).getConfigDTO(secretManagerConfig, inputDTO, migratedEntities);
  }

  public static boolean isStoredInHarnessSecretManager(NGYamlFile yamlFile) {
    SecretRequestWrapper secretDTOV2 = (SecretRequestWrapper) yamlFile.getYaml();
    if (SecretText.equals(secretDTOV2.getSecret().getType())) {
      SecretTextSpecDTO specDTO = (SecretTextSpecDTO) secretDTOV2.getSecret().getSpec();
      return Sets.newHashSet("account.harnessSecretManager", "org.harnessSecretManager", "harnessSecretManager")
          .contains(specDTO.getSecretManagerIdentifier());
    }
    return false;
  }
}
