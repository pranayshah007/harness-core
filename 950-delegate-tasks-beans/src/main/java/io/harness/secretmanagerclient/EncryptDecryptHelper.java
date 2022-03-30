/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.secretmanagerclient;

import io.harness.delegate.beans.DelegateFile;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;

import java.io.IOException;

public interface EncryptDecryptHelper {
  EncryptedRecord encryptContent(byte[] content, String name, EncryptionConfig config);

  EncryptedRecord encryptFile(byte[] content, String name, EncryptionConfig config, DelegateFile delegateFile)
      throws IOException;

  byte[] getDecryptedContent(EncryptionConfig config, EncryptedRecord record);

  byte[] getDecryptedContent(EncryptionConfig config, EncryptedRecord record, String accountId) throws IOException;

  boolean deleteEncryptedRecord(EncryptionConfig encryptionConfig, EncryptedRecord record);
}
