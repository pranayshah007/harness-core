/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.core.failurestrategy.retry;
import static io.harness.yaml.core.failurestrategy.NGFailureActionTypeConstants.RETRY_STEP_GROUP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.yaml.core.failurestrategy.FailureStrategyActionConfig;
import io.harness.yaml.core.failurestrategy.NGFailureActionType;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@Value
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
@RecasterAlias("io.harness.yaml.core.failurestrategy.retry.RetrySGFailureActionConfig")
public class RetrySGFailureActionConfig implements FailureStrategyActionConfig {
  @ApiModelProperty(allowableValues = RETRY_STEP_GROUP) NGFailureActionType type = NGFailureActionType.RETRY_STEP_GROUP;

  @NotNull @JsonProperty("spec") RetryStepGroupFailureSpecConfig specConfig;
}