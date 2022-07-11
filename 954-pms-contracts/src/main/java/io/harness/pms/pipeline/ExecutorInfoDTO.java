package io.harness.pms.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.plan.TriggerType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(PIPELINE)
@Schema(name = "ExecutorInfo", description = "This contains basic information about who/what started an Execution",
    hidden = true)
public class ExecutorInfoDTO {
  TriggerType triggerType;
  String username;
  String email;
}
