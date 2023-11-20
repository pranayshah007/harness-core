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
import io.harness.exception.UnexpectedException;
import io.harness.idp.common.Constants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.Iterator;
import java.util.Map;
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

  private static final String KAFKA_PLUGIN_JSON_SCHEMA_PATH = "configs/json-schemas/kafka-schema.json";

  private static final String PAGER_DUTY_PLUGIN_JSON_SCHEMA_PATH = "configs/json-schemas/pager-duty-schema.json";

  private static final String SNYK_SECURITY_PLUGIN_JSON_SCHEMA_PATH = "configs/json-schemas/snyk-security-schema.json";

  private static final String CIRCLE_CI_PLUGIN_JSON_SCHEMA_PATH = "configs/json-schemas/circleci-schema.json";
  private static final String CONFLUENCE_PLUGIN_JSON_SCHEMA_PATH = "configs/json-schemas/confluence-schema.json";

  private static final String JENKINS_PLUGIN_JSON_SCHEMA_PATH = "configs/json-schemas/jenkins-schema.json";

  private static final String LIGHTHOUSE_PLUGIN_JSON_SCHEMA_PATH = "configs/json-schemas/lighthouse-schema.json";

  private static final String JIRA_PLUGIN_JSON_SCHEMA_PATH = "configs/json-schemas/jira-schema.json";
  private static final String FIREHYDRANT_PLUGIN_JSON_SCHEMA_PATH = "configs/json-schemas/firehydrant-schema.json";
  private static final String HARNESS_CI_CD_JSON_SCHEMA_PATH = "configs/json-schemas/harness-ci-cd-schema.json";

  private static final String KUBERNETES_JSON_SCHEMA_PATH = "configs/json-schemas/kubernetes-schema.json";
  private static final String GITHUB_CATALOG_DISCOVERY_JSON_SCHEMA_PATH =
      "configs/json-schemas/github-catalog-discovery-schema.json";
  private static final String GRAFANA_JSON_SCHEMA_PATH = "configs/json-schemas/grafana-schema.json";
  private static final String OPSGENIE_JSON_SCHEMA_PATH = "configs/json-schemas/opsgenie-schema.json";
  private static final String SONARQUBE_JSON_SCHEMA_PATH = "configs/json-schemas/sonarqube-schema.json";
  private static final String HARNESS_CI_CD_CONFIG_PATH = "configs/plugins/harness-ci-cd.yaml";
  private static final String HARNESS_CI_CD_CONFIG_PATH_COMPLIANCE = "configs/plugins/harness-ci-cd-compliance.yaml";
  private static final String HARNESS_CI_CD_CONFIG_PATH_PRE_QA = "configs/plugins/harness-ci-cd-preqa.yaml";
  private static final String HARNESS_CI_CD_CONFIG_PATH_QA = "configs/plugins/harness-ci-cd-qa.yaml";
  private static final String GITHUB_AUTH_CONFIG_FILE = "configs/auth/github-auth.yaml";
  private static final String GITHUB_AUTH_JSON_SCHEMA_FILE = "configs/auth/json-schemas/github-auth-schema.json";
  private static final String GOOGLE_AUTH_CONFIG_FILE = "configs/auth/google-auth.yaml";
  private static final String GOOGLE_AUTH_JSON_SCHEMA_FILE = "configs/auth/json-schemas/google-auth-schema.json";

  public String asYaml(String jsonString) {
    JsonNode jsonNodeTree = getJsonNodeForJsonString(jsonString);

    String jsonAsYaml = null;
    try {
      jsonAsYaml =
          new YAMLMapper().configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true).writeValueAsString(jsonNodeTree);
    } catch (Exception e) {
      log.error("Error in converting json to yaml. Error - {}", e.getMessage(), e);
      throw new UnexpectedException(e.getMessage());
    }

    return jsonAsYaml;
  }

  public JsonNode asJsonNode(String yamlString) {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    JsonNode jsonNode = mapper.createObjectNode();
    if (yamlString != null) {
      try {
        jsonNode = mapper.readTree(yamlString);
      } catch (Exception e) {
        log.error("Error in reading the ymlString as json node. Error - {}", e.getMessage(), e);
        throw new UnexpectedException(e.getMessage());
      }
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

  public Set<String> validateSchemaForYaml(String yaml, JsonSchema schema) {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    JsonNode jsonNode = null;
    try {
      jsonNode = mapper.readTree(yaml);
    } catch (Exception e) {
      log.error("Error in converting yaml to json node. Error - {}", e.getMessage(), e);
      throw new UnexpectedException(e.getMessage());
    }
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

  public Boolean isValidSchema(String yaml, String jsonSchema) {
    JsonSchema schema = getJsonSchemaFromJsonNode(getJsonNodeForJsonString(jsonSchema));
    Set<String> invalidSchemaResponse = validateSchemaForYaml(yaml, schema);
    return (invalidSchemaResponse.size() <= 0);
  }

  public String getPluginConfigSchema(String configId) {
    switch (configId) {
      case Constants.KAFKA_PLUGIN:
        return readFileFromClassPath(KAFKA_PLUGIN_JSON_SCHEMA_PATH);
      case Constants.PAGER_DUTY_PLUGIN:
        return readFileFromClassPath(PAGER_DUTY_PLUGIN_JSON_SCHEMA_PATH);
      case Constants.SYNK_SECURITY_PLUGIN:
        return readFileFromClassPath(SNYK_SECURITY_PLUGIN_JSON_SCHEMA_PATH);
      case Constants.CIRCLE_CI_PLUGIN:
        return readFileFromClassPath(CIRCLE_CI_PLUGIN_JSON_SCHEMA_PATH);
      case Constants.CONFLUENCE_PLUGIN:
        return readFileFromClassPath(CONFLUENCE_PLUGIN_JSON_SCHEMA_PATH);
      case Constants.JENKINS_PLUGIN:
        return readFileFromClassPath(JENKINS_PLUGIN_JSON_SCHEMA_PATH);
      case Constants.LIGHTHOUSE_PLUGIN:
        return readFileFromClassPath(LIGHTHOUSE_PLUGIN_JSON_SCHEMA_PATH);
      case Constants.JIRA_PLUGIN:
        return readFileFromClassPath(JIRA_PLUGIN_JSON_SCHEMA_PATH);
      case Constants.FIRE_HYDRANT_PLUGIN:
        return readFileFromClassPath(FIREHYDRANT_PLUGIN_JSON_SCHEMA_PATH);
      case Constants.HARNESS_CI_CD_PLUGIN:
        return readFileFromClassPath(HARNESS_CI_CD_JSON_SCHEMA_PATH);
      case Constants.KUBERNETES_PLUGIN:
        return readFileFromClassPath(KUBERNETES_JSON_SCHEMA_PATH);
      case Constants.GITHUB_CATALOG_DISCOVERY_PLUGIN:
        return readFileFromClassPath(GITHUB_CATALOG_DISCOVERY_JSON_SCHEMA_PATH);
      case Constants.GRAFANA_PLUGIN:
        return readFileFromClassPath(GRAFANA_JSON_SCHEMA_PATH);
      case Constants.SONARQUBE_PLUGIN:
        return readFileFromClassPath(SONARQUBE_JSON_SCHEMA_PATH);
      case Constants.OPSGENIE_PLUGIN:
        return readFileFromClassPath(OPSGENIE_JSON_SCHEMA_PATH);
      default:
        return null;
    }
  }

  public String getAuthConfig(String authId) {
    switch (authId) {
      case Constants.GITHUB_AUTH:
        return readFileFromClassPath(GITHUB_AUTH_CONFIG_FILE);
      case Constants.GOOGLE_AUTH:
        return readFileFromClassPath(GOOGLE_AUTH_CONFIG_FILE);
      default:
        return null;
    }
  }

  public String getAuthConfigSchema(String authId) {
    switch (authId) {
      case Constants.GITHUB_AUTH:
        return readFileFromClassPath(GITHUB_AUTH_JSON_SCHEMA_FILE);
      case Constants.GOOGLE_AUTH:
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

  public JsonNode getNodeByName(JsonNode node, String name) {
    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fieldsIterator = node.fields();
      while (fieldsIterator.hasNext()) {
        Map.Entry<String, JsonNode> entry = fieldsIterator.next();
        if (entry.getKey().equals(name)) {
          return entry.getValue();
        } else {
          JsonNode result = getNodeByName(entry.getValue(), name);
          if (result != null) {
            return result;
          }
        }
      }
    } else if (node.isArray()) {
      for (JsonNode childNode : node) {
        JsonNode result = getNodeByName(childNode, name);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  private JsonNode getJsonNodeForJsonString(String jsonString) {
    JsonNode jsonNodeTree = null;
    try {
      jsonNodeTree = new ObjectMapper().readTree(jsonString);
    } catch (Exception e) {
      log.error("Error in reading the ymlString as json node. Error - {}", e.getMessage(), e);
      throw new UnexpectedException(e.getMessage());
    }
    return jsonNodeTree;
  }
}
