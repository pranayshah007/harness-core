package io.harness.cdng.ecs.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("ecsCanaryDeleteDataOutcome")
@JsonTypeName("ecsCanaryDeleteDataOutcome")
@RecasterAlias("io.harness.cdng.ecs.EcsCanaryDeleteDataOutcome")
public class EcsCanaryDeleteDataOutcome implements ExecutionSweepingOutput {
  String createServiceRequestBuilderString;
  String ecsServiceNameSuffix;
}
