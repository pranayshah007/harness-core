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
