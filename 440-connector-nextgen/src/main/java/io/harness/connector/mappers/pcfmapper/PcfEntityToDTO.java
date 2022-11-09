package io.harness.connector.mappers.pcfmapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.pcfconnector.CloudFoundryConfig;
import io.harness.connector.entities.embedded.pcfconnector.PcfManualCredential;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.pcfconnector.PcfConnectorDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfCredentialDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfCredentialType;
import io.harness.delegate.beans.connector.pcfconnector.PcfManualDetailsDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class PcfEntityToDTO implements ConnectorEntityToDTOMapper<PcfConnectorDTO, CloudFoundryConfig> {
  @Override
  public PcfConnectorDTO createConnectorDTO(CloudFoundryConfig connector) {
    final PcfCredentialType credentialType = connector.getCredentialType();
    if (credentialType == PcfCredentialType.MANUAL_CREDENTIALS) {
      return buildManualCredential(connector);
    }
    throw new InvalidRequestException("Invalid Credential type.");
  }

  private PcfConnectorDTO buildManualCredential(CloudFoundryConfig connector) {
    PcfManualCredential pcfCredential = (PcfManualCredential) connector.getCredential();
    PcfCredentialDTO pcfCredentialDTO =
        PcfCredentialDTO.builder()
            .type(PcfCredentialType.MANUAL_CREDENTIALS)
            .spec(PcfManualDetailsDTO.builder()
                      .endpointUrl(pcfCredential.getEndpointUrl())
                      .username(pcfCredential.getUserName())
                      .usernameRef(SecretRefHelper.createSecretRef(pcfCredential.getUserNameRef()))
                      .passwordRef(SecretRefHelper.createSecretRef(pcfCredential.getPasswordRef()))
                      .build())
            .build();
    return PcfConnectorDTO.builder().credential(pcfCredentialDTO).build();
  }
}
