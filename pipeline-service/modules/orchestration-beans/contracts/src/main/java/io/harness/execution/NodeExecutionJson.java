package io.harness.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.data.OrchestrationMap;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.data.stepparameters.PmsStepParameters;

import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;

@OwnedBy(PIPELINE)
@Value
public class NodeExecutionJson extends OrchestrationMap {
  String name;
  String identifier;

  NodeExecutionJson() {
    this.name = null;
    this.identifier = null;
  }
}
