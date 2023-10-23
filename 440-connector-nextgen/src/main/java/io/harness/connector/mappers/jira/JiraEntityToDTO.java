/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.jira;

import static java.util.Objects.isNull;

import io.harness.connector.entities.embedded.jira.JiraConnector;
import io.harness.connector.entities.embedded.jira.JiraUserNamePasswordAuthentication;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.jira.JiraAuthType;
import io.harness.delegate.beans.connector.jira.JiraAuthenticationDTO;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO.JiraConnectorDTOBuilder;
import io.harness.encryption.SecretRefHelper;

import com.google.inject.Singleton;

@Singleton
public class JiraEntityToDTO implements ConnectorEntityToDTOMapper<JiraConnectorDTO, JiraConnector> {
  @Override
  public JiraConnectorDTO createConnectorDTO(JiraConnector jiraConnector) {
    // no change required after Jira ConnectorDisconnectHandler migration
    JiraConnectorDTOBuilder jiraConnectorDTOBuilder =
        JiraConnectorDTO.builder()
            .jiraUrl(jiraConnector.getJiraUrl())
            .username(jiraConnector.getUsername())
            .usernameRef(SecretRefHelper.createSecretRef(jiraConnector.getUsernameRef()))
            .passwordRef(SecretRefHelper.createSecretRef(jiraConnector.getPasswordRef()));
    if (!isNull(jiraConnector.getJiraAuthentication())) {
      jiraConnectorDTOBuilder.auth(JiraAuthenticationDTO.builder()
                                       .authType(jiraConnector.getAuthType())
                                       .credentials(jiraConnector.getJiraAuthentication().toJiraAuthCredentialsDTO())
                                       .build());
      if (JiraAuthType.USER_PASSWORD.equals(jiraConnector.getAuthType())) {
        // override old base level fields with value present in new JiraAuthCredentials in USER_PASSWORD case
        JiraUserNamePasswordAuthentication jiraUserNamePasswordAuthentication =
            (JiraUserNamePasswordAuthentication) jiraConnector.getJiraAuthentication();
        jiraConnectorDTOBuilder.username(jiraUserNamePasswordAuthentication.getUsername())
            .usernameRef(SecretRefHelper.createSecretRef(jiraUserNamePasswordAuthentication.getUsernameRef()))
            .passwordRef(SecretRefHelper.createSecretRef(jiraUserNamePasswordAuthentication.getPasswordRef()));
      }
    }
    return jiraConnectorDTOBuilder.build();
  }
}
