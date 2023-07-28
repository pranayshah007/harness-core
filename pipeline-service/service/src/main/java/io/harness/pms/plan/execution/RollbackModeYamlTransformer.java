/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class RollbackModeYamlTransformer {
  NodeExecutionService nodeExecutionService;

  String transformProcessedYaml(String processedYaml, ExecutionMode executionMode, String originalPlanExecutionId) {
    switch (executionMode) {
      case PIPELINE_ROLLBACK:
        return transformProcessedYamlForPipelineRollbackMode(processedYaml);
      case POST_EXECUTION_ROLLBACK:
        return transformProcessedYamlForPostExecutionRollbackMode(processedYaml);
      default:
        throw new InvalidRequestException(String.format(
            "Unsupported Execution Mode %s in RollbackModeExecutionHelper while transforming plan for execution with id %s",
            executionMode.name(), originalPlanExecutionId));
    }
  }

  /**
   * This is to reverse the stages in the processed yaml, and remove stages that were not run in the original execution
   * Original->
   * pipeline:
   *   stages:
   *   - stage:
   *       identifier: s1
   *  - stage:
   *       identifier: s2
   *  - stage:
   *       identifier: s3
   * Lets say s3 was not run.
   * Transformed->
   * pipeline:
   *   stages:
   *   - stage:
   *       identifier: s2
   *   - stage:
   *       identifier: s1
   */
  String transformProcessedYamlForPipelineRollbackMode(String processedYaml) {
    return filterProcessedYaml(processedYaml);
  }

  /**
   * This is to reverse the stages in the processed yaml
   * Original->
   * pipeline:
   *   stages:
   *   - stage:
   *       identifier: s1
   *  - stage:
   *       identifier: s2
   * Transformed->
   * pipeline:
   *   stages:
   *   - stage:
   *       identifier: s2
   *   - stage:
   *       identifier: s1
   *
   * If stageNodeExecutionIds contains one element, and it corresponds to the stage s1, then we will get->
   * pipeline:
   *   stages:
   *   - stage:
   *       identifier: s1
   */
  String transformProcessedYamlForPostExecutionRollbackMode(String processedYaml) {
    return filterProcessedYaml(processedYaml);
  }

  String filterProcessedYaml(String processedYaml) {
    JsonNode pipelineNode;
    try {
      pipelineNode = YamlUtils.readTree(processedYaml).getNode().getCurrJsonNode();
    } catch (IOException e) {
      throw new UnexpectedException("Unable to transform processed YAML while executing in Rollback Mode");
    }
    ObjectNode pipelineInnerNode = (ObjectNode) pipelineNode.get(YAMLFieldNameConstants.PIPELINE);
    ArrayNode stagesList = (ArrayNode) pipelineInnerNode.get(YAMLFieldNameConstants.STAGES);
    ArrayNode reversedStages = stagesList.deepCopy().removeAll();
    int numStages = stagesList.size();
    for (int i = numStages - 1; i >= 0; i--) {
      JsonNode currentNode = stagesList.get(i);
      reversedStages.add(currentNode);
    }
    pipelineInnerNode.set(YAMLFieldNameConstants.STAGES, reversedStages);
    return YamlUtils.writeYamlString(pipelineNode);
  }
}
