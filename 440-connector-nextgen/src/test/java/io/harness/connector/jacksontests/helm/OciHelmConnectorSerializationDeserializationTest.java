/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.jacksontests.helm;

import static io.harness.connector.jacksontests.ConnectorJacksonTestHelper.createConnectorRequestDTO;
import static io.harness.connector.jacksontests.ConnectorJacksonTestHelper.readFileAsString;
import static io.harness.delegate.beans.connector.ConnectorType.OCI_HELM_REPO;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.helm.OciHelmAuthType;
import io.harness.delegate.beans.connector.helm.OciHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO.OciHelmConnectorDTOBuilder;
import io.harness.delegate.beans.connector.helm.OciHelmUsernamePasswordDTO;
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
public class OciHelmConnectorSerializationDeserializationTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private static final String BASE_PATH = "440-connector-nextgen/src/test/resources/helm/";

  @Before
  public void setup() {
    objectMapper = new ObjectMapper();
    HObjectMapper.configureObjectMapperForNG(objectMapper);
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testShouldSerializeOciHelmConnectorWithUsernamePasswordAuth() {
    OciHelmConnectorDTO ociHelmConnectorDTO = buildConnector(OciHelmAuthType.USER_PASSWORD);
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO(ociHelmConnectorDTO, OCI_HELM_REPO);
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    String connectorString = "";
    try {
      connectorString = objectMapper.writeValueAsString(connectorDTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while serialize oci helm connector " + ex.getMessage());
    }
    String expectedResult = readFileAsString(BASE_PATH + "ociHelmConnectorWithUsernamePasswordAuth.json");
    try {
      JsonNode tree1 = objectMapper.readTree(expectedResult);
      JsonNode tree2 = objectMapper.readTree(connectorString);
      log.info("Expected Connector String: {}", tree1.toString());
      log.info("Actual Connector String: {}", tree2.toString());
      assertThat(tree1.equals(tree2)).isTrue();
    } catch (Exception ex) {
      Assert.fail("Encountered exception while checking the two oci helm json value connector" + ex.getMessage());
    }
  }

  @Test
  @Owner(developers = OwnerRule.NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testShouldDeserializeOciHelmConnectorWithUsernamePasswordAuth() {
    String connectorInput = readFileAsString(BASE_PATH + "ociHelmConnectorWithUsernamePasswordAuth.json");
    ConnectorDTO inputConnector = null;
    try {
      inputConnector = objectMapper.readValue(connectorInput, ConnectorDTO.class);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserialize oci helm connector " + ex.getMessage());
    }
    OciHelmConnectorDTO ociHelmConnectorDTO = buildConnector(OciHelmAuthType.USER_PASSWORD);
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO(ociHelmConnectorDTO, OCI_HELM_REPO);
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    assertThat(inputConnector).isEqualTo(connectorDTO);
  }

  private OciHelmConnectorDTO buildConnector(OciHelmAuthType authType) {
    OciHelmConnectorDTOBuilder builder = OciHelmConnectorDTO.builder().helmRepoUrl("user.azurecr.io");

    if (authType == OciHelmAuthType.USER_PASSWORD) {
      builder.auth(
          OciHelmAuthenticationDTO.builder()
              .authType(authType)
              .credentials(
                  OciHelmUsernamePasswordDTO.builder()
                      .username("test")
                      .passwordRef(SecretRefData.builder().identifier("ociHelmPassword").scope(Scope.ACCOUNT).build())
                      .build())
              .build());
    } else {
      builder.auth(OciHelmAuthenticationDTO.builder().authType(authType).build());
    }

    return builder.build();
  }
}
