/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.utils;

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
}
