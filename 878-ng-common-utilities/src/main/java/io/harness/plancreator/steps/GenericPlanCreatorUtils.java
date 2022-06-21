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

import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.commons.RepairActionCode;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.core.failurestrategy.FailureStrategyActionConfig;

import java.util.Arrays;
import lombok.experimental.UtilityClass;

@UtilityClass
public class GenericPlanCreatorUtils {
  public String getStageNodeId(YamlField currentField) {
    YamlNode stageNode = YamlUtils.findParentNode(currentField.getNode(), STAGE);
    if (stageNode == null) {
      return null;
    }
    return stageNode.getUuid();
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
        return YamlUtils.findParentNode(currentField.getNode(), STEP_GROUP) == null
            || currentField.checkIfParentIsParallel(STEP_GROUP);
      }
    }
    return false;
  }

  public YamlField obtainNextSiblingField(YamlField currentField) {
    return currentField.getNode().nextSiblingFromParentArray(
        currentField.getName(), Arrays.asList(STEP, PARALLEL, STEP_GROUP));
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
      default:
        throw new InvalidRequestException(

            action.toString() + " Failure action doesn't have corresponding RepairAction Code.");
    }
  }
}
