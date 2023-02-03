/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.gcr.mappers;

import io.harness.cdng.artifact.resources.gcr.dtos.GcrBuildDetailsDTO;
import io.harness.cdng.artifact.resources.gcr.dtos.GcrResponseDTO;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
public class GcrResourceMapper {
  public GcrResponseDTO toGcrResponse(List<GcrArtifactDelegateResponse> gcrArtifactDelegateResponseList) {
    List<GcrBuildDetailsDTO> detailsDTOList =
        gcrArtifactDelegateResponseList.stream()
            .map(response -> toGcrBuildDetailsDTO(response.getBuildDetails(), response.getImagePath()))
            .collect(Collectors.toList());
    return GcrResponseDTO.builder().buildDetailsList(detailsDTOList).build();
  }

  public GcrBuildDetailsDTO toGcrBuildDetailsDTO(ArtifactBuildDetailsNG artifactBuildDetailsNG, String imagePath) {
    return GcrBuildDetailsDTO.builder()
        .tag(artifactBuildDetailsNG.getNumber())
        .buildUrl(artifactBuildDetailsNG.getBuildUrl())
        .labels(artifactBuildDetailsNG.getLabelsMap())
        .metadata(artifactBuildDetailsNG.getMetadata())
        .imagePath(imagePath)
        .build();
  }
}
