/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.when.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.plan.ExecutionMode.NORMAL;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.yaml.ParameterField;
import io.harness.when.beans.StageWhenCondition;
import io.harness.when.beans.StepWhenCondition;
import io.harness.when.beans.WhenConditionStatus;

import lombok.experimental.UtilityClass;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@UtilityClass
@OwnedBy(PIPELINE)
public class RunInfoUtils {
  String STAGE_SUCCESS = "OnStageSuccess";
  String STAGE_FAILURE = "OnStageFailure";
  String PIPELINE_SUCCESS = "OnPipelineSuccess";
  String PIPELINE_FAILURE = "OnPipelineFailure";
  String ALWAYS = "Always";
  String ROLLBACK_MODE_EXECUTION = "OnRollbackModeExecution";

  public String getRunConditionForStage(ParameterField<StageWhenCondition> stageWhenCondition) {
    return getRunConditionForStage(stageWhenCondition, NORMAL);
  }
  public String getRunConditionForStage(
      ParameterField<StageWhenCondition> stageWhenCondition, ExecutionMode executionMode) {
    if (ParameterField.isNull(stageWhenCondition) || stageWhenCondition.getValue() == null) {
      return getDefaultWhenCondition(true, executionMode);
    }

    if (stageWhenCondition.getValue().getPipelineStatus() == null) {
      throw new InvalidRequestException("Pipeline Status in stage when condition cannot be empty.");
    }

    return combineExpressions(
        getStatusExpressionForStage(stageWhenCondition.getValue().getPipelineStatus(), executionMode),
        getGivenRunCondition(stageWhenCondition.getValue().getCondition()));
  }

  public String getRunConditionForStep(ParameterField<StepWhenCondition> stepWhenCondition) {
    if (ParameterField.isNull(stepWhenCondition) || stepWhenCondition.getValue() == null) {
      return getDefaultWhenCondition(false);
    }

    if (stepWhenCondition.getValue().getStageStatus() == null) {
      throw new InvalidRequestException("Stage Status in step when condition cannot be empty.");
    }

    return combineExpressions(getStatusExpression(stepWhenCondition.getValue().getStageStatus()),
        getGivenRunCondition(stepWhenCondition.getValue().getCondition()));
  }

  public String getRunConditionForRollback(ParameterField<StepWhenCondition> stepWhenCondition) {
    if (ParameterField.isNull(stepWhenCondition) || stepWhenCondition.getValue() == null) {
      return getStatusExpression(ROLLBACK_MODE_EXECUTION) + " || " + getStatusExpression(STAGE_FAILURE);
    }
    if (stepWhenCondition.getValue().getStageStatus() == null) {
      throw new InvalidRequestException("Stage Status in step when condition cannot be empty.");
    }

    return combineExpressions(getStatusExpression(stepWhenCondition.getValue().getStageStatus()),
        getGivenRunCondition(stepWhenCondition.getValue().getCondition()));
  }

  boolean isRollbackMode(ExecutionMode executionMode) {
    return executionMode == ExecutionMode.POST_EXECUTION_ROLLBACK || executionMode == ExecutionMode.PIPELINE_ROLLBACK;
  }

  private String getGivenRunCondition(ParameterField<String> condition) {
    if (EmptyPredicate.isNotEmpty(condition.getValue())) {
      return condition.getValue();
    }
    return condition.getExpressionValue();
  }

  private String getDefaultWhenCondition(boolean isStage) {
    return getDefaultWhenCondition(isStage, NORMAL);
  }

  private String getDefaultWhenCondition(boolean isStage, ExecutionMode executionMode) {
    if (!isStage) {
      return getStatusExpression(STAGE_SUCCESS);
    }
    return isRollbackMode(executionMode) ? getStatusExpression(ALWAYS) : getStatusExpression(PIPELINE_SUCCESS);
  }

  private String combineExpressions(String statusExpression, String conditionExpression) {
    if (EmptyPredicate.isNotEmpty(conditionExpression)) {
      return statusExpression + " && (" + conditionExpression + ")";
    }
    return statusExpression;
  }

  private String getStatusExpression(WhenConditionStatus whenConditionStatus) {
    switch (whenConditionStatus) {
      case SUCCESS:
        return getStatusExpression(STAGE_SUCCESS);
      case FAILURE:
        return getStatusExpression(STAGE_FAILURE);
      default:
        return getStatusExpression(ALWAYS);
    }
  }

  private String getStatusExpressionForStage(WhenConditionStatus whenConditionStatus, ExecutionMode executionMode) {
    switch (whenConditionStatus) {
      case SUCCESS:
        return isRollbackMode(executionMode) ? getStatusExpression(ALWAYS) : getStatusExpression(PIPELINE_SUCCESS);
      case FAILURE:
        return isRollbackMode(executionMode) ? getStatusExpression(ALWAYS) : getStatusExpression(PIPELINE_FAILURE);
      default:
        return getStatusExpression(ALWAYS);
    }
  }

  private String getStatusExpression(String status) {
    return "<+" + status + ">";
  }
}
