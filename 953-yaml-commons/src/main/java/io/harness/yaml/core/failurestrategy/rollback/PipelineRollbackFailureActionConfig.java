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
