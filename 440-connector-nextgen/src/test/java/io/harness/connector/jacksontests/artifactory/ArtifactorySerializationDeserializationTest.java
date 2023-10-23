/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.jacksontests.artifactory;

import static io.harness.connector.jacksontests.ConnectorJacksonTestHelper.createConnectorRequestDTO;
import static io.harness.connector.jacksontests.ConnectorJacksonTestHelper.readFileAsString;
import static io.harness.delegate.beans.connector.ConnectorType.ARTIFACTORY;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
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
public class ArtifactorySerializationDeserializationTest extends CategoryTest {
  private ObjectMapper objectMapper;
  String artifactoryServerUrl = "https://artifactoryurl";
  String artifactoryusername = "artifactoryusername";
  String passwordRef = "artifactory_pass";

  @Before
  public void setup() {
    objectMapper = new ObjectMapper();
    HObjectMapper.configureObjectMapperForNG(objectMapper);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testSerializationOfArtifactoryConnectorWithUsernamePass() {
    ArtifactoryConnectorDTO artifactoryConnectorDTO = createArtifactoryConfigWithUserNameAndPass();
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO(artifactoryConnectorDTO, ARTIFACTORY);
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    String connectorString = "";
    try {
      connectorString = objectMapper.writeValueAsString(connectorDTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while serializing artifactory ConnectorDisconnectHandler " + ex.getMessage());
    }
    String expectedResult =
        readFileAsString("440-connector-nextgen/src/test/resources/artifactory/artifactoryWithUserNamePassword.json");
    try {
      JsonNode tree1 = objectMapper.readTree(expectedResult);
      JsonNode tree2 = objectMapper.readTree(connectorString);
      log.info("Expected Connector String: {}", tree1.toString());
      log.info("Actual Connector String: {}", tree2.toString());
      assertThat(tree1.equals(tree2)).isTrue();
    } catch (Exception ex) {
      Assert.fail("Encountered exception while checking the two artifactory json value ConnectorDisconnectHandler" + ex.getMessage());
    }
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testSerializationOfArtifactoryConnectorWithAnonymous() {
    ArtifactoryConnectorDTO artifactoryConnectorDTO = createArtifactoryConfigWithAnonymousCreds();
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO(artifactoryConnectorDTO, ARTIFACTORY);
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    String connectorString = "";
    try {
      connectorString = objectMapper.writeValueAsString(connectorDTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while serializing artifactory ConnectorDisconnectHandler " + ex.getMessage());
    }
    String expectedResult =
        readFileAsString("440-connector-nextgen/src/test/resources/artifactory/artifactoryWithAnonymousCreds.json");
    try {
      JsonNode tree1 = objectMapper.readTree(expectedResult);
      JsonNode tree2 = objectMapper.readTree(connectorString);
      log.info("Expected Connector String: {}", tree1.toString());
      log.info("Actual Connector String: {}", tree2.toString());
      assertThat(tree1.equals(tree2)).isTrue();
    } catch (Exception ex) {
      Assert.fail("Encountered exception while checking the two artifactory json value ConnectorDisconnectHandler" + ex.getMessage());
    }
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testDeserializationOfArtifactoryConnectorWithUsernamePass() {
    String connectorInput =
        readFileAsString("440-connector-nextgen/src/test/resources/artifactory/artifactoryWithUserNamePassword.json");
    ConnectorDTO inputConnector = null;
    try {
      inputConnector = objectMapper.readValue(connectorInput, ConnectorDTO.class);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserializing artifactory ConnectorDisconnectHandler " + ex.getMessage());
    }
    ArtifactoryConnectorDTO artifactoryConnectorDTO = createArtifactoryConfigWithUserNameAndPass();
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO(artifactoryConnectorDTO, ARTIFACTORY);
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    assertThat(inputConnector.toString()).isEqualTo(connectorDTO.toString());
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testDeserializationOfArtifactoryConnectorWithAnonymous() {
    String connectorInput =
        readFileAsString("440-connector-nextgen/src/test/resources/artifactory/artifactoryWithAnonymousCreds.json");
    ConnectorDTO inputConnector = null;
    try {
      inputConnector = objectMapper.readValue(connectorInput, ConnectorDTO.class);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserializing artifactory ConnectorDisconnectHandler " + ex.getMessage());
    }
    ArtifactoryConnectorDTO artifactoryConnectorDTO = createArtifactoryConfigWithAnonymousCreds();
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO(artifactoryConnectorDTO, ARTIFACTORY);
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    assertThat(inputConnector.toString()).isEqualTo(connectorDTO.toString());
  }

  private ArtifactoryConnectorDTO createArtifactoryConfigWithUserNameAndPass() {
    ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO =
        ArtifactoryUsernamePasswordAuthDTO.builder()
            .username(artifactoryusername)
            .passwordRef(SecretRefData.builder().identifier(passwordRef).scope(Scope.ACCOUNT).build())
            .build();
    return ArtifactoryConnectorDTO.builder()
        .artifactoryServerUrl(artifactoryServerUrl)
        .auth(ArtifactoryAuthenticationDTO.builder()
                  .authType(ArtifactoryAuthType.USER_PASSWORD)
                  .credentials(artifactoryUsernamePasswordAuthDTO)
                  .build())
        .build();
  }

  private ArtifactoryConnectorDTO createArtifactoryConfigWithAnonymousCreds() {
    return ArtifactoryConnectorDTO.builder()
        .artifactoryServerUrl(artifactoryServerUrl)
        .auth(ArtifactoryAuthenticationDTO.builder().authType(ArtifactoryAuthType.ANONYMOUS).build())
        .build();
  }
}
