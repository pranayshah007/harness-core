/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secretmanagers;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretManagerConfig;
import io.harness.security.encryption.common.EncryptionType;

import software.wings.security.UsageRestrictions;

import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by mark.lu on 5/31/2019.
 */
@OwnedBy(PL)
public interface SecretManagerConfigService {
  String save(SecretManagerConfig secretManagerConfig);

  boolean delete(@NotEmpty String accountId, @NotNull SecretManagerConfig secretManagerConfig);

  void clearDefaultFlagOfSecretManagers(String accountId);

  String getSecretManagerName(@NotEmpty String kmsId, @NotEmpty String accountId);

  EncryptionType getEncryptionType(@NotEmpty String accountId);

  EncryptionType getEncryptionBySecretManagerId(@NotEmpty String kmsId, @NotEmpty String accountId);

  List<SecretManagerConfig> listSecretManagers(String accountId, boolean maskSecret);

  List<SecretManagerConfig> listSecretManagers(
      String accountId, boolean maskSecret, boolean includeGlobalSecretManager);

  List<SecretManagerConfig> listSecretManagersByType(
      String accountId, EncryptionType encryptionType, boolean maskSecret);

  SecretManagerConfig getDefaultSecretManager(String accountId);

  SecretManagerConfig getGlobalSecretManager(String accountId);

  SecretManagerConfig getSecretManager(String accountId, String entityId);

  SecretManagerConfig getSecretManager(String accountId, String kmsId, EncryptionType encryptionType);

  SecretManagerConfig getSecretManager(
      String accountId, String kmsId, EncryptionType encryptionType, Map<String, String> runtimeParameters);

  SecretManagerConfig getSecretManager(String accountId, String entityId, boolean maskSecrets);

  SecretManagerConfig getSecretManagerByName(String accountId, String name);

  SecretManagerConfig getSecretManagerByName(
      String accountId, String entityName, EncryptionType encryptionType, boolean maskSecrets);

  List<Integer> getCountOfSecretManagersForAccounts(List<String> accountIds, boolean includeGlobalSecretManager);

  void decryptEncryptionConfigSecrets(String accountId, SecretManagerConfig secretManagerConfig, boolean maskSecrets);

  SecretManagerConfig updateRuntimeParameters(
      SecretManagerConfig secretManagerConfig, Map<String, String> runtimeParameters, boolean shouldUpdateVaultConfig);

  void updateUsageRestrictions(String accountId, String secretManagerId, UsageRestrictions usageRestrictions);

  UsageRestrictions getMaximalAllowedScopes(String accountId, String secretsManagerId);

  void canTransitionSecrets(
      @NotEmpty String accountId, @NotNull SecretManagerConfig fromConfig, @NotNull SecretManagerConfig toConfig);
}
