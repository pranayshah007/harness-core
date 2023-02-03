/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.ecr.mappers;

import io.harness.cdng.artifact.resources.ecr.dtos.EcrBuildDetailsDTO;
import io.harness.cdng.artifact.resources.ecr.dtos.EcrResponseDTO;
import io.harness.delegate.task.artifacts.ecr.EcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EcrResourceMapper {
  public EcrResponseDTO toEcrResponse(List<EcrArtifactDelegateResponse> ecrArtifactDelegateResponseList) {
    List<EcrBuildDetailsDTO> detailsDTOList =
        ecrArtifactDelegateResponseList.stream()
            .map(response -> toEcrBuildDetailsDTO(response.getBuildDetails(), response.getImagePath()))
            .collect(Collectors.toList());
    return EcrResponseDTO.builder().buildDetailsList(detailsDTOList).build();
  }

  public EcrBuildDetailsDTO toEcrBuildDetailsDTO(ArtifactBuildDetailsNG artifactBuildDetailsNG, String imagePath) {
    return EcrBuildDetailsDTO.builder()
        .tag(artifactBuildDetailsNG.getNumber())
        .buildUrl(artifactBuildDetailsNG.getBuildUrl())
        .labels(artifactBuildDetailsNG.getLabelsMap())
        .metadata(artifactBuildDetailsNG.getMetadata())
        .imagePath(imagePath)
        .build();
  }
}
