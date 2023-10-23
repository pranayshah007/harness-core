/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.servicenow;

import static java.util.Objects.isNull;

import io.harness.connector.entities.embedded.servicenow.ServiceNowConnector;
import io.harness.connector.entities.embedded.servicenow.ServiceNowUserNamePasswordAuthentication;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthType;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthenticationDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO.ServiceNowConnectorDTOBuilder;
import io.harness.encryption.SecretRefHelper;

public class ServiceNowEntityToDTO implements ConnectorEntityToDTOMapper<ServiceNowConnectorDTO, ServiceNowConnector> {
  @Override
  public ServiceNowConnectorDTO createConnectorDTO(ServiceNowConnector connector) {
    // no change required after ServiceNow ConnectorDisconnectHandler migration
    ServiceNowConnectorDTOBuilder serviceNowConnectorDTOBuilder =
        ServiceNowConnectorDTO.builder()
            .serviceNowUrl(connector.getServiceNowUrl())
            .username(connector.getUsername())
            .usernameRef(SecretRefHelper.createSecretRef(connector.getUsernameRef()))
            .passwordRef(SecretRefHelper.createSecretRef(connector.getPasswordRef()));
    if (!isNull(connector.getServiceNowAuthentication())) {
      serviceNowConnectorDTOBuilder.auth(
          ServiceNowAuthenticationDTO.builder()
              .authType(connector.getAuthType())
              .credentials(connector.getServiceNowAuthentication().toServiceNowAuthCredentialsDTO())
              .build());
      if (ServiceNowAuthType.USER_PASSWORD.equals(connector.getAuthType())) {
        // override old base level fields with value present in new ServiceNowAuthCredentials in USER_PASSWORD case
        ServiceNowUserNamePasswordAuthentication serviceNowUserNamePasswordAuthentication =
            (ServiceNowUserNamePasswordAuthentication) connector.getServiceNowAuthentication();
        serviceNowConnectorDTOBuilder.username(serviceNowUserNamePasswordAuthentication.getUsername())
            .usernameRef(SecretRefHelper.createSecretRef(serviceNowUserNamePasswordAuthentication.getUsernameRef()))
            .passwordRef(SecretRefHelper.createSecretRef(serviceNowUserNamePasswordAuthentication.getPasswordRef()));
      }
    }
    return serviceNowConnectorDTOBuilder.build();
  }
}
