package io.harness.delegate.task.pcf;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.pcfconnector.PcfConnectorDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfCredentialType;
import io.harness.delegate.beans.connector.pcfconnector.PcfManualDetailsDTO;
import io.harness.delegate.task.pcf.response.PcfInfraConfig;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class PcfInfraConfigHelper {
  @Inject private SecretDecryptionService secretDecryptionService;
  public void decryptPcfInfraConfig(PcfInfraConfig pcfInfraConfig) {
    PcfConnectorDTO pcfConnectorDTO = pcfInfraConfig.getPcfConnectorDTO();
    List<EncryptedDataDetail> encryptedDataDetails = pcfInfraConfig.getEncryptionDataDetails();
    if (pcfConnectorDTO.getCredential().getType() == PcfCredentialType.MANUAL_CREDENTIALS) {
      PcfManualDetailsDTO pcfManualDetailsDTO = (PcfManualDetailsDTO) pcfConnectorDTO.getCredential().getSpec();
      secretDecryptionService.decrypt(pcfManualDetailsDTO, encryptedDataDetails);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(pcfManualDetailsDTO, encryptedDataDetails);
    }
  }
}
