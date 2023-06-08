/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.core.failurestrategy;

import static com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXISTING_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.yaml.core.failurestrategy.abort.AbortFailureActionConfig;
import io.harness.yaml.core.failurestrategy.ignore.IgnoreFailureActionConfig;
import io.harness.yaml.core.failurestrategy.manualintervention.ManualInterventionFailureActionConfig;
import io.harness.yaml.core.failurestrategy.markFailure.MarkAsFailFailureActionConfig;
import io.harness.yaml.core.failurestrategy.marksuccess.MarkAsSuccessFailureActionConfig;
import io.harness.yaml.core.failurestrategy.retry.RetryFailureActionConfig;
import io.harness.yaml.core.failurestrategy.retry.RetrySGFailureActionConfig;
import io.harness.yaml.core.failurestrategy.rollback.PipelineRollbackFailureActionConfig;
import io.harness.yaml.core.failurestrategy.rollback.StageRollbackFailureActionConfig;
import io.harness.yaml.core.failurestrategy.rollback.StepGroupFailureActionConfig;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javax.validation.constraints.NotNull;

@JsonTypeInfo(use = NAME, property = "type", include = EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
  @Type(value = AbortFailureActionConfig.class, name = NGFailureActionTypeConstants.ABORT)
  , @Type(value = IgnoreFailureActionConfig.class, name = NGFailureActionTypeConstants.IGNORE),
      @Type(
          value = ManualInterventionFailureActionConfig.class, name = NGFailureActionTypeConstants.MANUAL_INTERVENTION),
      @Type(value = MarkAsSuccessFailureActionConfig.class, name = NGFailureActionTypeConstants.MARK_AS_SUCCESS),
      @Type(value = RetryFailureActionConfig.class, name = NGFailureActionTypeConstants.RETRY),
      @Type(value = StageRollbackFailureActionConfig.class, name = NGFailureActionTypeConstants.STAGE_ROLLBACK),
      @Type(value = StepGroupFailureActionConfig.class, name = NGFailureActionTypeConstants.STEP_GROUP_ROLLBACK),
      @Type(value = PipelineRollbackFailureActionConfig.class, name = NGFailureActionTypeConstants.PIPELINE_ROLLBACK),
      @Type(value = ProceedWithDefaultValuesFailureActionConfig.class,
          name = NGFailureActionTypeConstants.PROCEED_WITH_DEFAULT_VALUES),
      @Type(value = MarkAsFailFailureActionConfig.class, name = NGFailureActionTypeConstants.MARK_AS_FAILURE),
      @Type(value = RetrySGFailureActionConfig.class, name = NGFailureActionTypeConstants.RETRY_STEP_GROUP)
})
@OwnedBy(HarnessTeam.PIPELINE)
public interface FailureStrategyActionConfig {
  @NotNull NGFailureActionType getType();
}
