/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors;

import io.harness.security.encryption.EncryptionConfig;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang3.tuple.Pair;

public interface NgCgManagerVaultEncryptor extends VaultEncryptor {
  Pair<String, Boolean> validateSecretManagerConfigurationWithTaskId(
      @NotEmpty String accountId, @NotNull EncryptionConfig encryptionConfig);
}
