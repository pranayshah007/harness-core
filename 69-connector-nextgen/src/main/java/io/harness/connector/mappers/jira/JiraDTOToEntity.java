package io.harness.connector.mappers.jira;

import static io.harness.delegate.beans.connector.ConnectorCategory.CONNECTOR;

import com.google.inject.Singleton;

import io.harness.connector.entities.embedded.jira.JiraConnector;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.connector.mappers.SecretRefHelper;
import io.harness.delegate.beans.connector.ConnectorCategory;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;

import java.util.Collections;
import java.util.List;

@Singleton
public class JiraDTOToEntity implements ConnectorDTOToEntityMapper<JiraConnectorDTO> {
  @Override
  public JiraConnector toConnectorEntity(JiraConnectorDTO configDTO) {
    return JiraConnector.builder()
        .jiraUrl(configDTO.getJiraUrl())
        .username(configDTO.getUsername())
        .passwordRef(SecretRefHelper.getSecretConfigString(configDTO.getPasswordRef()))
        .build();
  }

  @Override
  public List<ConnectorCategory> getConnectorCategory() {
    return Collections.singletonList(CONNECTOR);
  }
}
