package io.harness.connector.mappers.servicenow;

import io.harness.connector.entities.embedded.servicenow.ServiceNowConnector;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.encryption.SecretRefHelper;

public class ServiceNowDTOtoEntity implements ConnectorDTOToEntityMapper<ServiceNowConnectorDTO, ServiceNowConnector> {
  @Override
  public ServiceNowConnector toConnectorEntity(ServiceNowConnectorDTO configDTO) {
    return ServiceNowConnector.builder()
        .serviceNowUrl(configDTO.getServiceNowUrl())
        .username(configDTO.getUsername())
        .usernameRef(SecretRefHelper.getSecretConfigString(configDTO.getUsernameRef()))
        .passwordRef(SecretRefHelper.getSecretConfigString(configDTO.getPasswordRef()))
        .build();
  }
}
