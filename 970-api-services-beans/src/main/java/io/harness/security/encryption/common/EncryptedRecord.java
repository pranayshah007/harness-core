/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security.encryption.common;

import java.util.Set;

/**
 * An interface to abstract the basic information available from an encrypted record.
 *
 * @author marklu on 2019-02-04
 */
public interface EncryptedRecord {
  String getUuid();
  String getName();
  String getPath(); // Only relevant if this is a record encrypted by Vault.
  Set<EncryptedDataParams> getParameters();
  String getEncryptionKey();
  char[] getEncryptedValue();
  String getKmsId();
  EncryptionType getEncryptionType();
  char[] getBackupEncryptedValue(); // Only relevant in case of GLOBAL secret manager.
  String getBackupEncryptionKey(); // Only relevant in case of GLOBAL secret manager.
  String getBackupKmsId(); // Only relevant in case of GLOBAL secret manager.
  EncryptionType getBackupEncryptionType(); // Only relevant in case of GLOBAL secret manager.
  boolean isBase64Encoded();
  AdditionalMetadata getAdditionalMetadata();
}
