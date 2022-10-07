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
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.dto.SecretManagerCreatedDTO;

import software.wings.ngmigration.CgEntityId;

import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public interface SecretMigrator {
  SecretTextSpecDTO getSecretSpec(
      EncryptedData encryptedData, SecretManagerConfig vaultConfig, String secretManagerIdentifier);

  SecretManagerCreatedDTO getConfigDTO(SecretManagerConfig secretManagerConfig, MigrationInputDTO inputDTO,
      Map<CgEntityId, NGYamlFile> migratedEntities);
}
