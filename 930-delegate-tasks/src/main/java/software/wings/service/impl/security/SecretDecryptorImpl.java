/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import io.harness.beans.DecryptableEntity;
import io.harness.secrets.SecretDecryptor;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import java.util.List;

public class SecretDecryptorImpl implements SecretDecryptor {
  @Inject private SecretDecryptionService secretDecryptionService;

  public DecryptableEntity decrypt(
      DecryptableEntity object, List<EncryptedDataDetail> encryptedDataDetails, String accountId) {
    return secretDecryptionService.decrypt(object, encryptedDataDetails);
  }
}
