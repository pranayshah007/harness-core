/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers;

import static io.harness.delegate.beans.connector.ConnectorType.DOCKER;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorsTestBase;
import io.harness.connector.entities.Connector;
import io.harness.connector.utils.DockerConnectorTestHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.encryption.Scope;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ConnectorMapperTest extends ConnectorsTestBase {
  @Inject ConnectorMapper connectorMapper;
  private static final String accountIdentifier = "accountIdentifier";
  private static final String orgIdentifier = "orgIdentifier";
  private static final String projectIdentifier = "projectIdentifier";
  private static final String identifier = "identifier";
  private static final String description = "description";
  private static final String name = "description";

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void toConnector() {
    ConnectorConfigDTO dockerConnectorDTO = DockerConnectorTestHelper.createDockerConnectorDTO(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, Scope.ACCOUNT);
    Map<String, String> tags = new HashMap<String, String>() {
      {
        put("company", "Harness");
        put("env", "dev");
      }
    };
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorConfig(dockerConnectorDTO)
                                            .connectorType(DOCKER)
                                            .description(description)
                                            .identifier(identifier)
                                            .name(name)
                                            .orgIdentifier(orgIdentifier)
                                            .projectIdentifier(projectIdentifier)
                                            .tags(tags)
                                            .build();
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorInfoDTO).build();
    Connector connector = connectorMapper.toConnector(connectorDTO, accountIdentifier);
    assertThat(connector).isNotNull();
    assertThat(connector.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(connector.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(connector.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(connector.getIdentifier()).isEqualTo(identifier);
    assertThat(connector.getType()).isEqualTo(DOCKER);
    assertThat(connector.getName()).isEqualTo(name);
    assertThat(connector.getDescription()).isEqualTo(description);
    List<NGTag> tagsInEntity = connector.getTags();
    assertThat(tagsInEntity.size()).isEqualTo(2);
    assertThat(tagsInEntity.get(0).getKey()).isEqualTo("company");
    assertThat(tagsInEntity.get(0).getValue()).isEqualTo("Harness");
    assertThat(tagsInEntity.get(1).getKey()).isEqualTo("env");
    assertThat(tagsInEntity.get(1).getValue()).isEqualTo("dev");
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void writeDTO() {
    List<NGTag> tags = Arrays.asList(
        NGTag.builder().key("env").value("service").build(), NGTag.builder().key("service").value("ui").build());
    Connector connector = DockerConnectorTestHelper.createDockerConnector(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, Scope.ACCOUNT);
    connector.setType(DOCKER);
    connector.setName(name);
    connector.setDescription(description);
    connector.setTags(tags);
    ConnectorInfoDTO connectorDTO = connectorMapper.writeDTO(connector).getConnector();
    assertThat(connectorDTO).isNotNull();
    assertThat(connectorDTO.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(connectorDTO.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(connectorDTO.getIdentifier()).isEqualTo(identifier);
    assertThat(connectorDTO.getConnectorType()).isEqualTo(DOCKER);
    assertThat(connectorDTO.getName()).isEqualTo(name);
    assertThat(connectorDTO.getDescription()).isEqualTo(description);
    Map<String, String> tagsMap = connectorDTO.getTags();
    assertThat(tagsMap.size()).isEqualTo(2);
    assertThat(tagsMap.get("env")).isEqualTo("service");
    assertThat(tagsMap.get("service")).isEqualTo("ui");
  }
}
