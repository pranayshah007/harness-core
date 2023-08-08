/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.jacksontests.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.connector.jacksontests.ConnectorJacksonTestHelper.readFileAsString;
import static io.harness.delegate.beans.connector.ConnectorType.AZURE;
import static io.harness.delegate.beans.connector.azureconnector.AzureCredentialType.MANUAL_CREDENTIALS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialSpecDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
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
public class AzureConfigSerializationDeserializationTest extends CategoryTest {
  String clientId = "clientId";
  String tenantId = "tenantId";
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

  private AzureConnectorDTO createAzureConnectorRequestDTO() {
    SecretRefData keySecretRef = SecretRefData.builder().identifier("secretRef").scope(Scope.ACCOUNT).build();
    AzureAuthDTO azureAuthDTO = AzureAuthDTO.builder()
                                    .azureSecretType(AzureSecretType.SECRET_KEY)
                                    .credentials(AzureClientSecretKeyDTO.builder().secretKey(keySecretRef).build())
                                    .build();
    AzureCredentialSpecDTO azureCredentialSpecDTO =
        AzureManualDetailsDTO.builder().clientId(clientId).tenantId(tenantId).authDTO(azureAuthDTO).build();

    AzureCredentialDTO azureCredentialDTO =
        AzureCredentialDTO.builder().azureCredentialType(MANUAL_CREDENTIALS).config(azureCredentialSpecDTO).build();

    return AzureConnectorDTO.builder()
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .credential(azureCredentialDTO)
        .build();
  }

  private ConnectorInfoDTO createConnectorRequestDTOForAzure() {
    AzureConnectorDTO azureConnectorDTO = createAzureConnectorRequestDTO();
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
        .connectorType(AZURE)
        .connectorConfig(azureConnectorDTO)
        .build();
  }

  @Test
  @Owner(developers = OwnerRule.BUHA)
  @Category(UnitTests.class)
  public void testSerializationOfAzureConnector() {
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTOForAzure();
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    String connectorString = "";
    try {
      connectorString = objectMapper.writeValueAsString(connectorDTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while serializing azure connector " + ex.getMessage());
    }
    String expectedResult = readFileAsString("440-connector-nextgen/src/test/resources/azure/azureConnector.json");
    try {
      JsonNode tree1 = objectMapper.readTree(expectedResult);
      JsonNode tree2 = objectMapper.readTree(connectorString);
      log.info("Expected Connector String: {}", tree1.toString());
      log.info("Actual Connector String: {}", tree2.toString());
      assertThat(tree1.equals(tree2)).isTrue();
    } catch (Exception ex) {
      Assert.fail("Encountered exception while checking the two azure json value connector" + ex.getMessage());
    }
  }

  @Test
  @Owner(developers = OwnerRule.BUHA)
  @Category(UnitTests.class)
  public void testDeserializationOfAzureConnector() {
    String connectorInput = readFileAsString("440-connector-nextgen/src/test/resources/azure/azureConnector.json");
    ConnectorDTO inputConnector = null;
    try {
      inputConnector = objectMapper.readValue(connectorInput, ConnectorDTO.class);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserializing azure connector " + ex.getMessage());
    }
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTOForAzure();
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    assertThat(inputConnector).isEqualTo(connectorDTO);
  }
}
