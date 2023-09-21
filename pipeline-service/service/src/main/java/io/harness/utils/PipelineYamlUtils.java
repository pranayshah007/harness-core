/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PipelineYamlUtils {
  public JsonNode getStagesNodeFromRootNode(JsonNode rootNode, String pipelineVersion) {
    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      return rootNode.get(YAMLFieldNameConstants.STAGES);
    }
    return rootNode.get(YAMLFieldNameConstants.PIPELINE).get(YAMLFieldNameConstants.STAGES);
  }

  public JsonNode getStagesNodeFromParallelNode(JsonNode parallelNode, String pipelineVersion) {
    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      return parallelNode.get(YAMLFieldNameConstants.SPEC).get(YAMLFieldNameConstants.STAGES);
    }
    return parallelNode.get(YAMLFieldNameConstants.PARALLEL);
  }

  public JsonNode getStageNodeFromStagesNode(ArrayNode stagesNode, int stageIndex, String pipelineVersion) {
    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      return stagesNode.get(stageIndex);
    }
    return stagesNode.get(stageIndex).get(YAMLFieldNameConstants.STAGE);
  }

  public boolean isParallelNode(JsonNode jsonNode, String pipelineVersion) {
    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      return YAMLFieldNameConstants.PARALLEL.equals(jsonNode.get(YAMLFieldNameConstants.TYPE).asText());
    }
    return jsonNode.get(YAMLFieldNameConstants.PARALLEL) != null;
  }

  public JsonNode getStageNodeFromStagesElement(JsonNode currentJsonNode, String pipelineVersion) {
    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      return currentJsonNode;
    }
    return currentJsonNode.get(YAMLFieldNameConstants.STAGE);
  }

  public String getIdentifierFromStageNode(JsonNode stage, String pipelineVersion) {
    if (HarnessYamlVersion.isV1(pipelineVersion)) {
      return stage.get(YAMLFieldNameConstants.ID).textValue();
    }
    return stage.get(YAMLFieldNameConstants.STAGE).get(YAMLFieldNameConstants.IDENTIFIER).textValue();
  }
}
