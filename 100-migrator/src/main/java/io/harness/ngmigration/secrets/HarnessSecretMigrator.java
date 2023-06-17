/*
 * Copyright 2022 Harness Inc. All rights reserved.
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
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretDTOV2.SecretDTOV2Builder;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.dto.SecretManagerCreatedDTO;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secrets.SecretService;

import software.wings.ngmigration.CgEntityId;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class HarnessSecretMigrator implements io.harness.ngmigration.secrets.SecretMigrator {
  @Inject private SecretService secretService;

  @Override
  public SecretDTOV2Builder getSecretDTOBuilder(
      EncryptedData encryptedData, SecretManagerConfig vaultConfig, String secretManagerIdentifier) {
    String value = "PLACE_HOLDER_SECRET";
    try {
      value = String.valueOf(secretService.fetchSecretValue(encryptedData));
    } catch (Exception e) {
      log.warn("There was an error with fetching actual secret value", e);
    }
    return SecretDTOV2.builder()
        .type(SecretText)
        .spec(SecretTextSpecDTO.builder()
                  .valueType(ValueType.Inline)
                  .value(value)
                  .secretManagerIdentifier(secretManagerIdentifier)
                  .build());
  }

  @Override
  public String getSecretFile(EncryptedData encryptedData, SecretManagerConfig secretManagerConfig) {
    if (!SettingVariableTypes.CONFIG_FILE.equals(encryptedData.getType())) {
      return null;
    }
    try {
      return String.valueOf(secretService.fetchSecretValue(encryptedData));
    } catch (Exception e) {
      log.error(String.format("There was an error processing the secret file %s", encryptedData.getName()));
      return null;
    }
  }

  @Override
  public String getEncryptionKey(EncryptedData encryptedData, SecretManagerConfig secretManagerConfig) {
    return null;
  }

  @Override
  public String getEncryptionValue(EncryptedData encryptedData, SecretManagerConfig secretManagerConfig) {
    return null;
  }

  @Override
  public SecretManagerCreatedDTO getConfigDTO(SecretManagerConfig secretManagerConfig, MigrationInputDTO inputDTO,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    return SecretManagerCreatedDTO.builder().build();
  }
}
