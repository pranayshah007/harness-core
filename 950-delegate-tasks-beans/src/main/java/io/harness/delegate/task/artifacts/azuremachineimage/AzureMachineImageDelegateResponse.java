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
  String name;
  String subscriptionId;

  @Builder
  public AzureMachineImageDelegateResponse(
      ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType, String name, String subscriptionId) {
    super(buildDetails, sourceType);
    this.name = name;
    this.subscriptionId = subscriptionId;
  }
}
