package io.harness.connector.mappers.pcfmapper;

import io.harness.connector.entities.embedded.pcfconnector.PcfConfig;
import io.harness.connector.entities.embedded.pcfconnector.PcfManualCredential;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.pcfconnector.PcfConnectorDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfCredentialDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfCredentialType;
import io.harness.delegate.beans.connector.pcfconnector.PcfManualDetailsDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

public class PcfDTOToEntity implements ConnectorDTOToEntityMapper<PcfConnectorDTO, PcfConfig> {
  @Override
  public PcfConfig toConnectorEntity(PcfConnectorDTO connectorDTO) {
    final PcfCredentialDTO credential = connectorDTO.getCredential();
    final PcfCredentialType credentialType = credential.getType();
    final PcfConfig pcfConfig;
    if (credentialType == PcfCredentialType.MANUAL_CREDENTIALS) {
      pcfConfig = buildManualCredential(credential);
    } else {
      throw new InvalidRequestException("Invalid Credential type.");
    }

    return pcfConfig;
  }

  private PcfConfig buildManualCredential(PcfCredentialDTO pcfCredentialDTO) {
    final PcfManualDetailsDTO config = (PcfManualDetailsDTO) pcfCredentialDTO.getSpec();
    final String endpointUrl = config.getEndpointUrl();
    final String passwordRef = SecretRefHelper.getSecretConfigString(config.getPasswordRef());
    final String userName = SecretRefHelper.getSecretConfigString(config.getUserName());
    PcfManualCredential credential =
        PcfManualCredential.builder().endpointUrl(endpointUrl).userName(userName).passwordRef(passwordRef).build();
    return PcfConfig.builder().credentialType(PcfCredentialType.MANUAL_CREDENTIALS).credential(credential).build();
  }
}
