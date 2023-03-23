/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.when.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.when.beans.StageWhenCondition;
import io.harness.when.beans.StepWhenCondition;
import io.harness.when.beans.WhenConditionStatus;

import java.io.IOException;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PIPELINE)
public class RunInfoUtils {
  String STAGE_SUCCESS = "OnStageSuccess";
  String STAGE_FAILURE = "OnStageFailure";
  String PIPELINE_SUCCESS = "OnPipelineSuccess";
  String PIPELINE_FAILURE = "OnPipelineFailure";
  String ALWAYS = "Always";

  // adding == true here because if <+pipeline.rollback.isPipelineRollback> is null, then
  // (<+pipeline.rollback.isPipelineRollback> || <+OnStageFailure>) will equate to (null || true) and this leads to a
  // jexl error
  String IS_PIPELINE_ROLLBACK = "(<+pipeline.rollback.isPipelineRollback> == true)";

  // If when conditions configured as <+input> and no value is given, when.getValue() will still
  // be null and handled accordingly
  public String getRunConditionForStage(ParameterField<StageWhenCondition> stageWhenCondition) {
    if (ParameterField.isNull(stageWhenCondition) || stageWhenCondition.getValue() == null) {
      return getDefaultWhenCondition(true);
    }

    if (stageWhenCondition.getValue().getPipelineStatus() == null) {
      throw new InvalidRequestException("Pipeline Status in stage when condition cannot be empty.");
    }

    return combineExpressions(getStatusExpression(stageWhenCondition.getValue().getPipelineStatus(), true),
        getGivenRunCondition(stageWhenCondition.getValue().getCondition()));
  }

  public String getRunConditionForStep(ParameterField<StepWhenCondition> stepWhenCondition) {
    if (ParameterField.isNull(stepWhenCondition) || stepWhenCondition.getValue() == null) {
      return getDefaultWhenCondition(false);
    }

    if (stepWhenCondition.getValue().getStageStatus() == null) {
      throw new InvalidRequestException("Stage Status in step when condition cannot be empty.");
    }

    return combineExpressions(getStatusExpression(stepWhenCondition.getValue().getStageStatus(), false),
        getGivenRunCondition(stepWhenCondition.getValue().getCondition()));
  }

  public String getRunConditionForRollback(
      ParameterField<StepWhenCondition> stepWhenCondition, ExecutionMode executionMode) {
    if (ParameterField.isNull(stepWhenCondition) || stepWhenCondition.getValue() == null) {
      return getStatusExpression(isRollbackMode(executionMode) ? ALWAYS : STAGE_FAILURE);
    }
    if (stepWhenCondition.getValue().getStageStatus() == null) {
      throw new InvalidRequestException("Stage Status in step when condition cannot be empty.");
    }

    return combineExpressions(getStatusExpression(stepWhenCondition.getValue().getStageStatus(), false),
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
    if (!isStage) {
      return getStatusExpression(STAGE_SUCCESS);
    }
    return getStatusExpression(PIPELINE_SUCCESS);
  }

  private String combineExpressions(String statusExpression, String conditionExpression) {
    if (EmptyPredicate.isNotEmpty(conditionExpression)) {
      return statusExpression + " && (" + conditionExpression + ")";
    }
    return statusExpression;
  }

  private String getStatusExpression(WhenConditionStatus whenConditionStatus, boolean isStage) {
    switch (whenConditionStatus) {
      case SUCCESS:
        return isStage ? getStatusExpression(PIPELINE_SUCCESS) : getStatusExpression(STAGE_SUCCESS);
      case FAILURE:
        return isStage ? getStatusExpression(PIPELINE_FAILURE) : getStatusExpression(STAGE_FAILURE);
      default:
        return getStatusExpression(ALWAYS);
    }
  }

  private String getStatusExpression(String status) {
    return "<+" + status + ">";
  }

  public String getStageWhenCondition(YamlField field) {
    StageWhenCondition stageWhenCondition = null;
    if (field.getNode().getField(YAMLFieldNameConstants.WHEN) != null) {
      try {
        stageWhenCondition = YamlUtils.read(
            field.getNode().getField(YAMLFieldNameConstants.WHEN).getNode().toString(), StageWhenCondition.class);
      } catch (IOException e) {
        // Empty whenCondition. Default will be used.
      }
    }
    return getRunConditionForStage(ParameterField.createValueField(stageWhenCondition));
  }
}
