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
