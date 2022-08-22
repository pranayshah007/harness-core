package io.harness.cdng.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.artifacts.ArtifactSourceConstants;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;

@OwnedBy(CDC)
@Data
@Builder
@JsonTypeName(ArtifactSourceConstants.GOOGLE_ARTIFACT_REGISTRY_NAME)
public class GarArtifactSummary implements ArtifactSummary {
  @JsonProperty("package") String pkg;
  String version;
  String region;
  String repositoryName;
  String project;
  @Override
  public String getType() {
    return ArtifactSourceConstants.GOOGLE_ARTIFACT_REGISTRY_NAME;
  }

  @Override
  public String getDisplayName() {
    return pkg + ":" + version;
  }
}
