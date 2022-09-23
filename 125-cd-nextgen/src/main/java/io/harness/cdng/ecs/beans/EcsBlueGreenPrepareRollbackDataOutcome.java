package io.harness.cdng.ecs.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("ecsBlueGreenPrepareRollbackDataOutcome")
@JsonTypeName("ecsBlueGreenPrepareRollbackDataOutcome")
@RecasterAlias("io.harness.cdng.ecs.EcsBlueGreenPrepareRollbackDataOutcome")
public class EcsBlueGreenPrepareRollbackDataOutcome implements Outcome, ExecutionSweepingOutput {
  String serviceName;
  String createServiceRequestBuilderString;
  List<String> registerScalableTargetRequestBuilderStrings;
  List<String> registerScalingPolicyRequestBuilderStrings;
  boolean isFirstDeployment;
  String loadBalancer;
  String listenerArn;
  String listenerRuleArn;
  String targetGroupArn;
}
