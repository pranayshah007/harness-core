package io.harness.polling.bean.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GithubPackagesArtifactConfig;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.pms.yaml.ParameterField;

import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
public class GithubPackagesArtifactInfo implements ArtifactInfo {
  String connectorRef;
  String packageName;
  String org;
  String packageType;
  @Override
  public ArtifactSourceType getType() {
    return ArtifactSourceType.GITHUB_PACKAGES;
  }

  @Override
  public ArtifactConfig toArtifactConfig() {
    return GithubPackagesArtifactConfig.builder()
        .connectorRef(ParameterField.<String>builder().value(connectorRef).build())
        .packageName(ParameterField.<String>builder().value(packageName).build())
        .org(ParameterField.<String>builder().value(org).build())
        .packageType(ParameterField.<String>builder().value(packageType).build())
        .build();
  }
}
