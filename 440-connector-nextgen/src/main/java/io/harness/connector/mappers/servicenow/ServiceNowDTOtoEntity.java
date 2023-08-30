/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.servicenow;
import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.entities.embedded.servicenow.ServiceNowADFSAuthentication;
import io.harness.connector.entities.embedded.servicenow.ServiceNowConnector;
import io.harness.connector.entities.embedded.servicenow.ServiceNowConnector.ServiceNowConnectorBuilder;
import io.harness.connector.entities.embedded.servicenow.ServiceNowRefreshTokenAuthentication;
import io.harness.connector.entities.embedded.servicenow.ServiceNowUserNamePasswordAuthentication;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.servicenow.ServiceNowADFSDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthType;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowRefreshTokenDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowUserNamePasswordDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
public class ServiceNowDTOtoEntity implements ConnectorDTOToEntityMapper<ServiceNowConnectorDTO, ServiceNowConnector> {
  @Override
  public ServiceNowConnector toConnectorEntity(ServiceNowConnectorDTO configDTO) {
    // no change required after ServiceNow connector migration
    ServiceNowConnectorBuilder serviceNowConnectorBuilder =
        ServiceNowConnector.builder()
            .serviceNowUrl(configDTO.getServiceNowUrl())
            .username(configDTO.getUsername())
            .usernameRef(SecretRefHelper.getSecretConfigString(configDTO.getUsernameRef()))
            .passwordRef(SecretRefHelper.getSecretConfigString(configDTO.getPasswordRef()));

    if (!isNull(configDTO.getAuth())) {
      serviceNowConnectorBuilder.authType(configDTO.getAuth().getAuthType());

      if (ServiceNowAuthType.USER_PASSWORD.equals(configDTO.getAuth().getAuthType())) {
        // override old base level fields with value present in new ServiceNowAuthCredentials in USER_PASSWORD case
        ServiceNowUserNamePasswordDTO serviceNowUserNamePasswordDTO =
            (ServiceNowUserNamePasswordDTO) configDTO.getAuth().getCredentials();
        serviceNowConnectorBuilder.username(serviceNowUserNamePasswordDTO.getUsername())
            .usernameRef(SecretRefHelper.getSecretConfigString(serviceNowUserNamePasswordDTO.getUsernameRef()))
            .passwordRef(SecretRefHelper.getSecretConfigString(serviceNowUserNamePasswordDTO.getPasswordRef()))
            .serviceNowAuthentication(ServiceNowUserNamePasswordAuthentication.fromServiceNowAuthCredentialsDTO(
                serviceNowUserNamePasswordDTO));
      } else if (ServiceNowAuthType.ADFS.equals(configDTO.getAuth().getAuthType())) {
        ServiceNowADFSDTO serviceNowADFSDTO = (ServiceNowADFSDTO) configDTO.getAuth().getCredentials();
        serviceNowConnectorBuilder.serviceNowAuthentication(
            ServiceNowADFSAuthentication.fromServiceNowAuthCredentialsDTO(serviceNowADFSDTO));
      } else if (ServiceNowAuthType.REFRESH_TOKEN.equals(configDTO.getAuth().getAuthType())) {
        ServiceNowRefreshTokenDTO serviceNowRefreshTokenDTO =
            (ServiceNowRefreshTokenDTO) configDTO.getAuth().getCredentials();
        serviceNowConnectorBuilder.serviceNowAuthentication(
            ServiceNowRefreshTokenAuthentication.fromServiceNowAuthCredentialsDTO(serviceNowRefreshTokenDTO));
      } else {
        throw new InvalidRequestException(
            String.format("Unsupported servicenow auth type provided : %s", configDTO.getAuth().getAuthType()));
      }
    }
    return serviceNowConnectorBuilder.build();
  }
}
