/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.plancreator.pipelinerollback.PipelineRollbackStageHelper.PIPELINE_ROLLBACK_STAGE_NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)

@Singleton
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class RollbackModeYamlTransformer {
  NodeExecutionService nodeExecutionService;

  String transformProcessedYaml(String processedYaml, ExecutionMode executionMode, String originalPlanExecutionId,
      List<String> stageNodeExecutionIds) {
    switch (executionMode) {
      case PIPELINE_ROLLBACK:
        return transformProcessedYamlForPipelineRollbackMode(processedYaml, originalPlanExecutionId);
      case POST_EXECUTION_ROLLBACK:
        return transformProcessedYamlForPostExecutionRollbackMode(
            processedYaml, originalPlanExecutionId, stageNodeExecutionIds);
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
  String transformProcessedYamlForPipelineRollbackMode(String processedYaml, String originalPlanExecutionId) {
    List<String> executedStages = nodeExecutionService.getStageDetailFromPlanExecutionId(originalPlanExecutionId)
                                      .stream()
                                      .filter(info -> !info.getName().equals(PIPELINE_ROLLBACK_STAGE_NAME))
                                      .map(info -> info.getIdentifier())
                                      .collect(Collectors.toList());
    return filterProcessedYamlWithRequiredStageIdentifiers(processedYaml, executedStages);
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
  String transformProcessedYamlForPostExecutionRollbackMode(
      String processedYaml, String originalPlanExecutionId, List<String> stageNodeExecutionIds) {
    List<String> executedStages = new ArrayList<>();
    List<NodeExecution> nodeExecutions =
        nodeExecutionService.fetchStageExecutionsWithProjection(originalPlanExecutionId,
            Sets.newHashSet(NodeExecutionKeys.identifier, NodeExecutionKeys.status, NodeExecutionKeys.stepType));
    nodeExecutions.forEach(nodeExecution -> {
      if (null != nodeExecution.getUuid() && stageNodeExecutionIds.contains(nodeExecution.getUuid())
          && !StatusUtils.isFinalStatus(nodeExecution.getStatus())) {
        throw new InvalidRequestException(
            String.format("Stage plan execution [%s] is still in Progress. Wait for Node Execution [%s] to complete.",
                originalPlanExecutionId, nodeExecution.getIdentifier()));
      }
      if (StatusUtils.isFinalStatus(nodeExecution.getStatus())) {
        executedStages.add(nodeExecution.getIdentifier());
      } else if (nodeExecution.getStepType().getStepCategory() == StepCategory.STRATEGY
          && nodeExecution.getStatus() == Status.RUNNING) {
        executedStages.add(nodeExecution.getIdentifier());
      }
    });
    return filterProcessedYaml(processedYaml, executedStages);
  }

  String filterProcessedYaml(String processedYaml, List<String> executedStageIds) {
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
      if (currentNode.get(YAMLFieldNameConstants.PARALLEL) == null) {
        handleSerialStage(currentNode, executedStageIds, reversedStages);
      } else {
        handleParallelStagesForPostExecutionRollback(currentNode, executedStageIds, reversedStages);
      }
    }
    pipelineInnerNode.set(YAMLFieldNameConstants.STAGES, reversedStages);
    return YamlUtils.writeYamlString(pipelineNode);
  }

  String filterProcessedYamlWithRequiredStageIdentifiers(String processedYaml, List<String> requiredStageIds) {
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
      if (currentNode.get(YAMLFieldNameConstants.PARALLEL) == null) {
        handleSerialStage(currentNode, requiredStageIds, reversedStages);
      } else {
        handleParallelStages(currentNode, requiredStageIds, reversedStages);
      }
    }
    pipelineInnerNode.set(YAMLFieldNameConstants.STAGES, reversedStages);
    return YamlUtils.writeYamlString(pipelineNode);
  }

  void handleSerialStage(JsonNode currentNode, List<String> executedStages, ArrayNode reversedStages) {
    String stageId = currentNode.get(YAMLFieldNameConstants.STAGE).get(YAMLFieldNameConstants.IDENTIFIER).asText();
    if (executedStages.contains(stageId)) {
      reversedStages.add(currentNode);
    }
  }

  void handleParallelStages(JsonNode currentNode, List<String> executedStages, ArrayNode reversedStages) {
    ArrayNode parallelStages = (ArrayNode) currentNode.get(YAMLFieldNameConstants.PARALLEL);
    int numParallelStages = parallelStages.size();
    for (int i = 0; i < numParallelStages; i++) {
      JsonNode currParallelStage = parallelStages.get(i);
      String stageId =
          currParallelStage.get(YAMLFieldNameConstants.STAGE).get(YAMLFieldNameConstants.IDENTIFIER).asText();
      if (executedStages.contains(stageId)) {
        // adding currentNode because we need to add the parallel block fully
        reversedStages.add(currentNode);
        break;
      }
    }
  }

  void handleParallelStagesForPostExecutionRollback(
      JsonNode currentNode, List<String> executedStages, ArrayNode reversedStages) {
    ArrayNode parallelStages = (ArrayNode) currentNode.get(YAMLFieldNameConstants.PARALLEL);
    ArrayNode parallelExecutedStages = parallelStages.deepCopy().removeAll();
    int numParallelStages = parallelStages.size();
    for (int i = 0; i < numParallelStages; i++) {
      JsonNode currParallelStage = parallelStages.get(i);
      String stageId =
          currParallelStage.get(YAMLFieldNameConstants.STAGE).get(YAMLFieldNameConstants.IDENTIFIER).asText();
      if (executedStages.contains(stageId)) {
        parallelExecutedStages.add(currParallelStage);
      }
    }
    if (!parallelExecutedStages.isEmpty()) {
      ObjectNode newParallelNodeNode = (ObjectNode) currentNode;
      newParallelNodeNode.set(YAMLFieldNameConstants.PARALLEL, parallelExecutedStages);
      reversedStages.add(newParallelNodeNode);
    }
  }
}
