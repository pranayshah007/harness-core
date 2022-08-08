package io.harness.delegate.task.artifacts.mappers;

import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.gar.beans.GarInternalConfig;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.gar.GarDelegateRequest;
import io.harness.delegate.task.artifacts.gar.GarDelegateResponse;

import lombok.experimental.UtilityClass;

@UtilityClass
public class GarRequestResponseMapper {
  public GarInternalConfig toGarInternalConfig(GarDelegateRequest request, String bearerToken) {
    return GarInternalConfig.builder()
        .bearerToken(bearerToken)
        .project(request.getProject())
        .repositoryName(request.getRepositoryName())
        .region(request.getRegion())
        .pkg(request.getPkg())
        .maxBuilds(request.getMaxBuilds())
        .build();
  }
  public GarDelegateResponse toGarResponse(BuildDetailsInternal buildDetailsInternal, GarDelegateRequest request) {
    return GarDelegateResponse.builder()
        .buildDetails(ArtifactBuildDetailsMapper.toBuildDetailsNG(buildDetailsInternal))
        .sourceType(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY)
        .version(buildDetailsInternal.getNumber())
        .build();
  }
}
