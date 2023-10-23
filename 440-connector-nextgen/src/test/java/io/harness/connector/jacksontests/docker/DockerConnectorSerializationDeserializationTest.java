/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.jacksontests.docker;

import static io.harness.connector.jacksontests.ConnectorJacksonTestHelper.createConnectorRequestDTO;
import static io.harness.connector.jacksontests.ConnectorJacksonTestHelper.readFileAsString;
import static io.harness.delegate.beans.connector.ConnectorType.DOCKER;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerRegistryProviderType;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
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
public class DockerConnectorSerializationDeserializationTest extends CategoryTest {
  private ObjectMapper objectMapper;
  String dockerRegistryUrl = "https://index.docker.io/v2/";
  String dockerUsername = "dockerUsername";
  String dockerpasswordRef = "dockerPassword";

  @Before
  public void setup() {
    objectMapper = new ObjectMapper();
    HObjectMapper.configureObjectMapperForNG(objectMapper);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testSerializationOfDockerConnectorWithUsernamePass() {
    DockerConnectorDTO dockerConnectorDTO = createDockerConfigWithUserNameAndPass();
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO(dockerConnectorDTO, DOCKER);
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    String connectorString = "";
    try {
      connectorString = objectMapper.writeValueAsString(connectorDTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while serializing docker ConnectorDisconnectHandler " + ex.getMessage());
    }
    String expectedResult =
        readFileAsString("440-connector-nextgen/src/test/resources/docker/dockerConnectorWithUserNamePass.json");
    try {
      JsonNode tree1 = objectMapper.readTree(expectedResult);
      JsonNode tree2 = objectMapper.readTree(connectorString);
      log.info("Expected Connector String: {}", tree1.toString());
      log.info("Actual Connector String: {}", tree2.toString());
      assertThat(tree1.equals(tree2)).isTrue();
    } catch (Exception ex) {
      Assert.fail("Encountered exception while checking the two docker json value ConnectorDisconnectHandler" + ex.getMessage());
    }
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testSerializationOfDockerConnectorWithAnonymous() {
    DockerConnectorDTO dockerConnectorDTO = createDockerConfigWithAnonymousCreds();
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO(dockerConnectorDTO, DOCKER);
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    String connectorString = "";
    try {
      connectorString = objectMapper.writeValueAsString(connectorDTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while serializing docker ConnectorDisconnectHandler " + ex.getMessage());
    }
    String expectedResult =
        readFileAsString("440-connector-nextgen/src/test/resources/docker/dockerConnectorWithAnonymous.json");
    try {
      JsonNode tree1 = objectMapper.readTree(expectedResult);
      JsonNode tree2 = objectMapper.readTree(connectorString);
      log.info("Expected Connector String: {}", tree1.toString());
      log.info("Actual Connector String: {}", tree2.toString());
      assertThat(tree1.equals(tree2)).isTrue();
    } catch (Exception ex) {
      Assert.fail("Encountered exception while checking the two docker json value ConnectorDisconnectHandler" + ex.getMessage());
    }
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testDeserializationOfDockerConnectorWithUsernamePass() {
    String connectorInput =
        readFileAsString("440-connector-nextgen/src/test/resources/docker/dockerConnectorWithUserNamePass.json");
    ConnectorDTO inputConnector = null;
    try {
      inputConnector = objectMapper.readValue(connectorInput, ConnectorDTO.class);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserializing k8s ConnectorDisconnectHandler " + ex.getMessage());
    }
    DockerConnectorDTO dockerConnectorDTO = createDockerConfigWithUserNameAndPass();
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO(dockerConnectorDTO, DOCKER);
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    assertThat(inputConnector).isEqualTo(connectorDTO);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testDeserializationOfDockerConnectorWithAnonymous() {
    String connectorInput =
        readFileAsString("440-connector-nextgen/src/test/resources/docker/dockerConnectorWithAnonymous.json");
    ConnectorDTO inputConnector = null;
    try {
      inputConnector = objectMapper.readValue(connectorInput, ConnectorDTO.class);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserializing docker ConnectorDisconnectHandler " + ex.getMessage());
    }
    DockerConnectorDTO dockerConnectorDTO = createDockerConfigWithAnonymousCreds();
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO(dockerConnectorDTO, DOCKER);
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    assertThat(inputConnector).isEqualTo(connectorDTO);
  }

  private DockerConnectorDTO createDockerConfigWithUserNameAndPass() {
    DockerUserNamePasswordDTO dockerUserNamePasswordDTO =
        DockerUserNamePasswordDTO.builder()
            .username(dockerUsername)
            .passwordRef(SecretRefData.builder().identifier(dockerpasswordRef).scope(Scope.ACCOUNT).build())
            .build();
    return DockerConnectorDTO.builder()
        .dockerRegistryUrl(dockerRegistryUrl)
        .providerType(DockerRegistryProviderType.DOCKER_HUB)
        .auth(DockerAuthenticationDTO.builder()
                  .authType(DockerAuthType.USER_PASSWORD)
                  .credentials(dockerUserNamePasswordDTO)
                  .build())
        .build();
  }

  private DockerConnectorDTO createDockerConfigWithAnonymousCreds() {
    return DockerConnectorDTO.builder()
        .dockerRegistryUrl(dockerRegistryUrl)
        .providerType(DockerRegistryProviderType.DOCKER_HUB)
        .auth(DockerAuthenticationDTO.builder().authType(DockerAuthType.ANONYMOUS).build())
        .build();
  }
}
