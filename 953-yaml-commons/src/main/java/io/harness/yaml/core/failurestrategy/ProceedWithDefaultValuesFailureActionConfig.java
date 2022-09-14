/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.core.failurestrategy;

import static io.harness.yaml.core.failurestrategy.NGFailureActionTypeConstants.PROCEED_WITH_DEFAULT_VALUE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
@RecasterAlias("io.harness.yaml.core.failurestrategy.ProceedWithDefaultValuesFailureActionConfig")
public class ProceedWithDefaultValuesFailureActionConfig implements FailureStrategyActionConfig {
  @ApiModelProperty(allowableValues = PROCEED_WITH_DEFAULT_VALUE)
  NGFailureActionType type = NGFailureActionType.PROCEED_WITH_DEFAULT_VALUE;
}
