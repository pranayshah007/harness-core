/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.beans.SecretManagerCapabilities.CAN_BE_DEFAULT_SM;
import static io.harness.beans.SecretManagerCapabilities.CREATE_FILE_SECRET;
import static io.harness.beans.SecretManagerCapabilities.CREATE_INLINE_SECRET;
import static io.harness.helpers.GlobalSecretManagerUtils.isNgHarnessSecretManager;
import static io.harness.mappers.SecretManagerConfigMapper.updateNGSecretManagerMetadata;
import static io.harness.security.encryption.SecretManagerType.KMS;

import io.harness.beans.SecretManagerCapabilities;
import io.harness.beans.SecretManagerConfig;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.expression.ExpressionEvaluator;
import io.harness.secretmanagerclient.dto.LocalConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.security.encryption.EncryptionType;
import io.harness.security.encryption.SecretManagerType;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * When no other secret manager is configured. LOCAL encryption secret manager will be the default.
 * This entity don't need to be persisted in MongoDB.
 *
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class LocalEncryptionConfig extends SecretManagerConfig {
  public static final String HARNESS_DEFAULT_SECRET_MANAGER = "Harness Secrets Manager";
  @Builder.Default private String name = HARNESS_DEFAULT_SECRET_MANAGER;

  @Override
  public String getEncryptionServiceUrl() {
    return null;
  }

  @Override
  public String getValidationCriteria() {
    return "encryption type: " + EncryptionType.LOCAL;
  }

  @Override
  public EncryptionType getEncryptionType() {
    return EncryptionType.LOCAL;
  }

  @Override
  public void maskSecrets() {}

  @Override
  public boolean isGlobalKms() {
    return isNgHarnessSecretManager(getNgMetadata());
  }

  @Override
  public List<SecretManagerCapabilities> getSecretManagerCapabilities() {
    return Lists.newArrayList(CREATE_INLINE_SECRET, CREATE_FILE_SECRET, CAN_BE_DEFAULT_SM);
  }

  @Override
  public SecretManagerType getType() {
    return KMS;
  }

  @Override
  public SecretManagerConfigDTO toDTO(boolean maskSecrets) {
    LocalConfigDTO localConfigDTO =
        LocalConfigDTO.builder().name(getName()).isDefault(isDefault()).encryptionType(getEncryptionType()).build();
    updateNGSecretManagerMetadata(getNgMetadata(), localConfigDTO);
    return localConfigDTO;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Collections.emptyList();
  }
}
