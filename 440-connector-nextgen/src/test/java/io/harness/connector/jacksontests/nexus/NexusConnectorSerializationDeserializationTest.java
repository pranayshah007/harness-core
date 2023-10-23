/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.jacksontests.nexus;

import static io.harness.connector.jacksontests.ConnectorJacksonTestHelper.createConnectorRequestDTO;
import static io.harness.connector.jacksontests.ConnectorJacksonTestHelper.readFileAsString;
import static io.harness.delegate.beans.connector.ConnectorType.NEXUS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthType;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthenticationDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusUsernamePasswordAuthDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.serializer.HObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class NexusConnectorSerializationDeserializationTest extends CategoryTest {
  private ObjectMapper objectMapper;
  String nexusServerUrl = "https://nexusUrl.com";
  String nexusUserName = "nexusUserName";
  String passwordRef = "nexus_pass";

  @Before
  public void setup() {
    objectMapper = new ObjectMapper();
    HObjectMapper.configureObjectMapperForNG(objectMapper);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testSerializationOfNexusConnectorWithUsernamePass() {
    NexusConnectorDTO nexusConnectorDTO = createNexusConfigWithUserNameAndPass();
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO(nexusConnectorDTO, NEXUS);
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    String connectorString = "";
    try {
      connectorString = objectMapper.writeValueAsString(connectorDTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while serializing nexus ConnectorDisconnectHandler " + ex.getMessage());
    }
    String expectedResult =
        readFileAsString("440-connector-nextgen/src/test/resources/nexus/nexusWithUserNamePass.json");
    try {
      JsonNode tree1 = objectMapper.readTree(expectedResult);
      JsonNode tree2 = objectMapper.readTree(connectorString);
      log.info("Expected Connector String: {}", tree1.toString());
      log.info("Actual Connector String: {}", tree2.toString());
      assertThat(tree1.equals(tree2)).isTrue();
    } catch (Exception ex) {
      Assert.fail("Encountered exception while checking the two nexus json value ConnectorDisconnectHandler" + ex.getMessage());
    }
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testSerializationOfNexusConnectorWithAnonymous() {
    NexusConnectorDTO nexusConnectorDTO = createNexusConfigWithAnonymousCreds();
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO(nexusConnectorDTO, NEXUS);
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    String connectorString = "";
    try {
      connectorString = objectMapper.writeValueAsString(connectorDTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while serializing nexus ConnectorDisconnectHandler " + ex.getMessage());
    }
    String expectedResult =
        readFileAsString("440-connector-nextgen/src/test/resources/nexus/nexusWithAnonymousCreds.json");
    try {
      JsonNode tree1 = objectMapper.readTree(expectedResult);
      JsonNode tree2 = objectMapper.readTree(connectorString);
      log.info("Expected Connector String: {}", tree1.toString());
      log.info("Actual Connector String: {}", tree2.toString());
      assertThat(tree1.equals(tree2)).isTrue();
    } catch (Exception ex) {
      Assert.fail("Encountered exception while checking the two nexus json value ConnectorDisconnectHandler" + ex.getMessage());
    }
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testDeserializationOfNexusConnectorWithUsernamePass() {
    String connectorInput =
        readFileAsString("440-connector-nextgen/src/test/resources/nexus/nexusWithUserNamePass.json");
    ConnectorDTO inputConnector = null;
    try {
      inputConnector = objectMapper.readValue(connectorInput, ConnectorDTO.class);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserializing nexus ConnectorDisconnectHandler " + ex.getMessage());
    }
    NexusConnectorDTO nexusConnectorDTO = createNexusConfigWithUserNameAndPass();
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO(nexusConnectorDTO, NEXUS);
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    assertThat(inputConnector.toString()).isEqualTo(connectorDTO.toString());
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testDeserializationOfNexusConnectorWithAnonymous() {
    String connectorInput =
        readFileAsString("440-connector-nextgen/src/test/resources/nexus/nexusWithAnonymousCreds.json");
    ConnectorDTO inputConnector = null;
    try {
      inputConnector = objectMapper.readValue(connectorInput, ConnectorDTO.class);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserializing nexus ConnectorDisconnectHandler " + ex.getMessage());
    }
    NexusConnectorDTO nexusConnectorDTO = createNexusConfigWithAnonymousCreds();
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO(nexusConnectorDTO, NEXUS);
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    assertThat(inputConnector.toString()).isEqualTo(connectorDTO.toString());
  }

  private NexusConnectorDTO createNexusConfigWithUserNameAndPass() {
    NexusUsernamePasswordAuthDTO nexusUsernamePasswordAuthDTO =
        NexusUsernamePasswordAuthDTO.builder()
            .username(nexusUserName)
            .passwordRef(SecretRefData.builder().identifier(passwordRef).scope(Scope.ACCOUNT).build())
            .build();
    return NexusConnectorDTO.builder()
        .nexusServerUrl(nexusServerUrl)
        .version("3.x")
        .auth(NexusAuthenticationDTO.builder()
                  .authType(NexusAuthType.USER_PASSWORD)
                  .credentials(nexusUsernamePasswordAuthDTO)
                  .build())
        .build();
  }

  private NexusConnectorDTO createNexusConfigWithAnonymousCreds() {
    return NexusConnectorDTO.builder()
        .nexusServerUrl(nexusServerUrl)
        .version("3.x")
        .auth(NexusAuthenticationDTO.builder().authType(NexusAuthType.ANONYMOUS).build())
        .build();
  }
}
