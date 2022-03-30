/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.HarnessSecret;
import io.harness.beans.MigrateSecretTask;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretMetadata;
import io.harness.security.encryption.EncryptionType;

import software.wings.security.UsageRestrictions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.PL)
public interface SecretService {
  EncryptedData createSecret(@NotEmpty String accountId, @NotNull HarnessSecret secret, boolean validateScopes);
  EncryptedData encryptSecret(@NotEmpty String accountId, @NotNull HarnessSecret secret, boolean validateScopes);
  boolean updateSecret(@NotEmpty String accountId, @NotNull HarnessSecret secret, @NotEmpty String existingRecordId,
      boolean validateScopes);
  boolean updateSecretScopes(@NotEmpty String accountId, @NotEmpty String existingRecordId,
      UsageRestrictions usageRestrictions, boolean scopedToAccount, boolean inheritScopesFromSM);
  void updateConflictingSecretsToInheritScopes(
      @NotEmpty String accountId, @NotNull SecretManagerConfig secretManagerConfig);
  boolean deleteSecret(@NotEmpty String accountId, @NotEmpty String secretRecordId, boolean validateScopes,
      Map<String, String> runtimeParameters);
  boolean migrateSecrets(
      @NotEmpty String accountId, @NotNull SecretManagerConfig fromConfig, @NotNull SecretManagerConfig toConfig);
  void migrateSecret(@NotNull MigrateSecretTask migrateSecretTask);
  char[] fetchSecretValue(@NotNull EncryptedData encryptedData);
  Optional<EncryptedData> getSecretById(@NotEmpty String accountId, @NotEmpty String secretRecordId);
  Optional<EncryptedData> getSecretById(
      @NotEmpty String accountId, @NotEmpty String secretRecordId, @NotEmpty String appId, String envId);
  Optional<EncryptedData> getAccountScopedSecretById(@NotEmpty String accountId, @NotEmpty String secretRecordId);

  Optional<EncryptedData> getSecretByName(@NotEmpty String accountId, @NotEmpty String secretName);
  Optional<EncryptedData> getSecretByName(
      @NotEmpty String accountId, @NotEmpty String secretName, @NotEmpty String appId, String envId);
  Optional<EncryptedData> getAccountScopedSecretByName(@NotEmpty String accountId, @NotEmpty String secretName);

  Optional<EncryptedData> getSecretByKeyOrPath(
      @NotEmpty String accountId, @NotNull EncryptionType encryptionType, String encryptionKey, String path);

  boolean hasAccessToReadSecrets(
      @NotEmpty String accountId, @NotNull Set<String> secretIds, String appId, String envId);
  boolean hasAccessToEditSecrets(@NotEmpty String accountId, @NotNull Set<String> secretIds);

  PageResponse<EncryptedData> listSecrets(
      @NotEmpty String accountId, @NotNull PageRequest<EncryptedData> pageRequest, String appId, String envId);
  PageResponse<EncryptedData> listSecretsScopedToAccount(
      @NotEmpty String accountId, @NotNull PageRequest<EncryptedData> pageRequest);

  List<SecretMetadata> filterSecretIdsByReadPermission(Set<String> secretIds, String accountId, String appIdFromRequest,
      String envIdFromRequest, boolean forUsageInNewApp);
}
