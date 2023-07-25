/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.serviceNow;

import static io.harness.utils.IdentifierRefHelper.getIdentifierRef;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthCredentialsDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.DecryptableEntityWithEncryptionConsumers;
import io.harness.ng.core.NGAccessWithEncryptionConsumer;
import io.harness.remote.client.NGRestUtils;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.servicenow.ServiceNowTicketNG;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CCMServiceNowHelperImpl implements CCMServiceNowHelper {
  @Inject private ConnectorResourceClient connectorResourceClient;
  @Inject @Named("PRIVILEGED") private SecretNGManagerClient secretManagerClient;
  @Inject private CCMServiceNowUtils serviceNowUtils;
  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public ServiceNowTicketNG createIssue(
      String accountId, String servicenowConnectorRef, String ticketType, Map<String, String> fields) {
    IdentifierRef connectorRef = getIdentifierRef(servicenowConnectorRef, accountId, "default", null);
    ServiceNowConnectorDTO serviceNowConnectorDTO = getConnector(connectorRef);
    ServiceNowTaskNGParameters parameters = ServiceNowTaskNGParameters.builder()
                                                .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                .ticketType(ticketType)
                                                .fields(fields)
                                                .build();
    return serviceNowUtils.createTicket(parameters);
  }

  @Override
  public ServiceNowTicketNG getIssue(
      String accountId, String servicenowConnectorRef, String ticketType, String ticketNumber) {
    IdentifierRef connectorRef = getIdentifierRef(servicenowConnectorRef, accountId, "default", null);
    ServiceNowConnectorDTO serviceNowConnectorDTO = getConnector(connectorRef);
    ServiceNowTaskNGParameters parameters = ServiceNowTaskNGParameters.builder()
                                                .serviceNowConnectorDTO(serviceNowConnectorDTO)
                                                .ticketType(ticketType)
                                                .ticketNumber(ticketNumber)
                                                .build();
    return serviceNowUtils.getTicket(parameters);
  }

  private ServiceNowConnectorDTO getConnector(IdentifierRef serviceNowConnectorRef) {
    ConnectorConfigDTO connectorConfigDTO;
    try {
      // Returns encrypted Jira Connector
      Optional<ConnectorDTO> connector = NGRestUtils.getResponse(connectorResourceClient.get(
          serviceNowConnectorRef.getIdentifier(), serviceNowConnectorRef.getAccountIdentifier(),
          serviceNowConnectorRef.getOrgIdentifier(), serviceNowConnectorRef.getProjectIdentifier()));
      if (connector.isEmpty() || !isServiceNowConnector(connector.get().getConnectorInfo())) {
        throw new InvalidRequestException(
            String.format("Servicenow connector not found for identifier : [%s] with scope: [%s]",
                serviceNowConnectorRef.getIdentifier(), serviceNowConnectorRef.getScope()),
            WingsException.USER);
      }
      connectorConfigDTO = connector.get().getConnectorInfo().getConnectorConfig();
    } catch (Exception e) {
      throw new NotFoundException(
          format("Error while getting connector information : [%s]", serviceNowConnectorRef.getIdentifier()), e);
    }
    // Get Encryption Details
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(serviceNowConnectorRef, connectorConfigDTO);
    // Decrypt connector using Encryption Details
    connectorConfigDTO = decryptServiceNowConnectorDTO(
        (ServiceNowConnectorDTO) connectorConfigDTO, encryptionDetails, serviceNowConnectorRef.getAccountIdentifier());
    return (ServiceNowConnectorDTO) connectorConfigDTO;
  }

  private List<EncryptedDataDetail> getEncryptionDetails(
      IdentifierRef serviceNowConnectorRef, ConnectorConfigDTO connectorConfigDTO) {
    ServiceNowConnectorDTO serviceNowConnectorDTO = (ServiceNowConnectorDTO) connectorConfigDTO;
    BaseNGAccess baseNGAccess = getBaseNGAccess(serviceNowConnectorRef);
    if (!isNull(serviceNowConnectorDTO.getAuth()) && !isNull(serviceNowConnectorDTO.getAuth().getCredentials())) {
      return NGRestUtils.getResponse(
          secretManagerClient.getEncryptionDetails(serviceNowConnectorRef.getAccountIdentifier(),
              NGAccessWithEncryptionConsumer.builder()
                  .ngAccess(baseNGAccess)
                  .decryptableEntity(serviceNowConnectorDTO.getAuth().getCredentials())
                  .build()));
    }
    return NGRestUtils.getResponse(secretManagerClient.getEncryptionDetails(
        serviceNowConnectorRef.getAccountIdentifier(),
        NGAccessWithEncryptionConsumer.builder().ngAccess(baseNGAccess).decryptableEntity(connectorConfigDTO).build()));
  }

  private ServiceNowConnectorDTO decryptServiceNowConnectorDTO(
      ServiceNowConnectorDTO dto, List<EncryptedDataDetail> encryptionDetails, String accountIdentifier) {
    if (!isNull(dto.getAuth()) && !isNull(dto.getAuth().getCredentials())) {
      ServiceNowAuthCredentialsDTO decryptedEntity = (ServiceNowAuthCredentialsDTO) NGRestUtils.getResponse(
          secretManagerClient.decryptEncryptedDetails(DecryptableEntityWithEncryptionConsumers.builder()
                                                          .decryptableEntity(dto.getAuth().getCredentials())
                                                          .encryptedDataDetailList(encryptionDetails)
                                                          .build(),
              accountIdentifier));
      dto.getAuth().setCredentials(decryptedEntity);
      return dto;
    } else {
      return (ServiceNowConnectorDTO) NGRestUtils.getResponse(
          secretManagerClient.decryptEncryptedDetails(DecryptableEntityWithEncryptionConsumers.builder()
                                                          .decryptableEntity(dto)
                                                          .encryptedDataDetailList(encryptionDetails)
                                                          .build(),
              accountIdentifier));
    }
  }

  private BaseNGAccess getBaseNGAccess(IdentifierRef ref) {
    return BaseNGAccess.builder()
        .accountIdentifier(ref.getAccountIdentifier())
        .orgIdentifier(ref.getOrgIdentifier())
        .projectIdentifier(ref.getProjectIdentifier())
        .build();
  }

  private boolean isServiceNowConnector(ConnectorInfoDTO connector) {
    return ConnectorType.SERVICENOW == connector.getConnectorType();
  }
}
