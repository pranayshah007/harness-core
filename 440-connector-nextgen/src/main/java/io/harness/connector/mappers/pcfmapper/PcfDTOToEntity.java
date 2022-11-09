package io.harness.connector.mappers.pcfmapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.pcfconnector.CloudFoundryConfig;
import io.harness.connector.entities.embedded.pcfconnector.PcfManualCredential;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.pcfconnector.PcfConnectorDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfCredentialDTO;
import io.harness.delegate.beans.connector.pcfconnector.PcfCredentialType;
import io.harness.delegate.beans.connector.pcfconnector.PcfManualDetailsDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class PcfDTOToEntity implements ConnectorDTOToEntityMapper<PcfConnectorDTO, CloudFoundryConfig> {
  @Override
  public CloudFoundryConfig toConnectorEntity(PcfConnectorDTO connectorDTO) {
    final PcfCredentialDTO credential = connectorDTO.getCredential();
    final PcfCredentialType credentialType = credential.getType();
    final CloudFoundryConfig cloudFoundryConfig;
    if (credentialType == PcfCredentialType.MANUAL_CREDENTIALS) {
      cloudFoundryConfig = buildManualCredential(credential);
    } else {
      throw new InvalidRequestException("Invalid Credential type.");
    }

    return cloudFoundryConfig;
  }

  private CloudFoundryConfig buildManualCredential(PcfCredentialDTO pcfCredentialDTO) {
    final PcfManualDetailsDTO config = (PcfManualDetailsDTO) pcfCredentialDTO.getSpec();
    final String endpointUrl = config.getEndpointUrl();
    final String passwordRef = SecretRefHelper.getSecretConfigString(config.getPasswordRef());
    final String usernameRef = SecretRefHelper.getSecretConfigString(config.getUsernameRef());
    final String username = config.getUsername();
    PcfManualCredential credential = PcfManualCredential.builder()
                                         .endpointUrl(endpointUrl)
                                         .userName(username)
                                         .userNameRef(usernameRef)
                                         .passwordRef(passwordRef)
                                         .build();
    return CloudFoundryConfig.builder()
        .credentialType(PcfCredentialType.MANUAL_CREDENTIALS)
        .credential(credential)
        .build();
  }
}
