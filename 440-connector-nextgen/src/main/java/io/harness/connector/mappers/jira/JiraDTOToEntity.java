/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.jira;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.jira.JiraConnector;
import io.harness.connector.entities.embedded.jira.JiraConnector.JiraConnectorBuilder;
import io.harness.connector.entities.embedded.jira.JiraPATAuthentication;
import io.harness.connector.entities.embedded.jira.JiraUserNamePasswordAuthentication;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.jira.JiraAuthType;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.beans.connector.jira.JiraPATDTO;
import io.harness.delegate.beans.connector.jira.JiraUserNamePasswordDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDC)
@Singleton
public class JiraDTOToEntity implements ConnectorDTOToEntityMapper<JiraConnectorDTO, JiraConnector> {
  @Override
  public JiraConnector toConnectorEntity(JiraConnectorDTO configDTO) {
    // no change required after Jira connector migration
    JiraConnectorBuilder jiraConnectorBuilder =
        JiraConnector.builder()
            .jiraUrl(configDTO.getJiraUrl())
            .username(configDTO.getUsername())
            .usernameRef(SecretRefHelper.getSecretConfigString(configDTO.getUsernameRef()))
            .passwordRef(SecretRefHelper.getSecretConfigString(configDTO.getPasswordRef()));

    if (!isNull(configDTO.getAuth())) {
      jiraConnectorBuilder.authType(configDTO.getAuth().getAuthType());

      if (JiraAuthType.USER_PASSWORD.equals(configDTO.getAuth().getAuthType())) {
        // override old base level fields with value present in new JiraAuthCredentials in USER_PASSWORD case
        JiraUserNamePasswordDTO jiraUserNamePasswordDTO =
            (JiraUserNamePasswordDTO) configDTO.getAuth().getCredentials();
        jiraConnectorBuilder.username(jiraUserNamePasswordDTO.getUsername())
            .usernameRef(SecretRefHelper.getSecretConfigString(jiraUserNamePasswordDTO.getUsernameRef()))
            .passwordRef(SecretRefHelper.getSecretConfigString(jiraUserNamePasswordDTO.getPasswordRef()))
            .jiraAuthentication(JiraUserNamePasswordAuthentication.fromJiraAuthCredentialsDTO(jiraUserNamePasswordDTO));
      } else if (JiraAuthType.PAT.equals(configDTO.getAuth().getAuthType())) {
        JiraPATDTO jiraPATDTO = (JiraPATDTO) configDTO.getAuth().getCredentials();
        jiraConnectorBuilder.jiraAuthentication(JiraPATAuthentication.fromJiraAuthCredentialsDTO(jiraPATDTO));
      } else {
        throw new InvalidRequestException(
            String.format("Unsupported jira auth type provided : %s", configDTO.getAuth().getAuthType()));
      }
    }
    return jiraConnectorBuilder.build();
  }
}
