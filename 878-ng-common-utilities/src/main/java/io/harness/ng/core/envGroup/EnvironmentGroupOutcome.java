package io.harness.ng.core.envGroup;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonTypeName("environmentGroupOutcome")
@RecasterAlias("io.harness.ng.core.envGroup.EnvironmentGroupOutcome")
@OwnedBy(CDC)
public class EnvironmentGroupOutcome implements Outcome, ExecutionSweepingOutput {
  String name;
  String identifier;
  String description;
  Map<String, String> tags;
}
