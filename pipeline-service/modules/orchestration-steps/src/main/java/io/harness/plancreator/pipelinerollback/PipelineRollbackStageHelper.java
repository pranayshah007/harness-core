/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.pipelinerollback;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.LinkedHashMap;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(PIPELINE)
public class PipelineRollbackStageHelper {
  public final String PIPELINE_ROLLBACK_STAGE_NAME = "Pipeline Rollback Stage";

  public void addPipelineRollbackStageDependency(
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, YamlField stagesField) {
    JsonNode prbStageJsonNode = buildPipelineRollbackStageJsonNode();
    int numElementsInStages = stagesField.getNode().asArray().size();

    String prbStageYamlPath = "pipeline/stages/[" + numElementsInStages + "]";
    YamlUpdates yamlUpdates =
        YamlUpdates.newBuilder().putFqnToYaml(prbStageYamlPath, prbStageJsonNode.toString()).build();

    String prbStageUuid = prbStageJsonNode.get(YAMLFieldNameConstants.STAGE).get(YamlNode.UUID_FIELD_NAME).asText();
    planCreationResponseMap.put(prbStageUuid,
        PlanCreationResponse.builder()
            .dependencies(Dependencies.newBuilder().putDependencies(prbStageUuid, prbStageYamlPath + "/stage").build())
            .yamlUpdates(yamlUpdates)
            .build());
  }

  JsonNode buildPipelineRollbackStageJsonNode() {
    String stageInnerYAML = "stage:\n"
        + "  name: " + PIPELINE_ROLLBACK_STAGE_NAME + "\n"
        + "  identifier: prb-" + generateUuid() + "\n"
        + "  type: PipelineRollback\n"
        + "  spec: {}\n";
    YamlField prbStageYamlField;
    try {
      prbStageYamlField = YamlUtils.injectUuidInYamlField(stageInnerYAML);
    } catch (IOException e) {
      log.error("Exception occurred while creating Pipeline Rollback Stage", e);
      throw new InvalidRequestException("Exception while creating Pipeline Rollback Stage");
    }
    return prbStageYamlField.getNode().getCurrJsonNode();
  }
}
