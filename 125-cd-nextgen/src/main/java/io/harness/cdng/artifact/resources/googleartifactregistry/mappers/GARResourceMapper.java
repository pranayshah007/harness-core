package io.harness.cdng.artifact.resources.googleartifactregistry.mappers;

import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARBuildDetailsDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARResponseDTO;
import io.harness.delegate.task.artifacts.gar.GarDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class GARResourceMapper {
  public GARResponseDTO toGarResponse(List<GarDelegateResponse> garDelegateResponses) {
    List<GARBuildDetailsDTO> detailsDTOList = garDelegateResponses.stream()
                                                  .map(response -> toGarBuildDetailsDTO(response.getBuildDetails()))
                                                  .collect(Collectors.toList());
    return GARResponseDTO.builder().buildDetailsList(detailsDTOList).build();
  }
  public GARBuildDetailsDTO toGarBuildDetailsDTO(ArtifactBuildDetailsNG artifactBuildDetailsNG) {
    return GARBuildDetailsDTO.builder().version(artifactBuildDetailsNG.getNumber()).build();
  }
}
