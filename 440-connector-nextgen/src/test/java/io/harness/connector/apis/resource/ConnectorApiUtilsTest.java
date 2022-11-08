/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.apis.resource;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.jacksontests.ConnectorJacksonTestHelper;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.spec.server.connector.v1.model.ConnectorRequest;
import io.harness.spec.server.connector.v1.model.ConnectorResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.serializer.HObjectMapper;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import net.sf.json.test.JSONAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.DX)
public class ConnectorApiUtilsTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private Validator validator;
  private ConnectorApiUtils connectorApiUtils;

  private String testFilesBasePath = "440-connector-nextgen/src/test/resources/server/stub/connector/";

  @Before
  public void setUp() {
    objectMapper = new ObjectMapper();
    HObjectMapper.configureObjectMapperForNG(objectMapper);

    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
    connectorApiUtils = new ConnectorApiUtils(validator);
  }

  private boolean testConnectorDtoMapping(String from, String to) throws JsonProcessingException {
    String fromJson = ConnectorJacksonTestHelper.readFileAsString(testFilesBasePath + from);
    String toJson = ConnectorJacksonTestHelper.readFileAsString(testFilesBasePath + to);

    ConnectorRequest connectorRequest = objectMapper.readValue(fromJson, ConnectorRequest.class);
    Set<ConstraintViolation<Object>> violations = validator.validate(connectorRequest);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    ConnectorDTO connectorDTO = connectorApiUtils.toConnectorDTO(connectorRequest);
    violations = validator.validate(connectorDTO);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    String connectorDto = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(connectorDTO);
    JSONAssert.assertEquals(toJson, connectorDto);

    return true;
  }

  private boolean testResponseMapping(String from, String to) throws JsonProcessingException {
    String fromJson = ConnectorJacksonTestHelper.readFileAsString(testFilesBasePath + from);
    String toJson = ConnectorJacksonTestHelper.readFileAsString(testFilesBasePath + to);

    ConnectorResponseDTO connectorResponseDTO = objectMapper.readValue(fromJson, ConnectorResponseDTO.class);
    Set<ConstraintViolation<Object>> violations = validator.validate(connectorResponseDTO);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    ConnectorResponse connectorResponse = connectorApiUtils.toConnectorResponse(connectorResponseDTO);
    violations = validator.validate(connectorResponse);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    String connectorDto = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(connectorResponse);
    JSONAssert.assertEquals(toJson, connectorDto);

    return true;
  }

  @Test
  @Owner(developers = OwnerRule.ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testConnectorDtoMapping() throws JsonProcessingException {
    assertThat(testConnectorDtoMapping("git/git-http-1.json", "git/git-http-2.json")).isTrue();
    assertThat(testConnectorDtoMapping("git/git-http-encrypted-1.json", "git/git-http-encrypted-2.json")).isTrue();
    assertThat(testConnectorDtoMapping("git/git-ssh-1.json", "git/git-ssh-2.json")).isTrue();
    assertThat(testConnectorDtoMapping("artifactory/artifactory-1.json", "artifactory/artifactory-2.json")).isTrue();
    assertThat(
        testConnectorDtoMapping("artifactory/artifactory-encrypted-1.json", "artifactory/artifactory-encrypted-2.json"))
        .isTrue();
    assertThat(
        testConnectorDtoMapping("artifactory/artifactory-anonymous-1.json", "artifactory/artifactory-anonymous-2.json"))
        .isTrue();
    assertThat(testConnectorDtoMapping("appdynamics/appdynamics-1.json", "appdynamics/appdynamics-2.json")).isTrue();
    assertThat(
        testConnectorDtoMapping("appdynamics/appdynamics-client-id-1.json", "appdynamics/appdynamics-client-id-2.json"))
        .isTrue();
    assertThat(testConnectorDtoMapping("azure/azure-client-secret-key-1.json", "azure/azure-client-secret-key-2.json"))
        .isTrue();
    assertThat(
        testConnectorDtoMapping("azure/azure-client-certificate-1.json", "azure/azure-client-certificate-2.json"))
        .isTrue();
    assertThat(testConnectorDtoMapping("azure/azure-inherit-form-delegate-user-managed-identity-1.json",
                   "azure/azure-inherit-form-delegate-user-managed-identity-2.json"))
        .isTrue();
    assertThat(testConnectorDtoMapping("azure/azure-inherit-form-delegate-system-managed-identity-1.json",
                   "azure/azure-inherit-form-delegate-system-managed-identity-2.json"))
        .isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testResponseMapping() throws JsonProcessingException {
    assertThat(testResponseMapping("git/git-http-3.json", "git/git-http-4.json")).isTrue();
    assertThat(testResponseMapping("git/git-http-encrypted-3.json", "git/git-http-encrypted-4.json")).isTrue();
    assertThat(testResponseMapping("git/git-ssh-3.json", "git/git-ssh-4.json")).isTrue();
    assertThat(testResponseMapping("artifactory/artifactory-3.json", "artifactory/artifactory-4.json")).isTrue();
    assertThat(
        testResponseMapping("artifactory/artifactory-encrypted-3.json", "artifactory/artifactory-encrypted-4.json"))
        .isTrue();
    assertThat(
        testResponseMapping("artifactory/artifactory-anonymous-3.json", "artifactory/artifactory-anonymous-4.json"))
        .isTrue();
    assertThat(testResponseMapping("appdynamics/appdynamics-3.json", "appdynamics/appdynamics-4.json")).isTrue();
    assertThat(
        testResponseMapping("appdynamics/appdynamics-client-id-3.json", "appdynamics/appdynamics-client-id-4.json"))
        .isTrue();
    assertThat(testResponseMapping("azure/azure-client-certificate-3.json", "azure/azure-client-certificate-4.json"))
        .isTrue();
    assertThat(testResponseMapping("azure/azure-client-secret-key-3.json", "azure/azure-client-secret-key-4.json"))
        .isTrue();
    assertThat(testResponseMapping("azure/azure-inherit-form-delegate-system-managed-identity-3.json",
                   "azure/azure-inherit-form-delegate-system-managed-identity-4.json"))
        .isTrue();
    assertThat(testResponseMapping("azure/azure-inherit-form-delegate-user-managed-identity-3.json",
                   "azure/azure-inherit-form-delegate-user-managed-identity-4.json"))
        .isTrue();
  }
}
