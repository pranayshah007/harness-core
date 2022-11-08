package io.harness.connector.mappers.pcfmapper;

import io.harness.connector.entities.embedded.pcfconnector.PcfConfig;
import io.harness.connector.entities.embedded.pcfconnector.PcfManualCredential;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.pcfconnector.PcfConnectorDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfCredentialDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfCredentialType;
import io.harness.delegate.beans.connector.pcfconnector.PcfManualDetailsDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

public class PcfEntityToDTO implements ConnectorEntityToDTOMapper<PcfConnectorDTO, PcfConfig> {
  @Override
  public PcfConnectorDTO createConnectorDTO(PcfConfig connector) {
    final PcfCredentialType credentialType = connector.getCredentialType();
    if (credentialType == PcfCredentialType.MANUAL_CREDENTIALS) {
      return buildManualCredential(connector);
    }
    throw new InvalidRequestException("Invalid Credential type.");
  }

  private PcfConnectorDTO buildManualCredential(PcfConfig connector) {
    PcfManualCredential pcfCredential = (PcfManualCredential) connector.getCredential();
    PcfCredentialDTO pcfCredentialDTO =
        PcfCredentialDTO.builder()
            .type(PcfCredentialType.MANUAL_CREDENTIALS)
            .spec(PcfManualDetailsDTO.builder()
                      .endpointUrl(pcfCredential.getEndpointUrl())
                      .userName(SecretRefHelper.createSecretRef(pcfCredential.getUserName()))
                      .passwordRef(SecretRefHelper.createSecretRef(pcfCredential.getPasswordRef()))
                      .build())
            .build();
    return PcfConnectorDTO.builder().credential(pcfCredentialDTO).build();
  }
}
