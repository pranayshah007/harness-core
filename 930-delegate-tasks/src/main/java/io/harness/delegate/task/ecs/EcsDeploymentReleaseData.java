package io.harness.delegate.task.ecs;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class EcsDeploymentReleaseData {
  private EcsInfraConfig ecsInfraConfig;
  private String serviceName;
}
