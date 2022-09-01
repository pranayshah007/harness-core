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
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.secretmanagerclient.ValueType;

import software.wings.ngmigration.CgEntityId;

import java.util.Map;

public class HarnessSecretMigrator implements SecretMigrator {
  @Override
  public SecretTextSpecDTO getSecretSpec(
      EncryptedData encryptedData, SecretManagerConfig vaultConfig, String secretManagerIdentifier) {
    return SecretTextSpecDTO.builder()
        .valueType(ValueType.Inline)
        .value("__ACTUAL_SECRET__")
        // TODO: Use actual secret manager identifier
        .secretManagerIdentifier("account.harnessSecretManager")
        .build();
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SecretManagerConfig secretManagerConfig, Map<CgEntityId, NgEntityDetail> migratedEntities) {
    return null;
  }
}
