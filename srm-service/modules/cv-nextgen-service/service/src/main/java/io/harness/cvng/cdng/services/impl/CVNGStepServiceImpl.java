/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import io.harness.common.NGExpressionUtils;
import io.harness.cvng.cdng.beans.CVNGStepType;
import io.harness.cvng.cdng.services.api.CVNGStepService;
import io.harness.pms.merger.helpers.InputSetTemplateHelper;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.collections4.IteratorUtils;
import org.jetbrains.annotations.NotNull;

public class CVNGStepServiceImpl implements CVNGStepService {
  private static final String TEMPLATE_YAML_KEY = "template";
  private static final String TEMPLATE_INPUTS_YAML_KEY = "templateInputs";
  private static final String IDENTIFIER_YAML_KEY = "identifier";
  private static final String PIPELINE_YAML_KEY = "pipeline";
  private static final String PATH_DELIMITER = ".";

  @Override
  public String getUpdatedInputSetTemplate(String pipelineYaml, String templateYaml) {
    try {
      YamlField pipeline = YamlUtils.readTree(pipelineYaml);
      for (YamlNode stage : getStageYamlNodes(pipeline)) {
        updateStageYamlWithDummyInputParam(stage.getField("stage").getNode());
      }
      String updatedPipelineYaml = YamlUtils.writeYamlString(pipeline);
      String updatedTemplate = InputSetTemplateHelper.createTemplateFromPipeline(updatedPipelineYaml);
      if (updatedTemplate != null) {
        YamlField updatedTemplateField = YamlUtils.readTree(updatedTemplate);
        removeDummyFieldFromTemplate(updatedTemplateField);
        if (Objects.nonNull(templateYaml)) {
          JsonNode jsonNode = getTemplateCorrectedYaml(updatedTemplateField, templateYaml);
          return YamlUtils.writeYamlString(new YamlField(new YamlNode(jsonNode)));
        } else {
          return YamlUtils.writeYamlString(updatedTemplateField);
        }
      } else {
        return null;
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private void removeDummyFieldFromTemplate(YamlField yamlField) {
    if (CVNGStepType.CVNG_VERIFY.getDisplayName().equals(yamlField.getNode().getType())) {
      ((ObjectNode) yamlField.getNode().getCurrJsonNode()).remove("dummyInputParam");
    } else {
      for (YamlField child : yamlField.getNode().fields()) {
        removeDummyFieldFromTemplate(child);
      }
      if (yamlField.getNode().isArray()) {
        for (YamlNode child : yamlField.getNode().asArray()) {
          removeDummyFieldFromTemplate(new YamlField(child));
        }
      }
    }
  }

  @NotNull
  private List<YamlNode> getStageYamlNodes(YamlField pipeline) {
    return pipeline.getNode().getField("pipeline").getNode().getField("stages").getNode().asArray();
  }

  private void updateStageYamlWithDummyInputParam(YamlNode stageYaml) {
    if (!isDeploymentStage(stageYaml)) {
      return;
    }
    addDummyInputFieldToVerifyStep(CVNGStepUtils.getExecutionNodeField(stageYaml));
  }

  private boolean isDeploymentStage(YamlNode stageYaml) {
    if (stageYaml.getField("type") == null) {
      return false;
    }
    return stageYaml.getField("type").getNode().asText().equals("Deployment");
  }

  private void addDummyInputFieldToVerifyStep(YamlField yamlField) {
    if (CVNGStepType.CVNG_VERIFY.getDisplayName().equals(yamlField.getNode().getType())) {
      if (!hasInputSetPattern(yamlField)) {
        ((ObjectNode) yamlField.getNode().getCurrJsonNode()).put("dummyInputParam", "<+input>");
      }
    } else {
      for (YamlField child : yamlField.getNode().fields()) {
        addDummyInputFieldToVerifyStep(child);
      }
      if (yamlField.getNode().isArray()) {
        for (YamlNode child : yamlField.getNode().asArray()) {
          addDummyInputFieldToVerifyStep(new YamlField(child));
        }
      }
    }
  }

  private boolean hasInputSetPattern(YamlField yamlField) {
    if (NGExpressionUtils.matchesInputSetPattern(yamlField.getNode().asText())) {
      return true;
    } else {
      boolean hasInputParams = false;
      for (YamlField child : yamlField.getNode().fields()) {
        hasInputParams |= hasInputSetPattern(child);
      }
      if (yamlField.getNode().isArray()) {
        for (YamlNode child : yamlField.getNode().asArray()) {
          hasInputParams |= hasInputSetPattern(new YamlField(child));
        }
      }
      return hasInputParams;
    }
  }

  private JsonNode getTemplateCorrectedYaml(YamlField yamlField, String templateYaml) {
    try {
      YamlField templateYamlField = YamlUtils.readTree(templateYaml);
      JsonNode templateJsonNode = templateYamlField.getNode().getCurrJsonNode();
      Set<String> templatePathsSet = new HashSet<>();
      findTemplateKeyPaths(templateJsonNode, PIPELINE_YAML_KEY, templatePathsSet);
      Map<String, JsonNode> templateCorrectedJsonNodeMap = new HashMap<>();
      templateCorrectedJsonNodeMap.put(PIPELINE_YAML_KEY,
          addTemplateYamlKeys(
              yamlField.getNode().getCurrJsonNode().get(PIPELINE_YAML_KEY), PIPELINE_YAML_KEY, templatePathsSet));
      return new ObjectNode(JsonNodeFactory.instance, templateCorrectedJsonNodeMap);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private void findTemplateKeyPaths(JsonNode rootNode, String path, Set<String> set) {
    if (Objects.isNull(rootNode)) {
      return;
    }
    if (rootNode.getNodeType() == JsonNodeType.OBJECT) {
      ObjectNode objectNode = (ObjectNode) rootNode;
      if (Objects.nonNull(objectNode.get(TEMPLATE_YAML_KEY))) {
        set.add(path);
      }
      Iterator<String> propertyIterator = objectNode.fieldNames();
      List<String> properties = IteratorUtils.toList(propertyIterator);
      for (String s : properties) {
        String currentPath;
        if (s.equals(TEMPLATE_YAML_KEY) || s.equals(TEMPLATE_INPUTS_YAML_KEY)) {
          currentPath = path;
        } else {
          currentPath = path + PATH_DELIMITER + s;
        }
        JsonNode child = objectNode.get(s);
        if (child.isArray()) {
          ArrayNode arrayNode = (ArrayNode) child;
          arrayNode.forEach(element -> {
            String arrayKey = s.substring(0, s.length() - 1);
            JsonNode singleNode = element.get(arrayKey);
            TextNode textNode = (TextNode) singleNode.get(IDENTIFIER_YAML_KEY);
            if (textNode != null) {
              String currentArrayPath = currentPath + PATH_DELIMITER + "[" + textNode.asText() + "]";
              findTemplateKeyPaths(singleNode, currentArrayPath, set);
            }
          });
        } else {
          findTemplateKeyPaths(child, currentPath, set);
        }
      }
    }
  }

  private JsonNode addTemplateYamlKeys(JsonNode rootNode, String path, Set<String> set) {
    if (Objects.isNull(rootNode)) {
      return null;
    }
    if (rootNode.getNodeType() == JsonNodeType.OBJECT) {
      ObjectNode objectNode = (ObjectNode) rootNode;
      Iterator<String> propertyIterator = objectNode.fieldNames();
      List<String> properties = IteratorUtils.toList(propertyIterator);
      Map<String, JsonNode> specMap = new HashMap<>();
      for (String s : properties) {
        JsonNode newChildNode;
        String currentPath = path + PATH_DELIMITER + s;
        JsonNode child = objectNode.get(s);
        if (child.isArray()) {
          List<JsonNode> childrenElements = new ArrayList<>();
          ArrayNode arrayNode = (ArrayNode) child;
          arrayNode.forEach(element -> {
            Map<String, JsonNode> arrSubNodeMap = new HashMap<>();
            String arrayKey = s.substring(0, s.length() - 1);
            JsonNode objectNode1 = element.get(arrayKey);
            TextNode textNode = (TextNode) objectNode1.get(IDENTIFIER_YAML_KEY);
            if (textNode != null) {
              String currentArrayPath = currentPath + PATH_DELIMITER + "[" + textNode.asText() + "]";
              arrSubNodeMap.put(arrayKey, addTemplateYamlKeys(objectNode1, currentArrayPath, set));
              childrenElements.add(new ObjectNode(JsonNodeFactory.instance, arrSubNodeMap));
            }
          });
          newChildNode = new ArrayNode(JsonNodeFactory.instance, childrenElements);
        } else {
          newChildNode = addTemplateYamlKeys(child, currentPath, set);
        }
        specMap.put(s, newChildNode);
      }
      if (set.contains(path)) {
        JsonNode identifierNode = specMap.remove(IDENTIFIER_YAML_KEY);
        Map<String, JsonNode> templateInputsMap = new HashMap<>();
        templateInputsMap.put(TEMPLATE_INPUTS_YAML_KEY, new ObjectNode(JsonNodeFactory.instance, specMap));
        Map<String, JsonNode> templateMap = new HashMap<>();
        templateMap.put(IDENTIFIER_YAML_KEY, identifierNode);
        templateMap.put(TEMPLATE_YAML_KEY, new ObjectNode(JsonNodeFactory.instance, templateInputsMap));
        return new ObjectNode(JsonNodeFactory.instance, templateMap);
      } else {
        return new ObjectNode(JsonNodeFactory.instance, specMap);
      }
    } else {
      return rootNode;
    }
  }
}
