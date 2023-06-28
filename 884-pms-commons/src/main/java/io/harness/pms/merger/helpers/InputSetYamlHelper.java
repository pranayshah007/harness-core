/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.common.EntityYamlRootNames;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.exception.UnexpectedException;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class InputSetYamlHelper {
  public JsonNode getPipelineComponent(JsonNode inputSetYaml) {
    if (isEmpty(inputSetYaml)) {
      return inputSetYaml;
    }
    JsonNode node = inputSetYaml;
    ObjectNode innerMap = (ObjectNode) node.get("inputSet");
    if (innerMap == null) {
      log.error("Yaml provided is not an input set yaml. Yaml:\n" + inputSetYaml);
      throw new InvalidRequestException("Yaml provided is not an input set yaml.");
    }
    JsonNode pipelineNode = innerMap.get("pipeline");
    innerMap.removeAll();
    innerMap.putObject("pipeline");
    innerMap.set("pipeline", pipelineNode);
    return innerMap;
  }

  public String getPipelineComponent(String inputSetYaml) {
    try {
      if (EmptyPredicate.isEmpty(inputSetYaml)) {
        return inputSetYaml;
      }
      JsonNode node = YamlUtils.readTree(inputSetYaml).getNode().getCurrJsonNode();
      ObjectNode innerMap = (ObjectNode) node.get("inputSet");
      if (innerMap == null) {
        log.error("Yaml provided is not an input set yaml. Yaml:\n" + inputSetYaml);
        throw new InvalidRequestException("Yaml provided is not an input set yaml.");
      }
      JsonNode pipelineNode = innerMap.get("pipeline");
      innerMap.removeAll();
      innerMap.putObject("pipeline");
      innerMap.set("pipeline", pipelineNode);
      return YamlUtils.writeYamlString(innerMap);
    } catch (IOException e) {
      log.error("Input set yaml is invalid. Yaml:\n" + inputSetYaml);
      throw new InvalidYamlException("Input set yaml is invalid", e);
    }
  }

  public String setPipelineComponent(String inputSetYaml, String pipelineComponent) {
    try {
      if (EmptyPredicate.isEmpty(inputSetYaml)) {
        return inputSetYaml;
      }
      JsonNode node = YamlUtils.readTree(inputSetYaml).getNode().getCurrJsonNode();
      ObjectNode innerMap = (ObjectNode) node.get("inputSet");
      if (innerMap == null) {
        log.error("Yaml provided is not an input set yaml. Yaml:\n" + inputSetYaml);
        throw new InvalidRequestException("Yaml provided is not an input set yaml.");
      }
      innerMap.set("pipeline", YamlUtils.readTree(pipelineComponent).getNode().getCurrJsonNode().get("pipeline"));
      return YamlUtils.writeYamlString(node);
    } catch (IOException e) {
      log.error("Input set yaml is invalid. Yaml:\n" + inputSetYaml);
      throw new InvalidYamlException("Input set yaml is invalid", e);
    }
  }

  public String getStringField(String yaml, String fieldName, String rootNode) {
    YamlConfig config;
    try {
      config = new YamlConfig(yaml);
    } catch (Exception e) {
      log.error("Input set yaml is invalid. Yaml:\n" + yaml);
      throw new InvalidRequestException("Input set yaml is invalid", e);
    }
    JsonNode node = config.getYamlMap();
    JsonNode innerMap = node.get(rootNode);
    if (innerMap == null) {
      log.error("Root node is not " + rootNode + ". Yaml:\n" + yaml);
      throw new InvalidRequestException("Root node is not " + rootNode);
    }
    JsonNode field = innerMap.get(fieldName);
    if (field == null) {
      return null;
    }
    return innerMap.get(fieldName).asText().equals("") ? null : innerMap.get(fieldName).asText();
  }

  public boolean isPipelineAbsent(String yaml) {
    JsonNode node = (new YamlConfig(yaml)).getYamlMap();
    JsonNode innerMap = node.get("inputSet");
    if (innerMap == null) {
      log.error("Yaml provided is not an input set yaml. Yaml:\n" + yaml);
      throw new InvalidRequestException("Yaml provided is not an input set yaml.");
    }
    JsonNode field = innerMap.get("pipeline");
    return field == null || field.toString().equals("{}");
  }

  public Map<String, String> getTags(String yaml, String rootNode) {
    JsonNode node = (new YamlConfig(yaml)).getYamlMap();
    JsonNode innerMap = node.get(rootNode);
    if (innerMap == null) {
      log.error("Root node is not " + rootNode + ". Yaml:\n" + yaml);
      throw new InvalidRequestException("Root node is not " + rootNode);
    }
    ObjectNode tags = (ObjectNode) innerMap.get("tags");
    if (tags == null) {
      return null;
    }
    Map<String, String> res = new LinkedHashMap<>();

    Set<String> fieldNames = new LinkedHashSet<>();
    tags.fieldNames().forEachRemaining(fieldNames::add);
    for (String key : fieldNames) {
      String value = tags.get(key).asText();
      res.put(key, value);
    }
    return res;
  }

  /**
   * If the yaml is an input set, it returns "inputSet". If it is an overlay input set, it returns "overlayInputSet".
   * Throws an exception if it is any other kind of yaml
   */
  public String getRootNodeOfInputSetYaml(String yaml) {
    JsonNode node = (new YamlConfig(yaml)).getYamlMap();
    JsonNode innerMap = node.get("inputSet");
    if (innerMap == null) {
      innerMap = node.get("overlayInputSet");
      if (innerMap == null) {
        log.error("Yaml provided is neither an input set nor an overlay input set. Yaml:\n" + yaml);
        throw new InvalidRequestException("Yaml provided is neither an input set nor an overlay input set");
      }
      return "overlayInputSet";
    }
    return "inputSet";
  }

  public List<String> getReferencesFromOverlayInputSetYaml(String yaml) {
    JsonNode node = (new YamlConfig(yaml)).getYamlMap();
    JsonNode innerMap = node.get("overlayInputSet");
    if (innerMap == null) {
      log.error("Yaml provided is not an overlay input set yaml. Yaml:\n" + yaml);
      throw new InvalidRequestException("Yaml provided is not an overlay input set yaml.");
    }
    ArrayNode list = (ArrayNode) innerMap.get("inputSetReferences");
    List<String> res = new ArrayList<>();

    if (list == null) {
      throw new InvalidRequestException(
          "Input Set References cannot be empty. Please give valid Input Set References.");
    }
    list.forEach(element -> res.add(element.asText()));
    return res;
  }

  public String setReferencesFromOverlayInputSetYaml(String yaml, List<String> newReferences) {
    JsonNode newReferencesNode;
    JsonNode rootLevelNode;
    try {
      newReferencesNode = YamlUtils.readTree(newReferences.toString()).getNode().getCurrJsonNode();
      rootLevelNode = YamlUtils.readTree(yaml).getNode().getCurrJsonNode();
    } catch (IOException e) {
      throw new UnexpectedException("Unexpected Error while setting new references into Overlay Input Set");
    }
    ObjectNode innerJsonNode = (ObjectNode) rootLevelNode.get(EntityYamlRootNames.OVERLAY_INPUT_SET);
    innerJsonNode.set(YAMLFieldNameConstants.INPUT_SET_REFERENCES, newReferencesNode);
    return YamlUtils.writeYamlString(rootLevelNode);
  }

  public void confirmPipelineIdentifierInInputSet(String inputSetYaml, String pipelineIdentifier) {
    if (InputSetYamlHelper.isPipelineAbsent(inputSetYaml)) {
      throw new InvalidRequestException(
          "Input Set provides no values for any runtime input, or the pipeline has no runtime input");
    }
    String pipelineComponent = getPipelineComponent(inputSetYaml);
    String identifierInYaml = InputSetYamlHelper.getStringField(pipelineComponent, "identifier", "pipeline");
    if (EmptyPredicate.isEmpty(identifierInYaml)) {
      throw new InvalidRequestException(
          "Pipeline identifier is missing in the YAML. Please give a valid Pipeline identifier");
    }
    if (!pipelineIdentifier.equals(identifierInYaml)) {
      throw new InvalidRequestException("Pipeline identifier in input set does not match");
    }
  }

  public void confirmPipelineIdentifierInOverlayInputSet(String inputSetYaml, String pipelineIdentifier) {
    String identifierInYaml = InputSetYamlHelper.getStringField(inputSetYaml, "pipelineIdentifier", "overlayInputSet");
    if (EmptyPredicate.isEmpty(identifierInYaml)) {
      throw new InvalidRequestException(
          "Pipeline identifier is missing in the YAML. Please give a valid Pipeline identifier");
    }
    if (!pipelineIdentifier.equals(identifierInYaml)) {
      throw new InvalidRequestException("Pipeline identifier in input set does not match");
    }
  }

  public void confirmOrgAndProjectIdentifier(
      String yaml, String rootNode, String orgIdentifier, String projectIdentifier) {
    String orgIdInYaml = InputSetYamlHelper.getStringField(yaml, "orgIdentifier", rootNode);
    String projectIdInYaml = InputSetYamlHelper.getStringField(yaml, "projectIdentifier", rootNode);

    if (EmptyPredicate.isEmpty(orgIdInYaml)) {
      throw new InvalidRequestException(
          "Organization identifier is missing in the YAML. Please give a valid Organization identifier");
    }
    if (EmptyPredicate.isEmpty(projectIdInYaml)) {
      throw new InvalidRequestException(
          "Project identifier is missing in the YAML. Please give a valid Project identifier");
    }

    if (!orgIdentifier.equals(orgIdInYaml)) {
      throw new InvalidRequestException("Org identifier in input set does not match");
    }
    if (!projectIdentifier.equals(projectIdInYaml)) {
      throw new InvalidRequestException("Project identifier in input set does not match");
    }
  }
}
