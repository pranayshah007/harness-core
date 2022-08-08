/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.acr.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.azure.AcrBuildDetailsDTO;
import io.harness.delegate.beans.azure.AcrResponseDTO;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class AcrResourceMapper {
  public AcrResponseDTO toAcrResponse(List<AcrArtifactDelegateResponse> acrArtifactDelegateResponseList) {
    List<AcrBuildDetailsDTO> detailsDTOList =
        acrArtifactDelegateResponseList.stream()
            .map(response -> toAcrBuildDetailsDTO(response.getBuildDetails(), response.getRepository()))
            .collect(Collectors.toList());
    return AcrResponseDTO.builder().buildDetailsList(detailsDTOList).build();
  }

  public AcrBuildDetailsDTO toAcrBuildDetailsDTO(ArtifactBuildDetailsNG artifactBuildDetailsNG, String repository) {
    return AcrBuildDetailsDTO.builder()
        .tag(artifactBuildDetailsNG.getNumber())
        .buildUrl(artifactBuildDetailsNG.getBuildUrl())
        .labels(artifactBuildDetailsNG.getLabelsMap())
        .metadata(artifactBuildDetailsNG.getMetadata())
        .repository(repository)
        .build();
  }
}
