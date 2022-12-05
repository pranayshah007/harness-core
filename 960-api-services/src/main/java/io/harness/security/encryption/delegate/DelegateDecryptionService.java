/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security.encryption.delegate;

import io.harness.security.encryption.common.EncryptedRecord;
import io.harness.security.encryption.common.EncryptionConfig;

import java.util.List;
import java.util.Map;

/**
 * Decrypt a batch of encrypted records. Return a map of the encrypted record UUID to the decrypted secret.
 */
public interface DelegateDecryptionService {
  Map<String, char[]> decrypt(Map<EncryptionConfig, List<EncryptedRecord>> encryptedRecordMap);
}
