/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.googleartifactregistry.mappers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARBuildDetailsDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARRepositoryDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GARResponseDTO;
import io.harness.cdng.artifact.resources.googleartifactregistry.dtos.GarRepositoryDTOList;
import io.harness.delegate.task.artifacts.gar.GarDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@UtilityClass
public class GARResourceMapper {
  public static GARResponseDTO toGarResponse(List<GarDelegateResponse> garDelegateResponses) {
    List<GARBuildDetailsDTO> detailsDTOList = garDelegateResponses.stream()
                                                  .map(response -> toGarBuildDetailsDTO(response.getBuildDetails()))
                                                  .collect(Collectors.toList());
    return GARResponseDTO.builder().buildDetailsList(detailsDTOList).build();
  }
  public static GARBuildDetailsDTO toGarBuildDetailsDTO(ArtifactBuildDetailsNG artifactBuildDetailsNG) {
    return GARBuildDetailsDTO.builder()
        .version(artifactBuildDetailsNG.getNumber())
        .metadata(artifactBuildDetailsNG.getMetadata())
        .build();
  }

  public static GARRepositoryDTO toGarRepository(List<GarDelegateResponse> garDelegateResponses) {
    List<GarRepositoryDTOList> detailsDTOList = garDelegateResponses.stream()
                                                    .map(response -> toGarRepositoryDTOList(response.getBuildDetails()))
                                                    .collect(Collectors.toList());
    return GARRepositoryDTO.builder().repositories(detailsDTOList).build();
  }

  public static GarRepositoryDTOList toGarRepositoryDTOList(ArtifactBuildDetailsNG artifactBuildDetailsNG) {
    return GarRepositoryDTOList.builder().repository(artifactBuildDetailsNG.getUiDisplayName()).build();
  }
}
