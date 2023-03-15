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
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.task.jira.mappers.JiraRequestResponseMapper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.jira.JiraClient;
import io.harness.jira.JiraIssueNG;
import io.harness.ng.core.BaseNGAccess;
import io.harness.remote.client.NGRestUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CCMJiraHelperImpl implements CCMJiraHelper {
  @Inject private ConnectorResourceClient connectorResourceClient;
  @Inject @Named("PRIVILEGED") private SecretManagerClientService secretManagerClientService;
  @Inject private SecretDecryptionService secretDecryptionService;

  @Override
  public JiraIssueNG createIssue(
      String accountId, String jiraConnectorRef, String projectKey, String issueType, Map<String, String> fields) {
    IdentifierRef connectorRef = getIdentifierRef(jiraConnectorRef, accountId, "default", null);
    JiraConnectorDTO jiraConnectorDTO = getConnector(connectorRef);
    JiraClient jiraClient = getNGJiraClient(jiraConnectorDTO);
    return jiraClient.createIssue(projectKey, issueType, fields, false, false, false);
  }

  @Override
  public JiraIssueNG getIssue(String accountId, String jiraConnectorRef, String issueKey) {
    IdentifierRef connectorRef = getIdentifierRef(jiraConnectorRef, accountId, "default", null);
    JiraConnectorDTO jiraConnectorDTO = getConnector(connectorRef);
    JiraClient jiraClient = getNGJiraClient(jiraConnectorDTO);
    return jiraClient.getIssue(issueKey);
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
    decryptJiraConnectorDTO((JiraConnectorDTO) connectorConfigDTO, encryptionDetails);
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
      return secretManagerClientService.getEncryptionDetails(baseNGAccess, jiraConnectorDTO.getAuth().getCredentials());
    }
    return secretManagerClientService.getEncryptionDetails(baseNGAccess, jiraConnectorDTO);
  }

  private void decryptJiraConnectorDTO(JiraConnectorDTO dto, List<EncryptedDataDetail> encryptionDetails) {
    if (!isNull(dto.getAuth()) && !isNull(dto.getAuth().getCredentials())) {
      secretDecryptionService.decrypt(dto.getAuth().getCredentials(), encryptionDetails);
    } else {
      secretDecryptionService.decrypt(dto, encryptionDetails);
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
}
