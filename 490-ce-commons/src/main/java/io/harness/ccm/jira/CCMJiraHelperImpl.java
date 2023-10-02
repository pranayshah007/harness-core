/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.jira;

import static io.harness.utils.IdentifierRefHelper.getIdentifierRef;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.jira.JiraAuthCredentialsDTO;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.task.jira.mappers.JiraRequestResponseMapper;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HttpResponseException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.jira.JiraClient;
import io.harness.jira.JiraIssueNG;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.DecryptableEntityWithEncryptionConsumers;
import io.harness.ng.core.NGAccessWithEncryptionConsumer;
import io.harness.remote.client.NGRestUtils;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CCMJiraHelperImpl implements CCMJiraHelper {
  @Inject private ConnectorResourceClient connectorResourceClient;
  @Inject @Named("PRIVILEGED") private SecretNGManagerClient secretManagerClient;
  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public JiraIssueNG createIssue(
      String accountId, String jiraConnectorRef, String projectKey, String issueType, Map<String, String> fields) {
    try {
      IdentifierRef connectorRef = getIdentifierRef(jiraConnectorRef, accountId, "default", null);
      JiraConnectorDTO jiraConnectorDTO = getConnector(connectorRef);
      JiraClient jiraClient = getNGJiraClient(jiraConnectorDTO);
      return jiraClient.createIssue(projectKey, issueType, fields, false, false, false);
    } catch (Exception e) {
      log.warn("logging jira error: ", e);
      throw new InvalidRequestException(getJiraException(e));
    }
  }

  @Override
  public JiraIssueNG getIssue(String accountId, String jiraConnectorRef, String issueKey) {
    try {
      IdentifierRef connectorRef = getIdentifierRef(jiraConnectorRef, accountId, "default", null);
      JiraConnectorDTO jiraConnectorDTO = getConnector(connectorRef);
      JiraClient jiraClient = getNGJiraClient(jiraConnectorDTO);
      return jiraClient.getIssue(issueKey);
    } catch (Exception e) {
      throw new InvalidRequestException(getJiraException(e));
    }
  }

  private JiraConnectorDTO getConnector(IdentifierRef jiraConnectorRef) {
    ConnectorConfigDTO connectorConfigDTO;
    try {
      // Returns encrypted Jira Connector
      Optional<ConnectorDTO> connector = NGRestUtils.getResponse(
          connectorResourceClient.get(jiraConnectorRef.getIdentifier(), jiraConnectorRef.getAccountIdentifier(),
              jiraConnectorRef.getOrgIdentifier(), jiraConnectorRef.getProjectIdentifier()));
      if (!connector.isPresent() || !isJiraConnector(connector.get().getConnectorInfo())) {
        throw new InvalidRequestException(
            String.format("Jira connector not found for identifier : [%s] with scope: [%s]",
                jiraConnectorRef.getIdentifier(), jiraConnectorRef.getScope()),
            WingsException.USER);
      }
      connectorConfigDTO = connector.get().getConnectorInfo().getConnectorConfig();
    } catch (Exception e) {
      throw new NotFoundException(
          format("Error while getting connector information : [%s]", jiraConnectorRef.getIdentifier()), e);
    }
    // Get Encryption Details
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(jiraConnectorRef, connectorConfigDTO);
    // Decrypt connector using Encryption Details
    connectorConfigDTO = decryptJiraConnectorDTO(
        (JiraConnectorDTO) connectorConfigDTO, encryptionDetails, jiraConnectorRef.getAccountIdentifier());
    return (JiraConnectorDTO) connectorConfigDTO;
  }

  private boolean isJiraConnector(ConnectorInfoDTO connector) {
    return ConnectorType.JIRA == connector.getConnectorType();
  }

  private List<EncryptedDataDetail> getEncryptionDetails(
      IdentifierRef jiraConnectorRef, ConnectorConfigDTO connectorConfigDTO) {
    JiraConnectorDTO jiraConnectorDTO = (JiraConnectorDTO) connectorConfigDTO;
    BaseNGAccess baseNGAccess = getBaseNGAccess(jiraConnectorRef);
    if (!isNull(jiraConnectorDTO.getAuth()) && !isNull(jiraConnectorDTO.getAuth().getCredentials())) {
      return NGRestUtils.getResponse(secretManagerClient.getEncryptionDetails(jiraConnectorRef.getAccountIdentifier(),
          NGAccessWithEncryptionConsumer.builder()
              .ngAccess(baseNGAccess)
              .decryptableEntity(jiraConnectorDTO.getAuth().getCredentials())
              .build()));
    }
    return NGRestUtils.getResponse(secretManagerClient.getEncryptionDetails(jiraConnectorRef.getAccountIdentifier(),
        NGAccessWithEncryptionConsumer.builder().ngAccess(baseNGAccess).decryptableEntity(connectorConfigDTO).build()));
  }

  private JiraConnectorDTO decryptJiraConnectorDTO(
      JiraConnectorDTO dto, List<EncryptedDataDetail> encryptionDetails, String accountIdentifier) {
    if (!isNull(dto.getAuth()) && !isNull(dto.getAuth().getCredentials())) {
      JiraAuthCredentialsDTO decryptedEntity = (JiraAuthCredentialsDTO) NGRestUtils.getResponse(
          secretManagerClient.decryptEncryptedDetails(DecryptableEntityWithEncryptionConsumers.builder()
                                                          .decryptableEntity(dto.getAuth().getCredentials())
                                                          .encryptedDataDetailList(encryptionDetails)
                                                          .build(),
              accountIdentifier));
      dto.getAuth().setCredentials(decryptedEntity);
      return dto;
    } else {
      return (JiraConnectorDTO) NGRestUtils.getResponse(
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

  protected JiraClient getNGJiraClient(JiraConnectorDTO dto) {
    return new JiraClient(JiraRequestResponseMapper.toJiraInternalConfig(dto));
  }

  private String getJiraException(Exception e) {
    if (e.getCause().getClass().equals(HttpResponseException.class)) {
      String responseJson;
      try {
        responseJson = ((HttpResponseException) e.getCause()).getResponseMessage();
      } catch (Exception e1) {
        log.warn("Exception in getting responseMessage from HttpResponseException", e1);
        return ExceptionUtils.getMessage(e);
      }
      try {
        JiraHttpResponseException jiraException = mapper.readValue(responseJson, JiraHttpResponseException.class);
        return jiraException.getErrorMessages().get(0);
      } catch (Exception ex) {
        log.warn("Couldn't read error responseJson: {}", responseJson, ex);
        return responseJson;
      }
    } else {
      log.info("Exception is not of type HttpResponseException");
      return ExceptionUtils.getMessage(e);
    }
  }

  @Data
  @NoArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class JiraHttpResponseException {
    private List<String> errorMessages;
  }
}
