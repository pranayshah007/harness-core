/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.SecretManagerCapabilities.CAN_BE_DEFAULT_SM;
import static io.harness.beans.SecretManagerCapabilities.CREATE_FILE_SECRET;
import static io.harness.beans.SecretManagerCapabilities.CREATE_INLINE_SECRET;
import static io.harness.beans.SecretManagerCapabilities.CREATE_REFERENCE_SECRET;
import static io.harness.beans.SecretManagerCapabilities.TRANSITION_SECRET_FROM_SM;
import static io.harness.beans.SecretManagerCapabilities.TRANSITION_SECRET_TO_SM;
import static io.harness.security.encryption.SecretManagerType.VAULT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretManagerCapabilities;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.VaultConfigDTO;
import io.harness.security.encryption.EncryptionType;
import io.harness.security.encryption.SecretManagerType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.Attributes;
import com.google.common.collect.Lists;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

/**
 * Created by rsingh on 11/02/17.
 */

@OwnedBy(PL)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "VaultConfigKeys")
public class VaultConfig extends BaseVaultConfig {
  public static final String VAULT_VAILDATION_URL = "harness_vault_validation";
  public static final String DEFAULT_BASE_PATH = "/harness";
  public static final String DEFAULT_SECRET_ENGINE_NAME = "secret";
  public static final String DEFAULT_KEY_NAME = "value";
  public static final String PATH_SEPARATOR = "/";
  public static final String KEY_SPEARATOR = "#";

  @Attributes(title = "Base Path") private String basePath;

  // This field is deprecated and is not used anymore, will be removed in future. Please use renewalInterval
  @Attributes(title = "Renew token interval", required = true) @Deprecated private int renewIntervalHours;

  @Attributes(title = "Is Vault Read Only") private boolean isReadOnly;

  @Attributes(title = "Secret Engine Version") private int secretEngineVersion;

  @Override
  public EncryptionType getEncryptionType() {
    return EncryptionType.VAULT;
  }

  @Override
  public List<SecretManagerCapabilities> getSecretManagerCapabilities() {
    if (isReadOnly) {
      return Lists.newArrayList(CREATE_REFERENCE_SECRET);
    }
    List<SecretManagerCapabilities> secretManagerCapabilities =
        Lists.newArrayList(CREATE_INLINE_SECRET, CREATE_REFERENCE_SECRET, CREATE_FILE_SECRET, CAN_BE_DEFAULT_SM);
    if (!isTemplatized()) {
      secretManagerCapabilities.add(TRANSITION_SECRET_FROM_SM);
      secretManagerCapabilities.add(TRANSITION_SECRET_TO_SM);
    }
    return secretManagerCapabilities;
  }

  @Override
  public SecretManagerType getType() {
    return VAULT;
  }

  @Override
  public SecretManagerConfigDTO toDTO(boolean maskSecrets) {
    VaultConfigDTO ngVaultConfigDTO = VaultConfigDTO.builder()
                                          .encryptionType(getEncryptionType())
                                          .name(getName())
                                          .isDefault(isDefault())
                                          .isReadOnly(isReadOnly())
                                          .basePath(getBasePath())
                                          .secretEngineName(getSecretEngineName())
                                          .secretEngineVersion(getSecretEngineVersion())
                                          .renewalIntervalMinutes(getRenewalInterval())
                                          .vaultUrl(getVaultUrl())
                                          .engineManuallyEntered(isEngineManuallyEntered())
                                          .namespace(getNamespace())
                                          .appRoleId(getAppRoleId())
                                          .delegateSelectors(getDelegateSelectors())
                                          .build();
    SecretManagerConfigMapper.updateNGSecretManagerMetadata(getNgMetadata(), ngVaultConfigDTO);
    if (!maskSecrets) {
      ngVaultConfigDTO.setAuthToken(getAuthToken());
      ngVaultConfigDTO.setSecretId(getSecretId());
    }
    return ngVaultConfigDTO;
  }
}
