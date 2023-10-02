/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.jacksontests.kubernetescluster;

import static io.harness.connector.jacksontests.ConnectorJacksonTestHelper.readFileAsString;
import static io.harness.delegate.beans.connector.ConnectorType.KUBERNETES_CLUSTER;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.MANUAL_CREDENTIALS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesUserNamePasswordDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.serializer.HObjectMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class KubernetesClusterConfigSerializationDeserializationTest extends CategoryTest {
  String userName = "userName";
  String masterUrl = "https://abc.com";
  String connectorIdentifier = "identifier";
  String name = "name";
  String description = "description";
  String accountIdentifier = "accountIdentifier";
  String projectIdentifier = "projectIdentifier";
  String orgIdentifier = "orgIdentifier";
  private ObjectMapper objectMapper;

  @Before
  public void setup() {
    objectMapper = new ObjectMapper();
    HObjectMapper.configureObjectMapperForNG(objectMapper);
  }

  private KubernetesClusterConfigDTO createKubernetesConnectorRequestDTO() {
    SecretRefData secretRefDataCACert = SecretRefData.builder().identifier("caCertRef").scope(Scope.ACCOUNT).build();
    SecretRefData passwordSecretRef = SecretRefData.builder().identifier("passwordRef").scope(Scope.ACCOUNT).build();
    KubernetesAuthDTO kubernetesAuthDTO =
        KubernetesAuthDTO.builder()
            .authType(KubernetesAuthType.USER_PASSWORD)
            .credentials(
                KubernetesUserNamePasswordDTO.builder().username(userName).passwordRef(passwordSecretRef).build())
            .build();
    KubernetesCredentialDTO k8sCredentials =
        KubernetesCredentialDTO.builder()
            .kubernetesCredentialType(MANUAL_CREDENTIALS)
            .config(KubernetesClusterDetailsDTO.builder().masterUrl(masterUrl).auth(kubernetesAuthDTO).build())
            .build();
    return KubernetesClusterConfigDTO.builder().credential(k8sCredentials).build();
  }

  private ConnectorInfoDTO createConnectorRequestDTOForK8sConnector() {
    KubernetesClusterConfigDTO kubernetesConnectorDTO = createKubernetesConnectorRequestDTO();
    Map<String, String> tags = new HashMap<String, String>() {
      {
        put("company", "Harness");
        put("env", "dev");
      }
    };
    return ConnectorInfoDTO.builder()
        .name(name)
        .identifier(connectorIdentifier)
        .description(description)
        .accountIdentifier(accountIdentifier)
        .projectIdentifier(projectIdentifier)
        .orgIdentifier(orgIdentifier)
        .tags(tags)
        .connectorType(KUBERNETES_CLUSTER)
        .connectorConfig(kubernetesConnectorDTO)
        .build();
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testSerializationOfK8sConnector() {
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTOForK8sConnector();
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    String connectorString = "";
    try {
      connectorString = objectMapper.writeValueAsString(connectorDTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while serializing k8s connector " + ex.getMessage());
    }
    String expectedResult =
        readFileAsString("440-connector-nextgen/src/test/resources/kubernetescluster/k8sConnector.json");
    try {
      JsonNode tree1 = objectMapper.readTree(expectedResult);
      JsonNode tree2 = objectMapper.readTree(connectorString);
      log.info("Expected Connector String: {}", tree1.toString());
      log.info("Actual Connector String: {}", tree2.toString());
      assertThat(tree1.equals(tree2)).isTrue();
    } catch (Exception ex) {
      Assert.fail("Encountered exception while checking the two k8s json value connector" + ex.getMessage());
    }
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testDeserializationOfK8sConnector() {
    String connectorInput =
        readFileAsString("440-connector-nextgen/src/test/resources/kubernetescluster/k8sConnector.json");
    ConnectorDTO inputConnector = null;
    try {
      inputConnector = objectMapper.readValue(connectorInput, ConnectorDTO.class);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserializing k8s connector " + ex.getMessage());
    }
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTOForK8sConnector();
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    assertThat(inputConnector).isEqualTo(connectorDTO);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testSerializationOfK8sClusterConfig() {
    KubernetesClusterConfigDTO kubernetesClusterConfig = createKubernetesConnectorRequestDTO();
    String connectorString = "";
    try {
      connectorString = objectMapper.writeValueAsString(kubernetesClusterConfig);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while serializing k8s connector " + ex.getMessage());
    }
    String expectedResult =
        readFileAsString("440-connector-nextgen/src/test/resources/kubernetescluster/k8sClusterConfig.json");
    try {
      JsonNode tree1 = objectMapper.readTree(expectedResult);
      JsonNode tree2 = objectMapper.readTree(connectorString);
      log.info("Expected Connector String: {}", tree1.toString());
      log.info("Actual Connector String: {}", tree2.toString());
      assertThat(tree1.equals(tree2)).isTrue();
    } catch (Exception ex) {
      Assert.fail("Encountered exception while checking the two k8s json value connector" + ex.getMessage());
    }
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void testDeserializationOfK8sClusterConfig() {
    String connectorInput =
        readFileAsString("440-connector-nextgen/src/test/resources/kubernetescluster/k8sClusterConfig.json");
    KubernetesClusterConfigDTO inputConnector = null;
    try {
      inputConnector = objectMapper.readValue(connectorInput, KubernetesClusterConfigDTO.class);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserializing k8s connector " + ex.getMessage());
    }
    assertThat(inputConnector).isEqualTo(createKubernetesConnectorRequestDTO());
  }
}
