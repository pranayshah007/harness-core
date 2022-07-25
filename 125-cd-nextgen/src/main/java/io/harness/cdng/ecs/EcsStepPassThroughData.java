package io.harness.cdng.ecs;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

import java.util.List;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("ecsStepPassThroughData")
@RecasterAlias("io.harness.cdng.ecs.EcsStepPassThroughData")
public class EcsStepPassThroughData implements PassThroughData {
  List<ManifestOutcome> ecsManifestOutcomes;
  InfrastructureOutcome infrastructureOutcome;
}
