/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps;
import static io.harness.pms.yaml.YAMLFieldNameConstants.PARALLEL;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ROLLBACK_STEPS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STAGE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEPS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP_GROUP;

import io.harness.advisers.pipelinerollback.OnFailPipelineRollbackParameters;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.commons.RepairActionCode;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.core.failurestrategy.FailureStrategyActionConfig;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@UtilityClass
public class GenericPlanCreatorUtils {
  public String getStageNodeId(YamlField currentField) {
    YamlNode stageNode = YamlUtils.findParentNode(currentField.getNode(), STAGE);
    if (stageNode == null) {
      return null;
    }
    return stageNode.getUuid();
  }

  public String getStageOrParallelNodeId(YamlField currentField) {
    YamlNode stageNode = YamlUtils.findParentNode(currentField.getNode(), STAGE);
    if (stageNode == null) {
      return null;
    }
    YamlNode parallelNode = YamlUtils.findParentNode(stageNode, PARALLEL);
    return parallelNode == null ? stageNode.getUuid() : parallelNode.getUuid();
  }

  public String getStepGroupRollbackStepsNodeId(YamlField currentField) {
    YamlNode stepGroup = YamlUtils.findParentNode(currentField.getNode(), STEP_GROUP);
    return getRollbackStepsNodeId(stepGroup);
  }

  public String getRollbackStepsNodeId(YamlNode currentNode) {
    YamlField rollbackSteps = null;
    if (currentNode != null) {
      rollbackSteps = currentNode.getField(ROLLBACK_STEPS);
    }
    String uuid = null;
    if (rollbackSteps != null) {
      uuid = rollbackSteps.getNode().getUuid();
    }
    return uuid;
  }

  // This is required as step can be inside stepGroup which can have Parallel and stepGroup itself can
  // be inside Parallel section.
  public boolean checkIfStepIsInParallelSection(YamlField currentField) {
    if (currentField != null && currentField.getNode() != null) {
      if (currentField.checkIfParentIsParallel(STEPS) || currentField.checkIfParentIsParallel(ROLLBACK_STEPS)) {
        // Check if step is inside StepGroup and StepGroup is inside Parallel but not the step.
        YamlNode stepGroupNode = YamlUtils.findParentNode(currentField.getNode().getParentNode(), STEP_GROUP);
        if (stepGroupNode == null) {
          return true;
        }
        /**
         * We need to check if parallel is in between stepGroup and step then it means that step is in parallel section
         * otherwise parallel section might be above step group.
         */
        YamlNode parallelNodeParentOfStepGroupNode = YamlUtils.findParentNode(stepGroupNode, PARALLEL);
        YamlNode parallelNodeParentOfCurrentField = YamlUtils.findParentNode(currentField.getNode(), PARALLEL);
        if (parallelNodeParentOfCurrentField == null) {
          return false;
        }
        if (parallelNodeParentOfStepGroupNode == null) {
          return true;
        }
        return !Objects.equals(parallelNodeParentOfCurrentField.getUuid(), parallelNodeParentOfStepGroupNode.getUuid());
      }
    }
    return false;
  }

  public YamlField obtainNextSiblingField(YamlField currentField) {
    return currentField.getNode().nextSiblingFromParentArray(
        currentField.getName(), Arrays.asList(STEP, PARALLEL, STEP_GROUP));
  }

  public YamlField obtainNextSiblingFieldAtStageLevel(YamlField currentField) {
    return currentField.getNode().nextSiblingFromParentArray(currentField.getName(), Arrays.asList(STAGE, PARALLEL));
  }

  public RepairActionCode toRepairAction(FailureStrategyActionConfig action) {
    switch (action.getType()) {
      case IGNORE:
        return RepairActionCode.IGNORE;
      case MARK_AS_SUCCESS:
        return RepairActionCode.MARK_AS_SUCCESS;
      case ABORT:
        return RepairActionCode.END_EXECUTION;
      case STAGE_ROLLBACK:
        return RepairActionCode.STAGE_ROLLBACK;
      case MANUAL_INTERVENTION:
        return RepairActionCode.MANUAL_INTERVENTION;
      case RETRY:
        return RepairActionCode.RETRY;
      case MARK_AS_FAILURE:
        return RepairActionCode.MARK_AS_FAILURE;
      case PIPELINE_ROLLBACK:
        return RepairActionCode.PIPELINE_ROLLBACK;
      default:
        throw new InvalidRequestException(

            action.toString() + " Failure action doesn't have corresponding RepairAction Code.");
    }
  }

  public static String getRollbackStageNodeId(YamlField currentField) {
    String stageNodeId = getStageOrParallelNodeId(currentField);
    return stageNodeId + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX;
  }

  public OnFailPipelineRollbackParameters buildOnFailPipelineRollbackParameters(Set<FailureType> failureTypes) {
    return OnFailPipelineRollbackParameters.builder().applicableFailureTypes(failureTypes).build();
  }
}
