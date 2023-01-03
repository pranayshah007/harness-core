/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipelinestage.v1.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.GROUP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.PARALLEL;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STAGES;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.JsonUtils;
import io.harness.steps.StepSpecTypeConstants;

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

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PipelineStageHelperV1 {
  @Inject private PMSPipelineTemplateHelper pmsPipelineTemplateHelper;

  public void validateNestedChainedPipeline(PipelineEntity entity) {
    TemplateMergeResponseDTO templateMergeResponseDTO =
        pmsPipelineTemplateHelper.resolveTemplateRefsInPipeline(entity, BOOLEAN_FALSE_VALUE);

    containsPipelineStage(templateMergeResponseDTO.getMergedPipelineYaml());
  }

  private void containsPipelineStage(String yaml) {
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

  public String getInputSet(YamlField pipelineInputs) {
    String inputSetAsJson = "{}";
    if (pipelineInputs != null) {
      JsonNode inputJsonNode = pipelineInputs.getNode().getCurrJsonNode();
      YamlUtils.removeUuid(inputJsonNode);
      Map<String, JsonNode> map = new HashMap<>();
      map.put("inputs", inputJsonNode);
      inputSetAsJson = JsonUtils.asJson(map);
    }
    return inputSetAsJson;
  }
}
