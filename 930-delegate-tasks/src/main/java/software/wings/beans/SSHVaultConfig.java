/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.security.encryption.SecretManagerType.SSH;

import io.harness.beans.SecretManagerCapabilities;
import io.harness.secretmanagerclient.dto.SSHVaultConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.security.encryption.EncryptionType;
import io.harness.security.encryption.SecretManagerType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SSHVaultConfig extends BaseVaultConfig {
  public static final String VAULT_VAILDATION_URL = "harness_vault_validation";
  public static final String DEFAULT_BASE_PATH = "/harness";
  public static final String DEFAULT_SECRET_ENGINE_NAME = "secret";
  public static final String DEFAULT_KEY_NAME = "value";
  public static final String PATH_SEPARATOR = "/";
  public static final String KEY_SPEARATOR = "#";

  @Override
  public SecretManagerType getType() {
    return SSH;
  }

  @Override
  public List<SecretManagerCapabilities> getSecretManagerCapabilities() {
    return new ArrayList<>();
  }

  @Override
  public EncryptionType getEncryptionType() {
    return EncryptionType.VAULT_SSH;
  }

  @Override
  public SecretManagerConfigDTO toDTO(boolean maskSecrets) {
    SSHVaultConfigDTO ngVaultConfigDTO = SSHVaultConfigDTO.builder()
                                             .name(getName())
                                             .renewalInterval(getRenewalInterval())
                                             .secretEngineName(getSecretEngineName())
                                             .vaultUrl(getVaultUrl())
                                             .build();
    if (!maskSecrets) {
      ngVaultConfigDTO.setAuthToken(getAuthToken());
      ngVaultConfigDTO.setSecretId(getSecretId());
    }
    return ngVaultConfigDTO;
  }
}
