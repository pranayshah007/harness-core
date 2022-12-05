/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.security.encryption.common.EncryptionType.VAULT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretManagerConfig;
import io.harness.connector.ConnectorValidationResult;
import io.harness.secretmanagerclient.dto.SecretManagerConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.SecretManagerMetadataDTO;
import io.harness.secretmanagerclient.dto.SecretManagerMetadataRequestDTO;

import software.wings.beans.VaultConfig;

import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;

@OwnedBy(PL)
public interface NGSecretManagerService {
  static boolean isReadOnlySecretManager(SecretManagerConfig secretManagerConfig) {
    if (secretManagerConfig == null) {
      return false;
    }
    if (VAULT.equals(secretManagerConfig.getEncryptionType())) {
      return ((VaultConfig) secretManagerConfig).isReadOnly();
    }
    return false;
  }

  SecretManagerConfig create(SecretManagerConfig secretManagerConfig);

  ConnectorValidationResult testConnection(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier);

  List<SecretManagerConfig> list(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> identifiers);

  Optional<SecretManagerConfig> get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, boolean maskSecrets);

  SecretManagerConfig update(@NotNull String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String identifier, SecretManagerConfigUpdateDTO updateDTO);

  SecretManagerConfig getGlobalSecretManager(String accountIdentifier);

  boolean delete(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, boolean softDelete);

  SecretManagerMetadataDTO getMetadata(String accountIdentifier, SecretManagerMetadataRequestDTO requestDTO);

  long getCountOfSecretsCreatedUsingSecretManager(
      String account, String org, String project, String secretManagerIdentifier);
}
