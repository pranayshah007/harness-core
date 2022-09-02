package io.harness.delegate.task.artifacts.gar;

import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class GarDelegateResponse extends ArtifactDelegateResponse {
  String version;
  @Builder
  public GarDelegateResponse(ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType, String version) {
    super(buildDetails, sourceType);
    this.version = version;
  }
}
