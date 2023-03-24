/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.utils;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
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

  private static final String BITBUCKET_CONFIG_FILE = "configs/integrations/bitbucket.yaml";

  private static final String AZURE_CONFIG_FILE = "configs/integrations/azure.yaml";

  private static final String GITHUB_JSON_SCHEMA_FILE = "configs/integrations/github-schema.json";
  private static final String GITHUB_APP_JSON_SCHEMA_FILE = "configs/integrations/github-app-schema.json";

  private static final String GITLAB_JSON_SCHEMA_FILE = "configs/integrations/gitlab-schema.json";

  private static final String BITBUCKET_JSON_SCHEMA_FILE = "configs/integrations/bitbucket-schema.json";

  private static final String AZURE_JSON_SCHEMA_FILE = "configs/integrations/azure-schema.json";
  public String readFile(String filename) {
    ClassLoader classLoader = ClassLoader.getSystemClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename, e);
    }
  }

  public String asYaml(String jsonString) throws IOException {
    JsonNode jsonNodeTree = new ObjectMapper().readTree(jsonString);
    String jsonAsYaml =
        new YAMLMapper().configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true).writeValueAsString(jsonNodeTree);
    return jsonAsYaml;
  }

  public JsonNode asJsonNode(String yamlString) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    JsonNode jsonNode = mapper.readTree(yamlString);
    return jsonNode;
  }

  public String getIntegrationConfigBasedOnConnectorType(String connectorType) {
    switch (connectorType) {
      case "Github":
        return readFile(GITHUB_CONFIG_FILE);
      case "Github_App":
        return readFile(GITHUB_APP_CONFIG_FILE);
      case "Gitlab":
        return readFile(GITLAB_CONFIG_FILE);
      case "AzureRepo":
        return readFile(AZURE_CONFIG_FILE);
      case "Bitbucket":
        return readFile(BITBUCKET_CONFIG_FILE);
      default:
        return null;
    }
  }

  public JsonSchema getJsonSchemaFromJsonNode(JsonNode schema) {
    JsonSchemaFactory factory =
            JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)).build();
    try {
      final JsonSchema jsonSchema = factory.getSchema(schema);
      return jsonSchema;
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
        return readFile(GITHUB_JSON_SCHEMA_FILE);
      case "Github_App":
        return readFile(GITHUB_APP_JSON_SCHEMA_FILE);
      case "Gitlab":
        return readFile(GITLAB_JSON_SCHEMA_FILE);
      case "AzureRepo":
        return readFile(AZURE_JSON_SCHEMA_FILE);
      case "Bitbucket":
        return readFile(BITBUCKET_JSON_SCHEMA_FILE);
      default:
        return null;
    }
  }

  public Boolean isValidSchema(String yaml, String jsonSchema) throws Exception{
    ObjectMapper objectMapper = new ObjectMapper();
    JsonSchema schema = getJsonSchemaFromJsonNode(objectMapper.readTree(jsonSchema));
    Set<String> invalidSchemaResponse = validateSchemaForYaml(yaml, schema);
    return ((invalidSchemaResponse.size()>0) ? false : true);
  }
}
