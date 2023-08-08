/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.v1.helper;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.GROUP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.PARALLEL;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STAGES;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PipelineStageHelperV1 {
  public void containsPipelineStage(String yaml) {
    try {
      YamlField pipelineYamlField = YamlUtils.readTreeWithDefaultObjectMapper(yaml);
      List<YamlNode> stages = pipelineYamlField.getNode().getField(STAGES).getNode().asArray();
      for (YamlNode yamlNode : stages) {
        if (yamlNode == null || yamlNode.getType() == null) {
          continue;
        }
        if (yamlNode.getType().equals(StepSpecTypeConstants.PIPELINE_STAGE)) {
          throw new InvalidRequestException("Nested pipeline is not supported");
        } else if (yamlNode.getType().equals(PARALLEL) || yamlNode.getType().equals(GROUP)) {
          containsPipelineStageInParallelNode(yamlNode);
        }
      }
    } catch (IOException e) {
      throw new InvalidRequestException("Invalid YAML");
    }
  }

  private void containsPipelineStageInParallelNode(YamlNode yamlNode) {
    List<YamlNode> stageInParallel =
        yamlNode.getField(YAMLFieldNameConstants.SPEC).getNode().getField(STAGES).getNode().asArray();
    for (YamlNode stage : stageInParallel) {
      if (stage.getType().equals(StepSpecTypeConstants.PIPELINE_STAGE)) {
        throw new InvalidRequestException("Nested pipeline is not supported");
      }
    }
  }

  public JsonNode getInputSetJsonNode(YamlField pipelineInputs) {
    JsonNode inputJsonNode = null;
    if (pipelineInputs != null) {
      Map<String, JsonNode> map = getInputSetMapInternal(pipelineInputs);
      inputJsonNode = JsonPipelineUtils.asTree(map);
    }
    return inputJsonNode;
  }

  private Map<String, JsonNode> getInputSetMapInternal(YamlField pipelineInputs) {
    JsonNode inputJsonNode = pipelineInputs.getNode().getCurrJsonNode();
    YamlUtils.removeUuid(inputJsonNode);
    Map<String, JsonNode> map = new HashMap<>();
    map.put("inputs", inputJsonNode);
    return map;
  }
}
