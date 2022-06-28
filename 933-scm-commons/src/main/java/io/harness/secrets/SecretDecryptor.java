/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets;

import io.harness.beans.DecryptableEntity;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;

public interface SecretDecryptor {
  DecryptableEntity decrypt(DecryptableEntity object, List<EncryptedDataDetail> encryptedDataDetails, String accountId);
}
