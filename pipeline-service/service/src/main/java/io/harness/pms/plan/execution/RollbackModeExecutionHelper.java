/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.exception.UnexpectedException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.plan.Plan;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.helpers.PrincipalInfoHelper;
import io.harness.pms.pipeline.service.PipelineMetadataService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class RollbackModeExecutionHelper {
  NodeExecutionService nodeExecutionService;
  PipelineMetadataService pipelineMetadataService;
  PrincipalInfoHelper principalInfoHelper;

  public ExecutionMetadata transformExecutionMetadata(ExecutionMetadata executionMetadata, String planExecutionID,
      ExecutionTriggerInfo triggerInfo, String accountId, String orgIdentifier, String projectIdentifier) {
    return executionMetadata.toBuilder()
        .setExecutionUuid(planExecutionID)
        .setTriggerInfo(triggerInfo)
        .setRunSequence(pipelineMetadataService.incrementExecutionCounter(
            accountId, orgIdentifier, projectIdentifier, executionMetadata.getPipelineIdentifier()))
        .setPrincipalInfo(principalInfoHelper.getPrincipalInfoFromSecurityContext())
        .setExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK)
        .build();
  }

  public PlanExecutionMetadata transformPlanExecutionMetadata(
      PlanExecutionMetadata planExecutionMetadata, String planExecutionID) {
    return planExecutionMetadata.withPlanExecutionId(planExecutionID)
        .withProcessedYaml(transformProcessedYaml(planExecutionMetadata.getProcessedYaml()))
        .withUuid(null); // this uuid is the mongo uuid. It is being set as null so that when this Plan Execution
                         // Metadata is saved later on in the execution, a new object is stored rather than replacing
                         // the Metadata for the original execution
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
   */
  private String transformProcessedYaml(String processedYaml) {
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
      reversedStages.add(stagesList.get(i));
    }
    pipelineInnerNode.set(YAMLFieldNameConstants.STAGES, reversedStages);
    return YamlUtils.write(pipelineNode).replace("---\n", "");
  }

  public Plan transformPlanForRollbackMode(Plan plan, String previousExecutionId, List<String> nodeIDsToPreserve) {
    // todo: implement
    return plan;
  }
}