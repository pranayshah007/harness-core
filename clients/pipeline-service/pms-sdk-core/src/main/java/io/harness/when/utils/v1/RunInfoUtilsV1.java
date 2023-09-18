/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.when.utils.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.plan.ExecutionMode.NORMAL;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import java.io.IOException;
import lombok.experimental.UtilityClass;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@UtilityClass
@OwnedBy(PIPELINE)
public class RunInfoUtilsV1 {
  String STAGE_SUCCESS = "OnStageSuccess";
  String STAGE_FAILURE = "OnStageFailure";
  String PIPELINE_SUCCESS = "OnPipelineSuccess";
  String ALWAYS = "Always";
  String ROLLBACK_MODE_EXECUTION = "OnRollbackModeExecution";

  public boolean isRollbackMode(ExecutionMode executionMode) {
    return executionMode == ExecutionMode.POST_EXECUTION_ROLLBACK || executionMode == ExecutionMode.PIPELINE_ROLLBACK;
  }

  private String getDefaultWhenConditionForRollback() {
    return getStatusExpression(ROLLBACK_MODE_EXECUTION) + " || " + getStatusExpression(STAGE_FAILURE);
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

  private String getStatusExpression(String status) {
    return "<+" + status + ">";
  }

  public String getStageWhenCondition(YamlField field) {
    ParameterField<String> stageWhenConditionParameterField = null;
    if (field.getNode().getField(YAMLFieldNameConstants.WHEN) != null) {
      try {
        stageWhenConditionParameterField = YamlUtils.read(
            field.getNode().getField(YAMLFieldNameConstants.WHEN).getNode().toString(), ParameterField.class);
      } catch (IOException e) {
        // Empty whenCondition. Default will be used.
      }
    }
    return getStageWhenCondition(stageWhenConditionParameterField);
  }

  private String getStageWhenCondition(ParameterField<String> stageWhenCondition) {
    return getStageWhenCondition(stageWhenCondition, NORMAL);
  }

  private String getStageWhenCondition(ParameterField<String> stageWhenCondition, ExecutionMode executionMode) {
    return ParameterField.isNull(stageWhenCondition) ? RunInfoUtilsV1.getDefaultWhenCondition(true, executionMode)
                                                     : (String) stageWhenCondition.fetchFinalValue();
  }

  public String getStepWhenCondition(ParameterField<String> stepWhenCondition, boolean isStepInsideRollback) {
    return ParameterField.isNull(stepWhenCondition) ? isStepInsideRollback
            ? RunInfoUtilsV1.getDefaultWhenConditionForRollback()
            : RunInfoUtilsV1.getDefaultWhenCondition(false)
                                                    : (String) stepWhenCondition.fetchFinalValue();
  }
}
