package io.harness.connector.task.tas;

import static io.harness.utils.FieldWithPlainTextOrSecretValueHelper.getValueFromPlainTextOrSecretRef;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialDTO;
import io.harness.delegate.beans.connector.tasconnector.TasCredentialType;
import io.harness.delegate.beans.connector.tasconnector.TasManualDetailsDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.pcf.model.CloudFoundryConfig;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class TasNgConfigMapper {
  @Inject private DecryptionHelper decryptionHelper;

  public CloudFoundryConfig mapTasConfigWithDecryption(
      TasConnectorDTO tasConnectorDTO, List<EncryptedDataDetail> encryptionDetails) {
    TasCredentialDTO credential = tasConnectorDTO.getCredential();
    TasCredentialType credentialType = credential.getType();
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().build();

    if (credentialType == TasCredentialType.MANUAL_CREDENTIALS) {
      TasManualDetailsDTO tasManualDetailsForDecryption = (TasManualDetailsDTO) credential.getSpec();
      TasManualDetailsDTO tasManualDetailsDTO =
          (TasManualDetailsDTO) decryptionHelper.decrypt(tasManualDetailsForDecryption, encryptionDetails);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(tasManualDetailsDTO, encryptionDetails);
      cfConfig.setEndpointUrl(reformatEndpointURL(tasManualDetailsDTO.getEndpointUrl()));
      cfConfig.setUserName(
          getValueFromPlainTextOrSecretRef(tasManualDetailsDTO.getUsername(), tasManualDetailsDTO.getUsernameRef()));
      cfConfig.setPassword(getDecryptedValueWithNullCheck(tasManualDetailsDTO.getPasswordRef()));
    } else {
      throw new IllegalStateException("Unexpected Tas credential type : " + credentialType);
    }
    return cfConfig;
  }

  private char[] getDecryptedValueWithNullCheck(SecretRefData passwordRef) {
    if (passwordRef != null) {
      return passwordRef.getDecryptedValue();
    }
    return null;
  }
  private String reformatEndpointURL(String endpointUrl) {
    int colonIndex = endpointUrl.indexOf("://");
    if (colonIndex > 0) {
      return endpointUrl.substring(colonIndex + 3);
    }
    return endpointUrl;
  }
}
