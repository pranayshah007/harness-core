package io.harness.cdng.ecs.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("ecsBlueGreenCreateServiceDataOutcome")
@JsonTypeName("ecsBlueGreenCreateServiceDataOutcome")
@RecasterAlias("io.harness.cdng.ecs.EcsBlueGreenCreateServiceDataOutcome")
public class EcsBlueGreenCreateServiceDataOutcome implements Outcome, ExecutionSweepingOutput {
  boolean isNewServiceCreated;
  String serviceName;
  String loadBalancer;
  String listenerArn;
  String listenerRuleArn;
  String targetGroupArn;
}
