package io.harness.polling.bean.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.pms.yaml.ParameterField;

import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
public class GARrtifactInfo implements ArtifactInfo {
  String connectorRef;
  String jobName;

  @Override
  public ArtifactSourceType getType() {
    return ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY;
  }

  @Override
  public ArtifactConfig toArtifactConfig() {
    return GoogleArtifactRegistryConfig.builder()
        .connectorRef(ParameterField.<String>builder().value(connectorRef).build())
        .build();
  }
}
