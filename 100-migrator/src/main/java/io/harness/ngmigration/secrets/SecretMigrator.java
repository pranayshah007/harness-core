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
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretDTOV2.SecretDTOV2Builder;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.dto.SecretManagerCreatedDTO;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;

import software.wings.ngmigration.CgEntityId;

import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public interface SecretMigrator {
  SecretDTOV2Builder getSecretDTOBuilder(
      EncryptedData encryptedData, SecretManagerConfig vaultConfig, String secretManagerIdentifier);

  default String getSecretFile(EncryptedData encryptedData, SecretManagerConfig secretManagerConfig) {
    return null;
  }

  SecretManagerCreatedDTO getConfigDTO(SecretManagerConfig secretManagerConfig, MigrationInputDTO inputDTO,
      Map<CgEntityId, NGYamlFile> migratedEntities);

  default SecretDTOV2 getSecretDTO(SecretManagerConfig secretsManagerConfig, MigrationInputDTO inputDTO,
      String secretIdentifier, String actualSecret) {
    return SecretDTOV2.builder()
        .identifier(secretIdentifier)
        .name(secretIdentifier)
        .description(String.format("Auto Generated Secret for Secret Manager - %s", secretsManagerConfig.getName()))
        .orgIdentifier(inputDTO.getOrgIdentifier())
        .projectIdentifier(inputDTO.getProjectIdentifier())
        .type(SecretType.SecretText)
        .spec(SecretTextSpecDTO.builder()
                  .secretManagerIdentifier("harnessSecretManager")
                  .value(actualSecret)
                  .valueType(ValueType.Inline)
                  .build())
        .build();
  }
}
