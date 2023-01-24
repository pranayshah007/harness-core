package io.harness.delegate.task.artifacts.mappers;

import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.googlecloudsource.GoogleCloudSourceArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.googlecloudsource.GoogleCloudSourceArtifactDelegateResponse;

import lombok.experimental.UtilityClass;

@UtilityClass
public class GoogleCloudSourceRequestResponseMapper {
  public GoogleCloudSourceArtifactDelegateResponse toGoogleCloudSourceResponse(
      GoogleCloudSourceArtifactDelegateRequest request) {
    return GoogleCloudSourceArtifactDelegateResponse.builder()
        .sourceType(ArtifactSourceType.GOOGLE_CLOUD_SOURCE_ARTIFACT)
        .project(request.getProject())
        .repository(request.getRepository())
        .sourceDirectory(request.getSourceDirectory())
        .build();
  }
}
