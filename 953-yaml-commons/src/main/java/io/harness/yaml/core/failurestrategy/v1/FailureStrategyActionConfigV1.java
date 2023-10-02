/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.core.failurestrategy.v1;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXISTING_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.yaml.core.failurestrategy.abort.v1.AbortFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.ignore.v1.IgnoreFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.manualintervention.v1.ManualInterventionFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.markFailure.v1.MarkAsFailFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.marksuccess.v1.MarkAsSuccessFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.retry.v1.RetryFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.retry.v1.RetrySGFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.rollback.v1.PipelineRollbackFailureActionConfigV1;
import io.harness.yaml.core.failurestrategy.rollback.v1.StageRollbackFailureActionConfigV1;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

@Data
@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@JsonTypeInfo(use = NAME, property = "type", include = EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = AbortFailureActionConfigV1.class, name = NGFailureActionTypeConstantsV1.ABORT)
  , @JsonSubTypes.Type(value = IgnoreFailureActionConfigV1.class, name = NGFailureActionTypeConstantsV1.IGNORE),
      @JsonSubTypes.Type(value = ManualInterventionFailureActionConfigV1.class,
          name = NGFailureActionTypeConstantsV1.MANUAL_INTERVENTION),
      @JsonSubTypes.Type(
          value = MarkAsSuccessFailureActionConfigV1.class, name = NGFailureActionTypeConstantsV1.MARK_AS_SUCCESS),
      @JsonSubTypes.Type(value = RetryFailureActionConfigV1.class, name = NGFailureActionTypeConstantsV1.RETRY),
      @JsonSubTypes.Type(
          value = StageRollbackFailureActionConfigV1.class, name = NGFailureActionTypeConstantsV1.STAGE_ROLLBACK),
      @JsonSubTypes.Type(
          value = PipelineRollbackFailureActionConfigV1.class, name = NGFailureActionTypeConstantsV1.PIPELINE_ROLLBACK),
      @JsonSubTypes.Type(
          value = MarkAsFailFailureActionConfigV1.class, name = NGFailureActionTypeConstantsV1.MARK_AS_FAILURE),
      @JsonSubTypes.Type(
          value = RetrySGFailureActionConfigV1.class, name = NGFailureActionTypeConstantsV1.RETRY_STEP_GROUP)
})
public abstract class FailureStrategyActionConfigV1 {
  public abstract NGFailureActionTypeV1 getType();
}
