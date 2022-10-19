/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.core.failurestrategy.rollback;

import static io.harness.yaml.core.failurestrategy.NGFailureActionTypeConstants.PIPELINE_ROLLBACK;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.yaml.core.failurestrategy.FailureStrategyActionConfig;
import io.harness.yaml.core.failurestrategy.NGFailureActionType;

import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
@RecasterAlias("io.harness.yaml.core.failurestrategy.rollback.PipelineRollbackFailureActionConfig")
public class PipelineRollbackFailureActionConfig implements FailureStrategyActionConfig {
  @ApiModelProperty(allowableValues = PIPELINE_ROLLBACK)
  NGFailureActionType type = NGFailureActionType.PIPELINE_ROLLBACK;
}
