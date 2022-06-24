package io.harness.secrets;

import io.harness.beans.DecryptableEntity;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;

public interface SecretDecryptor {
  DecryptableEntity decrypt(DecryptableEntity object, List<EncryptedDataDetail> encryptedDataDetails, String accountId);
}
