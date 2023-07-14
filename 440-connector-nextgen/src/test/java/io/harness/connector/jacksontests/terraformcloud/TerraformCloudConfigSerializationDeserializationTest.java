/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.jacksontests.terraformcloud;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.connector.jacksontests.ConnectorJacksonTestHelper.readFileAsString;
import static io.harness.delegate.beans.connector.ConnectorType.TERRAFORM_CLOUD;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialDTO;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudCredentialType;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudTokenCredentialsDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.serializer.HObjectMapper;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
@OwnedBy(CDP)
public class TerraformCloudConfigSerializationDeserializationTest extends CategoryTest {
  String url = "https://some.io";
  String name = "name";
  String description = "description";
  String accountIdentifier = "accountIdentifier";
  String projectIdentifier = "projectIdentifier";
  String orgIdentifier = "orgIdentifier";
  String connectorIdentifier = "identifier";
  private ObjectMapper objectMapper;

  @Before
  public void setup() {
    objectMapper = new ObjectMapper();
    HObjectMapper.configureObjectMapperForNG(objectMapper);
  }

  private TerraformCloudConnectorDTO createTerraformCloudConnectorRequestDTO() {
    return TerraformCloudConnectorDTO.builder()
        .terraformCloudUrl(url)
        .credential(TerraformCloudCredentialDTO.builder()
                        .type(TerraformCloudCredentialType.API_TOKEN)
                        .spec(TerraformCloudTokenCredentialsDTO.builder()
                                  .apiToken(SecretRefData.builder().identifier("tokenRef").scope(Scope.ACCOUNT).build())
                                  .build())
                        .build())
        .build();
  }

  private ConnectorInfoDTO createConnectorRequestDTO() {
    TerraformCloudConnectorDTO terraformCloudConnectorDTO = createTerraformCloudConnectorRequestDTO();
    HashMap<String, String> tags = new HashMap<>();
    tags.put("company", "Harness");
    tags.put("env", "dev");
    return ConnectorInfoDTO.builder()
        .name(name)
        .identifier(connectorIdentifier)
        .description(description)
        .accountIdentifier(accountIdentifier)
        .projectIdentifier(projectIdentifier)
        .orgIdentifier(orgIdentifier)
        .tags(tags)
        .connectorType(TERRAFORM_CLOUD)
        .connectorConfig(terraformCloudConnectorDTO)
        .build();
  }

  @Test
  @Owner(developers = OwnerRule.BUHA)
  @Category(UnitTests.class)
  public void testSerializationOfTerraformCloudConnector() {
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO();
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    String connectorString = "";
    try {
      connectorString = objectMapper.writeValueAsString(connectorDTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while serializing terraformCloud connector " + ex.getMessage());
    }
    String expectedResult =
        readFileAsString("440-connector-nextgen/src/test/resources/terraformcloud/terraformCloudConnector.json");
    try {
      JsonNode tree1 = objectMapper.readTree(expectedResult);
      JsonNode tree2 = objectMapper.readTree(connectorString);
      log.info("Expected Connector String: {}", tree1.toString());
      log.info("Actual Connector String: {}", tree2.toString());
      assertThat(tree1.equals(tree2)).isTrue();
    } catch (Exception ex) {
      Assert.fail(
          "Encountered exception while checking the two terraform cloud json value connector" + ex.getMessage());
    }
  }

  @Test
  @Owner(developers = OwnerRule.BUHA)
  @Category(UnitTests.class)
  public void testDeserializationOfTerraformCloudConnector() {
    String connectorInput =
        readFileAsString("440-connector-nextgen/src/test/resources/terraformcloud/terraformCloudConnector.json");
    ConnectorDTO inputConnector = null;
    try {
      inputConnector = objectMapper.readValue(connectorInput, ConnectorDTO.class);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserializing terraform cloud connector " + ex.getMessage());
    }
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO();
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    assertThat(inputConnector).isEqualTo(connectorDTO);
  }
}
