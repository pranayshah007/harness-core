/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.nexus.mappers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusBuildDetailsDTO;
import io.harness.cdng.artifact.resources.nexus.dtos.NexusResponseDTO;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class NexusResourceMapper {
  public NexusResponseDTO toNexusResponse(List<NexusArtifactDelegateResponse> nexusArtifactDelegateResponseList) {
    List<NexusBuildDetailsDTO> detailsDTOList =
        nexusArtifactDelegateResponseList.stream()
            .map(response -> toNexusBuildDetailsDTO(response.getBuildDetails(), response.getArtifactPath()))
            .collect(Collectors.toList());
    return NexusResponseDTO.builder().buildDetailsList(detailsDTOList).build();
  }

  public NexusBuildDetailsDTO toNexusBuildDetailsDTO(ArtifactBuildDetailsNG artifactBuildDetailsNG, String imagePath) {
    return NexusBuildDetailsDTO.builder()
        .tag(artifactBuildDetailsNG.getNumber())
        .buildUrl(artifactBuildDetailsNG.getBuildUrl())
        .labels(artifactBuildDetailsNG.getLabelsMap())
        .metadata(artifactBuildDetailsNG.getMetadata())
        .imagePath(imagePath)
        .build();
  }
}
