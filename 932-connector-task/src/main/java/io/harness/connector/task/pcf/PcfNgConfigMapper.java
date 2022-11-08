package io.harness.connector.task.pcf;

import static java.lang.String.format;

import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.pcfconnector.PcfConnectorDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfCredentialDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfCredentialType;
import io.harness.delegate.beans.connector.pcfconnector.PcfManualDetailsDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.pcf.model.PcfConfig;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.List;

public class PcfNgConfigMapper {
  @Inject private DecryptionHelper decryptionHelper;

  public PcfConfig mapPcfConfigWithDecryption(
      PcfConnectorDTO pcfConnectorDTO, List<EncryptedDataDetail> encryptionDetails) {
    PcfCredentialDTO credential = pcfConnectorDTO.getCredential();
    PcfCredentialType credentialType = credential.getType();
    PcfConfig pcfConfig = PcfConfig.builder().build();

    if (credentialType == PcfCredentialType.MANUAL_CREDENTIALS) {
      PcfManualDetailsDTO pcfManualDetailsForDecryption = (PcfManualDetailsDTO) credential.getSpec();
      PcfManualDetailsDTO pcfManualDetailsDTO =
          (PcfManualDetailsDTO) decryptionHelper.decrypt(pcfManualDetailsForDecryption, encryptionDetails);
      pcfConfig.setEndpointUrl(pcfManualDetailsDTO.getEndpointUrl());
      pcfConfig.setUserName(getDecryptedValueWithNullCheck(pcfManualDetailsDTO.getUserName()));
      pcfConfig.setPasswordRef(getDecryptedValueWithNullCheck(pcfManualDetailsDTO.getPasswordRef()));
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
