package io.harness.delegate.task.artifacts.azuremachineimage;

import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class AzureMachineImageDelegateResponse extends ArtifactDelegateResponse {
  String version;

  @Builder
  public AzureMachineImageDelegateResponse(
      ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType, String name) {
    super(buildDetails, sourceType);
    this.version = name;
  }
}
