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
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secrets.SecretService;

import software.wings.ngmigration.CgEntityId;

import com.google.inject.Inject;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class HarnessSecretMigrator implements SecretMigrator {
  @Inject private SecretService secretService;

  @Override
  public SecretTextSpecDTO getSecretSpec(
      EncryptedData encryptedData, SecretManagerConfig vaultConfig, String secretManagerIdentifier) {
    return SecretTextSpecDTO.builder()
        .valueType(ValueType.Inline)
        .value(String.valueOf(secretService.fetchSecretValue(encryptedData)))
        .secretManagerIdentifier(secretManagerIdentifier)
        .build();
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SecretManagerConfig secretManagerConfig, Map<CgEntityId, NGYamlFile> migratedEntities) {
    return null;
  }
}
