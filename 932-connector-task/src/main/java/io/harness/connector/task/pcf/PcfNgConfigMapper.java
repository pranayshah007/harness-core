package io.harness.connector.task.pcf;

import static io.harness.utils.FieldWithPlainTextOrSecretValueHelper.getValueFromPlainTextOrSecretRef;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.pcfconnector.PcfConnectorDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfCredentialDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfCredentialType;
import io.harness.delegate.beans.connector.pcfconnector.PcfManualDetailsDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.pcf.model.CloudFoundryConfig;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class PcfNgConfigMapper {
  @Inject private DecryptionHelper decryptionHelper;

  public CloudFoundryConfig mapPcfConfigWithDecryption(
      PcfConnectorDTO pcfConnectorDTO, List<EncryptedDataDetail> encryptionDetails) {
    PcfCredentialDTO credential = pcfConnectorDTO.getCredential();
    PcfCredentialType credentialType = credential.getType();
    CloudFoundryConfig pcfConfig = CloudFoundryConfig.builder().build();

    if (credentialType == PcfCredentialType.MANUAL_CREDENTIALS) {
      PcfManualDetailsDTO pcfManualDetailsForDecryption = (PcfManualDetailsDTO) credential.getSpec();
      PcfManualDetailsDTO pcfManualDetailsDTO =
          (PcfManualDetailsDTO) decryptionHelper.decrypt(pcfManualDetailsForDecryption, encryptionDetails);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(pcfManualDetailsDTO, encryptionDetails);
      pcfConfig.setEndpointUrl(pcfManualDetailsDTO.getEndpointUrl());
      pcfConfig.setUserName(
          getValueFromPlainTextOrSecretRef(pcfManualDetailsDTO.getUsername(), pcfManualDetailsDTO.getUsernameRef()));
      pcfConfig.setPassword(getDecryptedValueWithNullCheck(pcfManualDetailsDTO.getPasswordRef()));
    } else {
      throw new IllegalStateException("Unexpected Pcf credential type : " + credentialType);
    }
    return pcfConfig;
  }

  private char[] getDecryptedValueWithNullCheck(SecretRefData passwordRef) {
    if (passwordRef != null) {
      return passwordRef.getDecryptedValue();
    }
    return null;
  }
}
