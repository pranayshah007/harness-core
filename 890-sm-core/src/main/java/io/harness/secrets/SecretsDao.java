/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SecretUpdateData;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionType;

import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.UpdateOperations;
import java.util.Optional;
import java.util.Set;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(PL)
public interface SecretsDao {
  String ID_KEY = "_id";

  Optional<EncryptedData> getSecretById(@NotEmpty String accountId, @NotEmpty String secretId);

  Optional<EncryptedData> getSecretByName(@NotEmpty String accountId, @NotEmpty String secretName);

  Optional<EncryptedData> getSecretByKeyOrPath(
      @NotEmpty String accountId, @NotNull EncryptionType encryptionType, String key, String path);

  String saveSecret(@NotNull EncryptedData encryptedData);

  EncryptedData updateSecret(@NotNull SecretUpdateData secretUpdateData, EncryptedRecord updatedEncryptedData);

  EncryptedData migrateSecret(
      @NotEmpty String accountId, @NotEmpty String secretId, @NotNull EncryptedRecord encryptedRecord);

  boolean deleteSecret(@NotEmpty String accountId, @NotEmpty String secretId);

  PageResponse<EncryptedData> listSecrets(PageRequest<EncryptedData> pageRequest);

  MorphiaIterator<EncryptedData, EncryptedData> listSecretsBySecretManager(
      @NotEmpty String accountId, @NotEmpty String secretManagerId, boolean shouldIncludeSecretManagerSecrets);

  MorphiaIterator<EncryptedData, EncryptedData> listSecretsBySecretIds(
      @NotEmpty String accountId, @NotNull Set<String> secretIds);

  void updateSecret(@NotNull EncryptedData encryptedData, @NotNull UpdateOperations<EncryptedData> updateOperations);

  UpdateOperations<EncryptedData> getUpdateOperations();
}
