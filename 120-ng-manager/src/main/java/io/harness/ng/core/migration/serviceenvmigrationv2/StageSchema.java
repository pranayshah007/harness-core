package io.harness.ng.core.migration.serviceenvmigrationv2;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@OwnedBy(CDP)
@Data
@Builder
public class StageSchema {
  @JsonProperty("stage") DeploymentStageNode stageNode;
}
