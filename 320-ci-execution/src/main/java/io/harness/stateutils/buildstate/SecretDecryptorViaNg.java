package io.harness.stateutils.buildstate;

import io.harness.beans.DecryptableEntity;
import io.harness.secrets.SecretDecryptor;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.List;

public class SecretDecryptorViaNg implements SecretDecryptor {
  @Inject private SecretUtils secretUtils;

  public DecryptableEntity decrypt(
      DecryptableEntity object, List<EncryptedDataDetail> encryptedDataDetails, String accountId) {
    return secretUtils.decryptViaManager(object, encryptedDataDetails, accountId, null);
  }
}
