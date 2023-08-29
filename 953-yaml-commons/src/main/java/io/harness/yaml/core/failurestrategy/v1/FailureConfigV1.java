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
import io.harness.yaml.core.failurestrategy.abort.v1.AbortFailureConfigV1;
import io.harness.yaml.core.failurestrategy.ignore.v1.IgnoreFailureConfigV1;
import io.harness.yaml.core.failurestrategy.manualintervention.v1.ManualInterventionFailureConfigV1;
import io.harness.yaml.core.failurestrategy.markFailure.v1.MarkAsFailFailureConfigV1;
import io.harness.yaml.core.failurestrategy.marksuccess.v1.MarkAsSuccessFailureConfigV1;
import io.harness.yaml.core.failurestrategy.retry.v1.RetryFailureConfigV1;
import io.harness.yaml.core.failurestrategy.retry.v1.RetrySGFailureConfigV1;
import io.harness.yaml.core.failurestrategy.rollback.v1.PipelineRollbackFailureConfigV1;
import io.harness.yaml.core.failurestrategy.rollback.v1.StageRollbackFailureConfigV1;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import lombok.Data;

@Data
@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@JsonTypeInfo(use = NAME, property = "type", include = EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = AbortFailureConfigV1.class, name = NGFailureActionTypeConstantsV1.ABORT)
  , @JsonSubTypes.Type(value = IgnoreFailureConfigV1.class, name = NGFailureActionTypeConstantsV1.IGNORE),
      @JsonSubTypes.Type(
          value = ManualInterventionFailureConfigV1.class, name = NGFailureActionTypeConstantsV1.MANUAL_INTERVENTION),
      @JsonSubTypes.Type(
          value = MarkAsSuccessFailureConfigV1.class, name = NGFailureActionTypeConstantsV1.MARK_AS_SUCCESS),
      @JsonSubTypes.Type(value = RetryFailureConfigV1.class, name = NGFailureActionTypeConstantsV1.RETRY),
      @JsonSubTypes.Type(
          value = StageRollbackFailureConfigV1.class, name = NGFailureActionTypeConstantsV1.STAGE_ROLLBACK),
      @JsonSubTypes.Type(
          value = PipelineRollbackFailureConfigV1.class, name = NGFailureActionTypeConstantsV1.PIPELINE_ROLLBACK),
      @JsonSubTypes.Type(value = MarkAsFailFailureConfigV1.class, name = NGFailureActionTypeConstantsV1.MARK_AS_FAILURE)
      , @JsonSubTypes.Type(value = RetrySGFailureConfigV1.class, name = NGFailureActionTypeConstantsV1.RETRY_STEP_GROUP)
})
public abstract class FailureConfigV1 {
  List<NGFailureTypeV1> errors;
  public abstract NGFailureActionTypeV1 getType();
}
