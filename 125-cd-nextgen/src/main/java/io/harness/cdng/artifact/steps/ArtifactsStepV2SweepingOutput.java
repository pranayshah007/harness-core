package io.harness.cdng.artifact.steps;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CI)
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@TypeAlias("ArtifactsStepV2SweepingOutput")
@JsonTypeName("artifactsStepV2SweepingOutput")
@RecasterAlias("io.harness.cdng.artifact.steps.ArtifactsStepV2SweepingOutput")
public class ArtifactsStepV2SweepingOutput implements ExecutionSweepingOutput {
  String primaryArtifactTaskId;
  @Builder.Default Map<String, ArtifactConfig> artifactConfigMap = new HashMap<>();
}
