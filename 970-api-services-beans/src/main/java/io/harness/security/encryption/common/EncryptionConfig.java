/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security.encryption.common;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

@TargetModule(HarnessModule._980_COMMONS)
public interface EncryptionConfig {
  /**
   * Return the UUID of this secret manager
   */
  String getUuid();

  /**
   * Return the name of this secret manager
   */
  String getName();

  /**
   * Get the account Id of this secret manager. For global default secret manager, the account Id could be of value
   * '__GLOBAL_ACCOUNT_ID__'.
   */
  String getAccountId();

  /**
   * Get the encryption type of this secret manager.
   */
  EncryptionType getEncryptionType();

  /**
   * Set the encryption type of this secret manager.
   */
  void setEncryptionType(EncryptionType encryptionType);

  /**
   * Retrieve if the current secret manager was set as default or not.
   */
  boolean isDefault();

  /**
   * Set or unset the current secret manager as default secret manager.
   */
  void setDefault(boolean isDefault);

  /**
   * Get the number of secrets encrypted using this secret manager.
   */
  int getNumOfEncryptedValue();

  /**
   * Get this secret manager's encryption service URL.
   */
  String getEncryptionServiceUrl();

  /**
   * Get the validation criteria string.
   */
  String getValidationCriteria();

  /**
   * Get the type of secret manager.
   */
  SecretManagerType getType();

  /**
   * Return true if this secret manager is associated with __GLOBAL_ACCOUNT_ID__ is of type KMS.
   */
  boolean isGlobalKms();
}
