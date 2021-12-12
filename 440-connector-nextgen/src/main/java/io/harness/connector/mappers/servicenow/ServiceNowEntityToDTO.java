package io.harness.connector.mappers.servicenow;

import io.harness.connector.entities.embedded.servicenow.ServiceNowConnector;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.encryption.SecretRefHelper;

public class ServiceNowEntityToDTO implements ConnectorEntityToDTOMapper<ServiceNowConnectorDTO, ServiceNowConnector> {
  @Override
  public ServiceNowConnectorDTO createConnectorDTO(ServiceNowConnector connector) {
    return ServiceNowConnectorDTO.builder()
        .serviceNowUrl(connector.getServiceNowUrl())
        .username(connector.getUsername())
        .usernameRef(SecretRefHelper.createSecretRef(connector.getUsernameRef()))
        .passwordRef(SecretRefHelper.createSecretRef(connector.getPasswordRef()))
        .build();
  }
}
