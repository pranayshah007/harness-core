package io.harness.cdng.ecs.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.pms.sdk.core.steps.io.PassThroughData;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("ecsGitFetchPassThroughData")
@RecasterAlias("io.harness.cdng.ecs.beans.EcsGitFetchPassThroughData")
public class EcsGitFetchPassThroughData implements PassThroughData {
  InfrastructureOutcome infrastructureOutcome;
}
