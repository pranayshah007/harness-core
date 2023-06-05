/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.FQNMapGenerator;
import io.harness.pms.merger.helpers.YamlMapGenerator;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Data
@Slf4j
public class YamlConfig {
  private String yaml;
  private JsonNode yamlMap;
  private Map<FQN, Object> fqnToValueMap;

  public YamlConfig(String yaml) {
    this.yaml = yaml;
    try {
      yamlMap = YamlUtils.readTree(yaml).getNode().getCurrJsonNode();
    } catch (IOException e) {
      log.error("Could not convert yaml to JsonNode. Yaml:\n" + yaml, e);
      throw new InvalidRequestException("Could not convert yaml to JsonNode: " + e.getMessage());
    }
    fqnToValueMap = FQNMapGenerator.generateFQNMap(yamlMap);
  }

  public YamlConfig(String yaml, boolean checkDuplicate) {
    this.yaml = yaml;
    try {
      yamlMap = YamlUtils.readTree(yaml, checkDuplicate).getNode().getCurrJsonNode();
    } catch (MismatchedInputException e) {
      log.error("Could not convert yaml to JsonNode. Yaml:\n" + yaml, e);
      throw new InvalidYamlException(
          "Invalid Yaml: Duplicate field found in yaml at line: " + e.getLocation().getLineNr());
    } catch (IOException e) {
      log.error("Could not convert yaml to JsonNode. Yaml:\n" + yaml, e);
      throw new InvalidRequestException("Could not convert yaml to JsonNode: " + e.getMessage());
    }
    fqnToValueMap = FQNMapGenerator.generateFQNMap(yamlMap);
  }

  public YamlConfig(JsonNode jsonNode) {
    yamlMap = jsonNode;
    fqnToValueMap = FQNMapGenerator.generateFQNMap(yamlMap);
  }

  public YamlConfig(JsonNode jsonNode, boolean keepUuidFields) {
    yamlMap = jsonNode;
    fqnToValueMap = FQNMapGenerator.generateFQNMap(yamlMap, keepUuidFields);
  }

  public YamlConfig(Map<FQN, Object> fqnToValueMap, JsonNode originalYaml) {
    yamlMap = YamlMapGenerator.generateYamlMap(fqnToValueMap, originalYaml, false);
    // fqnToValueMap can be missing some values which need to be taken from originalYaml. These values are there in
    // yamlMap generated in the above line, hence the fqn map needs to be regenerated
    if (!yamlMap.isEmpty()) {
      this.fqnToValueMap = FQNMapGenerator.generateFQNMap(yamlMap);
    } else {
      this.fqnToValueMap = new LinkedHashMap<>();
    }
  }

  public YamlConfig(
      Map<FQN, Object> fqnToValueMap, JsonNode originalYaml, boolean isSanitiseFlow, boolean keepUuidFields) {
    yamlMap = YamlMapGenerator.generateYamlMap(fqnToValueMap, originalYaml, isSanitiseFlow, keepUuidFields);
    // fqnToValueMap can be missing some values which need to be taken from originalYaml. These values are there in
    // yamlMap generated in the above line, hence the fqn map needs to be regenerated
    if (!yamlMap.isEmpty()) {
      this.fqnToValueMap = FQNMapGenerator.generateFQNMap(yamlMap);
    } else {
      this.fqnToValueMap = new LinkedHashMap<>();
    }
  }

  public YamlConfig(Map<FQN, Object> fqnToValueMap, JsonNode originalYaml, boolean isSanitiseFlow) {
    this.fqnToValueMap = fqnToValueMap;
    yamlMap = YamlMapGenerator.generateYamlMap(fqnToValueMap, originalYaml, isSanitiseFlow);
  }

  public String getYaml() {
    if (isNotEmpty(yaml)) {
      return yaml;
    }
    if (!yamlMap.isEmpty()) {
      yaml = YamlUtils.writeYamlString(yamlMap);
    } else {
      yaml = null;
    }
    return yaml;
  }
}
