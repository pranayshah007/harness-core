/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.utils;

import static io.harness.idp.common.CommonUtils.readFileFromClassPath;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
@Slf4j
public class ConfigManagerUtils {
  private static final String GITHUB_CONFIG_FILE = "configs/integrations/github.yaml";
  private static final String GITHUB_APP_CONFIG_FILE = "configs/integrations/github-app.yaml";
  private static final String GITLAB_CONFIG_FILE = "configs/integrations/gitlab.yaml";
  private static final String BITBUCKET_CLOUD_CONFIG_FILE = "configs/integrations/bitbucket-cloud.yaml";
  private static final String BITBUCKET_SERVER_BASIC_AUTH_CONFIG_FILE =
      "configs/integrations/bitbucket-server-basic-auth.yaml";
  private static final String BITBUCKET_SERVER_PAT_CONFIG_FILE = "configs/integrations/bitbucket-server-pat.yaml";
  private static final String AZURE_CONFIG_FILE = "configs/integrations/azure.yaml";
  private static final String GITHUB_JSON_SCHEMA_FILE = "configs/integrations/json-schemas/github-schema.json";
  private static final String GITHUB_APP_JSON_SCHEMA_FILE = "configs/integrations/json-schemas/github-app-schema.json";
  private static final String GITLAB_JSON_SCHEMA_FILE = "configs/integrations/json-schemas/gitlab-schema.json";
  private static final String BITBUCKET_JSON_SCHEMA_FILE = "configs/integrations/json-schemas/bitbucket-schema.json";
  private static final String BITBUCKET_SERVER_BASIC_AUTH_JSON_SCHEMA_FILE =
      "configs/integrations/json-schemas/bitbucket-server-basic-auth-schema.json";
  private static final String BITBUCKET_SERVER_PAT_JSON_SCHEMA_FILE =
      "configs/integrations/json-schemas/bitbucket-server-pat-schema.json";
  private static final String AZURE_JSON_SCHEMA_FILE = "configs/integrations/json-schemas/azure-schema.json";

  private static final String KAFKA_PLUGIN_CONFIG_PATH = "configs/kafka.yaml";

  private static final String PAGER_DUTY_PLUGIN_CONFIG = "configs/pager-duty.yaml";

  private static final String SNYK_SECURITY_PLUGIN_CONFIG = "configs/snyk-security.yaml";

  private static final String KAFKA_PLUGIN_JSON_SCHEMA_PATH = "configs/json-schemas/kafka-schema.json";

  private static final String PAGER_DUTY_PLUGIN_JSON_SCHEMA_PATH = "configs/json-schemas/pager-duty-schema.json";

  private static final String SNYK_SECURITY_PLUGIN_JSON_SCHEMA_PATH = "configs/json-schemas/snyk-security-schema.json";

  private static final String CIRCLE_CI_PLUGIN_JSON_SCHEMA_PATH = "configs/json-schemas/circleci-schema.json";

  private static final String JENKINS_PLUGIN_JSON_SCHEMA_PATH = "configs/json-schemas/jenkins-schema.json";

  private static final String LIGHTHOUSE_PLUGIN_JSON_SCHEMA_PATH = "configs/json-schemas/lighthouse-schema.json";

  private static final String JIRA_PLUGIN_JSON_SCHEMA_PATH = "configs/json-schemas/jira-schema.json";
  private static final String FIREHYDRANT_PLUGIN_JSON_SCHEMA_PATH = "configs/json-schemas/firehydrant-schema.json";
  private static final String HARNESS_CI_CD_JSON_SCHEMA_PATH = "configs/json-schemas/harness-ci-cd-schema.json";

  private static final String KUBERNETES_JSON_SCHEMA_PATH = "configs/json-schemas/kubernetes-schema.json";
  private static final String HARNESS_CI_CD_CONFIG_PATH = "configs/plugins/harness-ci-cd.yaml";
  private static final String HARNESS_CI_CD_CONFIG_PATH_COMPLIANCE = "configs/plugins/harness-ci-cd-compliance.yaml";
  private static final String HARNESS_CI_CD_CONFIG_PATH_PRE_QA = "configs/plugins/harness-ci-cd-preqa.yaml";
  private static final String HARNESS_CI_CD_CONFIG_PATH_QA = "configs/plugins/harness-ci-cd-qa.yaml";
  private static final String GITHUB_AUTH_CONFIG_FILE = "configs/auth/github-auth.yaml";
  private static final String GITHUB_AUTH_JSON_SCHEMA_FILE = "configs/auth/json-schemas/github-auth-schema.json";
  private static final String GOOGLE_AUTH_CONFIG_FILE = "configs/auth/google-auth.yaml";
  private static final String GOOGLE_AUTH_JSON_SCHEMA_FILE = "configs/auth/json-schemas/google-auth-schema.json";

  public String asYaml(String jsonString) throws IOException {
    JsonNode jsonNodeTree = new ObjectMapper().readTree(jsonString);
    String jsonAsYaml =
        new YAMLMapper().configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true).writeValueAsString(jsonNodeTree);
    return jsonAsYaml;
  }

  public JsonNode asJsonNode(String yamlString) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    JsonNode jsonNode = mapper.createObjectNode();
    if (yamlString != null) {
      jsonNode = mapper.readTree(yamlString);
    }
    return jsonNode;
  }

  public String getIntegrationConfigBasedOnConnectorType(String connectorType) {
    switch (connectorType) {
      case "Github":
        return readFileFromClassPath(GITHUB_CONFIG_FILE);
      case "Github_App":
        return readFileFromClassPath(GITHUB_APP_CONFIG_FILE);
      case "Gitlab":
        return readFileFromClassPath(GITLAB_CONFIG_FILE);
      case "AzureRepo":
        return readFileFromClassPath(AZURE_CONFIG_FILE);
      case "Bitbucket_Cloud":
        return readFileFromClassPath(BITBUCKET_CLOUD_CONFIG_FILE);
      case "Bitbucket_Server_Auth":
        return readFileFromClassPath(BITBUCKET_SERVER_BASIC_AUTH_CONFIG_FILE);
      case "Bitbucket_Server_Pat":
        return readFileFromClassPath(BITBUCKET_SERVER_PAT_CONFIG_FILE);
      default:
        return null;
    }
  }

  public JsonSchema getJsonSchemaFromJsonNode(JsonNode schema) {
    JsonSchemaFactory factory =
        JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)).build();
    try {
      return factory.getSchema(schema);
    } catch (Exception e) {
      throw new InvalidRequestException("Couldn't parse schema", e);
    }
  }

  public Set<String> validateSchemaForYaml(String yaml, JsonSchema schema) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    JsonNode jsonNode = mapper.readTree(yaml);
    Set<ValidationMessage> validateMsg = schema.validate(jsonNode);
    return validateMsg.stream().map(ValidationMessage::getMessage).collect(Collectors.toSet());
  }

  public String getJsonSchemaBasedOnConnectorTypeForIntegrations(String connectorType) {
    switch (connectorType) {
      case "Github":
        return readFileFromClassPath(GITHUB_JSON_SCHEMA_FILE);
      case "Github_App":
        return readFileFromClassPath(GITHUB_APP_JSON_SCHEMA_FILE);
      case "Gitlab":
        return readFileFromClassPath(GITLAB_JSON_SCHEMA_FILE);
      case "AzureRepo":
        return readFileFromClassPath(AZURE_JSON_SCHEMA_FILE);
      case "Bitbucket_Cloud":
        return readFileFromClassPath(BITBUCKET_JSON_SCHEMA_FILE);
      case "Bitbucket_Server_Auth":
        return readFileFromClassPath(BITBUCKET_SERVER_BASIC_AUTH_JSON_SCHEMA_FILE);
      case "Bitbucket_Server_Pat":
        return readFileFromClassPath(BITBUCKET_SERVER_PAT_JSON_SCHEMA_FILE);
      default:
        return null;
    }
  }

  public Boolean isValidSchema(String yaml, String jsonSchema) throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonSchema schema = getJsonSchemaFromJsonNode(objectMapper.readTree(jsonSchema));
    Set<String> invalidSchemaResponse = validateSchemaForYaml(yaml, schema);
    return (invalidSchemaResponse.size() <= 0);
  }

  public String getPluginConfig(String configId) {
    switch (configId) {
      case "kafka":
        return readFileFromClassPath(KAFKA_PLUGIN_CONFIG_PATH);
      case "pager-duty":
        return readFileFromClassPath(PAGER_DUTY_PLUGIN_CONFIG);
      case "snyk-security":
        return readFileFromClassPath(SNYK_SECURITY_PLUGIN_CONFIG);
      default:
        return null;
    }
  }

  public String getPluginConfigSchema(String configId) {
    switch (configId) {
      case "kafka":
        return readFileFromClassPath(KAFKA_PLUGIN_JSON_SCHEMA_PATH);
      case "pager-duty":
        return readFileFromClassPath(PAGER_DUTY_PLUGIN_JSON_SCHEMA_PATH);
      case "snyk-security":
        return readFileFromClassPath(SNYK_SECURITY_PLUGIN_JSON_SCHEMA_PATH);
      case "circleci":
        return readFileFromClassPath(CIRCLE_CI_PLUGIN_JSON_SCHEMA_PATH);
      case "jenkins":
        return readFileFromClassPath(JENKINS_PLUGIN_JSON_SCHEMA_PATH);
      case "lighthouse":
        return readFileFromClassPath(LIGHTHOUSE_PLUGIN_JSON_SCHEMA_PATH);
      case "jira":
        return readFileFromClassPath(JIRA_PLUGIN_JSON_SCHEMA_PATH);
      case "firehydrant":
        return readFileFromClassPath(FIREHYDRANT_PLUGIN_JSON_SCHEMA_PATH);
      case "harness-ci-cd":
        return readFileFromClassPath(HARNESS_CI_CD_JSON_SCHEMA_PATH);
      case "kubernetes":
        return readFileFromClassPath(KUBERNETES_JSON_SCHEMA_PATH);
      default:
        return null;
    }
  }

  public String getAuthConfig(String authId) {
    switch (authId) {
      case "github-auth":
        return readFileFromClassPath(GITHUB_AUTH_CONFIG_FILE);
      case "google-auth":
        return readFileFromClassPath(GOOGLE_AUTH_CONFIG_FILE);
      default:
        return null;
    }
  }

  public String getAuthConfigSchema(String authId) {
    switch (authId) {
      case "github-auth":
        return readFileFromClassPath(GITHUB_AUTH_JSON_SCHEMA_FILE);
      case "google-auth":
        return readFileFromClassPath(GOOGLE_AUTH_JSON_SCHEMA_FILE);
      default:
        return null;
    }
  }

  public String getHarnessCiCdAppConfig(String env) {
    switch (env) {
      case "qa":
        return readFileFromClassPath(HARNESS_CI_CD_CONFIG_PATH_QA);
      case "stress":
        return readFileFromClassPath(HARNESS_CI_CD_CONFIG_PATH_PRE_QA);
      case "compliance":
        return readFileFromClassPath(HARNESS_CI_CD_CONFIG_PATH_COMPLIANCE);
      default:
        return readFileFromClassPath(HARNESS_CI_CD_CONFIG_PATH);
    }
  }
}
