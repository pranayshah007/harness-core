package io.harness.cdng.ecs.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Value
@Builder
@TypeAlias("ecsBlueGreenSwapTargetGroupsOutcome")
@JsonTypeName("ecsBlueGreenSwapTargetGroupsOutcome")
@RecasterAlias("io.harness.cdng.ecs.EcsBlueGreenSwapTargetGroupsOutcome")
public class EcsBlueGreenSwapTargetGroupsOutcome implements Outcome, ExecutionSweepingOutput {
  String loadBalancer;
  String prodListenerArn;
  String prodListenerRuleArn;
  String prodTargetGroupArn;
  String stageListenerArn;
  String stageListenerRuleArn;
  String stageTargetGroupArn;
  boolean trafficShifted;
}
