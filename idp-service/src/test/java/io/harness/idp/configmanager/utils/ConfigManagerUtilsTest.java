/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.utils;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class ConfigManagerUtilsTest extends CategoryTest {
  AutoCloseable openMocks;

  static final String TEST_JSON_STRING = "{\n"
      + "    \"proxy\": {\n"
      + "        \"/harness/prod\": {\n"
      + "            \"target\": \"https://app.harness.io/\",\n"
      + "            \"pathRewrite\": {\n"
      + "                \"/api/proxy/harness/prod/?\": \"/\"\n"
      + "            },\n"
      + "            \"allowedHeaders\": [\n"
      + "                \"authorization\"\n"
      + "            ]\n"
      + "        }\n"
      + "    }\n"
      + "}";

  static final String TEST_INVALID_JSON_STRING = "{\n"
      + "    \"proxy\": {\n"
      + "        \"/harness/prod\": {\n"
      + "            \"target\": \"https://app.harness.io/\",\n"
      + "            \"pathRewrite\": {\n"
      + "                \"/api/proxy/harness/prod/?\": \"/\"\n"
      + "            }\n"
      + "            \"allowedHeaders\": [\n"
      + "                \"authorization\"\n"
      + "            ]\n"
      + "        }\n"
      + "    }\n"
      + "}";

  static final String TEST_YAML_STRING = "---\n"
      + "proxy:\n"
      + "  /harness/prod:\n"
      + "    target: https://app.harness.io/\n"
      + "    pathRewrite:\n"
      + "      /api/proxy/harness/prod/?: /\n"
      + "    allowedHeaders:\n"
      + "    - authorization\n";

  static final String TEST_INVALID_YAML_STRING = "---\n"
      + "proxy:\n"
      + "  /harness/prod:\n"
      + "    target: https://app.harness.io/\n"
      + "    pathRewrite:\n"
      + "      /api/proxy/harness/prod/?: /\n"
      + "    allowedHeaders: ,authorization\n";

  static final String TEST_JSON_NODE = "{\"proxy\":{\"/harness/prod\":{\"target\":\"https://app.harness.io/\"}}}";

  static final String TEST_YAML_FOR_JSON_NODE = "proxy:\n"
      + "  '/harness/prod':\n"
      + "    target: 'https://app.harness.io/'";
  static final String TEST_INVALID_CONNECTOR_TYPE = "TEST_GITHUB";

  static final String TEST_JSON_SCHEMA =
      "\"#\" : {\"proxy\":{\"/harness/prod\":{\"target\":\"https://app.harness.io/\"}}}";

  static final String TEST_GITHUB_CONNECTOR_TYPE = "Github";
  static final String TEST_GITHUB_APP_CONNECTOR_TYPE = "Github_App";
  static final String TEST_GITLAB_CONNECTOR_TYPE = "Gitlab";

  static final String TEST_AZURE_REPO_CONNECTOR_TYPE = "AzureRepo";

  static final String TEST_BITBUCKET_CLOUD_CONNECTOR_TYPE = "Bitbucket_Cloud";

  static final String TEST_BITBUCKET_SERVER_AUTH_CONNECTOR_TYPE = "Bitbucket_Server_Auth";

  static final String TEST_BITBUCKET_SERVER_PAT_CONNECTOR_TYPE = "Bitbucket_Server_Pat";

  static final String TEST_PAGER_DUTY_CONFIG_ID = "pager-duty";
  static final String TEST_KAFKA_CONFIG_ID = "kafka";
  static final String TEST_SNYK_SECURITY_CONFIG_ID = "snyk-security";
  static final String TEST_CIRCLE_CI_CONFIG_ID = "circleci";
  static final String TEST_JENKINS_CONFIG_ID = "jenkins";
  static final String TEST_LIGHTHOUSE_CONFIG_ID = "lighthouse";

  static final String TEST_JIRA_CONFIG_ID = "jira";
  static final String TEST_FIRE_HYDRANT_CONFIG_ID = "firehydrant";

  static final String TEST_HARNESS_CI_CD_CONFIG_ID = "harness-ci-cd";

  static final String TEST_KUBERNETES_CONFIG_ID = "kubernetes";

  static final String TEST_INVALID_VALUE = "invalid";

  static final String TEST_NODE_NAME = "proxy";
  static final String TEST_QA_ENV_NAME = "qa";
  static final String TEST_STRESS_ENV_NAME = "stress";

  static final String TEST_DEFAULT_ENV_NAME = "default";

  static final String TEST_COMPLIANCE_ENV_NAME = "compliance";

  static final String TEST_GITHUB_AUTH_CONFIG_ID = "github-auth";
  static final String TEST_GOOGLE_AUTH_CONFIG = "google-auth";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testAsYamlWithValidJson() {
    String asYaml = ConfigManagerUtils.asYaml(TEST_JSON_STRING);
    System.out.println(asYaml);
    assertEquals(TEST_YAML_STRING, asYaml);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testAsYamlWithInvalidJson() {
    Exception exception = null;
    try {
      ConfigManagerUtils.asYaml(TEST_INVALID_JSON_STRING);
    } catch (Exception e) {
      exception = e;
    }
    assertNotNull(exception);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testAsJsonNodeWithValidYaml() {
    JsonNode jsonNode = ConfigManagerUtils.asJsonNode(TEST_YAML_FOR_JSON_NODE);
    assertEquals(TEST_JSON_NODE, jsonNode.toString());
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testAsJsonNodeWithInValidYaml() {
    ConfigManagerUtils.asJsonNode(TEST_INVALID_YAML_STRING);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetIntegrationConfigBasedOnValidConnectorType() {
    assertNotNull(ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(TEST_GITHUB_CONNECTOR_TYPE));
    assertNotNull(ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(TEST_GITHUB_APP_CONNECTOR_TYPE));
    assertNotNull(ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(TEST_GITLAB_CONNECTOR_TYPE));
    assertNotNull(ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(TEST_AZURE_REPO_CONNECTOR_TYPE));
    assertNotNull(ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(TEST_BITBUCKET_CLOUD_CONNECTOR_TYPE));
    assertNotNull(
        ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(TEST_BITBUCKET_SERVER_AUTH_CONNECTOR_TYPE));
    assertNotNull(
        ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(TEST_BITBUCKET_SERVER_PAT_CONNECTOR_TYPE));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetIntegrationConfigBasedOnInValidConnectorType() {
    String integrationConfig = ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(TEST_INVALID_CONNECTOR_TYPE);
    assertNull(integrationConfig);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetJsonSchemaFromJsonNode() {
    JsonNode jsonNode = ConfigManagerUtils.asJsonNode(TEST_YAML_FOR_JSON_NODE);
    JsonSchema jsonSchema = ConfigManagerUtils.getJsonSchemaFromJsonNode(jsonNode);
    assertTrue(jsonSchema.toString().equals(TEST_JSON_SCHEMA));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetJsonSchemaFromJsonNodeForException() {
    JsonNode jsonNode = null;
    ConfigManagerUtils.getJsonSchemaFromJsonNode(jsonNode);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testValidateSchemaFromYaml() throws Exception {
    String jsonSchemaString =
        ConfigManagerUtils.getJsonSchemaBasedOnConnectorTypeForIntegrations(TEST_GITHUB_CONNECTOR_TYPE);
    ObjectMapper objectMapper = new ObjectMapper();
    JsonSchema jsonSchema = ConfigManagerUtils.getJsonSchemaFromJsonNode(objectMapper.readTree(jsonSchemaString));
    String yaml = ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(TEST_GITHUB_CONNECTOR_TYPE);
    Set<String> validation = ConfigManagerUtils.validateSchemaForYaml(yaml, jsonSchema);
    assertEquals(validation.size(), 0);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testValidateSchemaFromInValidYaml() throws Exception {
    String jsonSchemaString =
        ConfigManagerUtils.getJsonSchemaBasedOnConnectorTypeForIntegrations(TEST_GITHUB_CONNECTOR_TYPE);
    ObjectMapper objectMapper = new ObjectMapper();
    JsonSchema jsonSchema = ConfigManagerUtils.getJsonSchemaFromJsonNode(objectMapper.readTree(jsonSchemaString));
    String yaml = ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType("Github_App");
    Set<String> validation = ConfigManagerUtils.validateSchemaForYaml(yaml, jsonSchema);
    assertFalse(validation.isEmpty());
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testValidateSchemaFromInValidValueOfYaml() throws Exception {
    String jsonSchemaString =
        ConfigManagerUtils.getJsonSchemaBasedOnConnectorTypeForIntegrations(TEST_GITHUB_CONNECTOR_TYPE);
    ObjectMapper objectMapper = new ObjectMapper();
    JsonSchema jsonSchema = ConfigManagerUtils.getJsonSchemaFromJsonNode(objectMapper.readTree(jsonSchemaString));
    String yaml = "}{";
    ConfigManagerUtils.validateSchemaForYaml(yaml, jsonSchema);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetJsonSchemaBasedOnConnectorTypeForIntegrations() {
    assertNotNull(ConfigManagerUtils.getJsonSchemaBasedOnConnectorTypeForIntegrations(TEST_GITHUB_CONNECTOR_TYPE));
    assertNotNull(ConfigManagerUtils.getJsonSchemaBasedOnConnectorTypeForIntegrations(TEST_GITHUB_APP_CONNECTOR_TYPE));
    assertNotNull(ConfigManagerUtils.getJsonSchemaBasedOnConnectorTypeForIntegrations(TEST_GITLAB_CONNECTOR_TYPE));
    assertNotNull(ConfigManagerUtils.getJsonSchemaBasedOnConnectorTypeForIntegrations(TEST_AZURE_REPO_CONNECTOR_TYPE));
    assertNotNull(
        ConfigManagerUtils.getJsonSchemaBasedOnConnectorTypeForIntegrations(TEST_BITBUCKET_CLOUD_CONNECTOR_TYPE));
    assertNotNull(
        ConfigManagerUtils.getJsonSchemaBasedOnConnectorTypeForIntegrations(TEST_BITBUCKET_SERVER_AUTH_CONNECTOR_TYPE));
    assertNotNull(
        ConfigManagerUtils.getJsonSchemaBasedOnConnectorTypeForIntegrations(TEST_BITBUCKET_SERVER_PAT_CONNECTOR_TYPE));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetJsonSchemaBasedOnConnectorTypeForIntegrationsInvalidConnectorType() {
    assertNull(ConfigManagerUtils.getJsonSchemaBasedOnConnectorTypeForIntegrations(TEST_INVALID_VALUE));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testIsValidSchema() {
    String yaml = ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(TEST_GITHUB_CONNECTOR_TYPE);
    String jsonSchemaString =
        ConfigManagerUtils.getJsonSchemaBasedOnConnectorTypeForIntegrations(TEST_GITHUB_CONNECTOR_TYPE);
    assertTrue(ConfigManagerUtils.isValidSchema(yaml, jsonSchemaString));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testIsValidSchemaInvalidYaml() {
    String yaml = ConfigManagerUtils.getIntegrationConfigBasedOnConnectorType(TEST_GITHUB_APP_CONNECTOR_TYPE);
    String jsonSchemaString =
        ConfigManagerUtils.getJsonSchemaBasedOnConnectorTypeForIntegrations(TEST_GITHUB_CONNECTOR_TYPE);
    assertFalse(ConfigManagerUtils.isValidSchema(yaml, jsonSchemaString));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetPluginConfigSchema() {
    assertNotNull(ConfigManagerUtils.getPluginConfigSchema(TEST_PAGER_DUTY_CONFIG_ID));
    assertNotNull(ConfigManagerUtils.getPluginConfigSchema(TEST_KAFKA_CONFIG_ID));
    assertNotNull(ConfigManagerUtils.getPluginConfigSchema(TEST_SNYK_SECURITY_CONFIG_ID));
    assertNotNull(ConfigManagerUtils.getPluginConfigSchema(TEST_CIRCLE_CI_CONFIG_ID));
    assertNotNull(ConfigManagerUtils.getPluginConfigSchema(TEST_JENKINS_CONFIG_ID));
    assertNotNull(ConfigManagerUtils.getPluginConfigSchema(TEST_LIGHTHOUSE_CONFIG_ID));
    assertNotNull(ConfigManagerUtils.getPluginConfigSchema(TEST_JIRA_CONFIG_ID));
    assertNotNull(ConfigManagerUtils.getPluginConfigSchema(TEST_FIRE_HYDRANT_CONFIG_ID));
    assertNotNull(ConfigManagerUtils.getPluginConfigSchema(TEST_HARNESS_CI_CD_CONFIG_ID));
    assertNotNull(ConfigManagerUtils.getPluginConfigSchema(TEST_KUBERNETES_CONFIG_ID));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetPluginConfigSchemaForInvalidId() {
    assertNull(ConfigManagerUtils.getPluginConfigSchema(TEST_INVALID_VALUE));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAuthConfig() {
    assertNotNull(ConfigManagerUtils.getAuthConfig(TEST_GITHUB_AUTH_CONFIG_ID));
    assertNotNull(ConfigManagerUtils.getAuthConfig(TEST_GOOGLE_AUTH_CONFIG));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAuthConfigInvalidConfigId() {
    assertNull(ConfigManagerUtils.getAuthConfig(TEST_INVALID_VALUE));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAuthConfigSchema() {
    assertNotNull(ConfigManagerUtils.getAuthConfigSchema(TEST_GITHUB_AUTH_CONFIG_ID));
    assertNotNull(ConfigManagerUtils.getAuthConfigSchema(TEST_GOOGLE_AUTH_CONFIG));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAuthConfigSchemaForInvalidId() {
    assertNull(ConfigManagerUtils.getAuthConfigSchema(TEST_INVALID_VALUE));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetHarnessCiCdAppConfig() {
    assertNotNull(ConfigManagerUtils.getHarnessCiCdAppConfig(TEST_QA_ENV_NAME));
    assertNotNull(ConfigManagerUtils.getHarnessCiCdAppConfig(TEST_STRESS_ENV_NAME));
    assertNotNull(ConfigManagerUtils.getHarnessCiCdAppConfig(TEST_COMPLIANCE_ENV_NAME));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetHarnessCiCdAppConfigForDefaultEnv() {
    assertNotNull(ConfigManagerUtils.getHarnessCiCdAppConfig(TEST_DEFAULT_ENV_NAME));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetNodeByName() {
    assertNotNull(ConfigManagerUtils.getNodeByName(ConfigManagerUtils.asJsonNode(TEST_YAML_STRING), TEST_NODE_NAME));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetNodeByInvalidName() {
    assertNull(ConfigManagerUtils.getNodeByName(ConfigManagerUtils.asJsonNode(TEST_YAML_STRING), TEST_INVALID_VALUE));
  }
}
