/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.artifactory.mappers;
import static software.wings.utils.RepositoryType.generic;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryArtifactBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryDockerBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryGenericBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryResponseDTO;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryGenericArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class ArtifactoryResourceMapper {
  public List<ArtifactoryArtifactBuildDetailsDTO> toArtifactoryArtifactBuildDetailsDTO(
      List<BuildDetails> buildDetails) {
    return buildDetails.stream()
        .map(b
            -> ArtifactoryArtifactBuildDetailsDTO.builder()
                   .artifactName(b.getNumber())
                   .artifactPath(b.getArtifactPath())
                   .build())
        .collect(Collectors.toList());
  }

  public ArtifactoryResponseDTO getArtifactoryResponseDTO(
      ArtifactTaskExecutionResponse artifactTaskExecutionResponse, String repositoryFormat) {
    if (repositoryFormat.equals(generic.name())) {
      List<ArtifactoryGenericArtifactDelegateResponse> artifactoryArtifactDelegateResponses =
          artifactTaskExecutionResponse.getBuildDetails()
              .stream()
              .map(delegateResponse
                  -> ArtifactoryGenericArtifactDelegateResponse.builder()
                         .artifactPath(delegateResponse.getArtifactPath())
                         .build())
              .collect(Collectors.toList());
      return ArtifactoryResourceMapper.toArtifactoryGenericResponse(artifactoryArtifactDelegateResponses);
    }
    List<ArtifactoryArtifactDelegateResponse> artifactoryArtifactDelegateResponses =
        artifactTaskExecutionResponse.getArtifactDelegateResponses()
            .stream()
            .map(delegateResponse -> (ArtifactoryArtifactDelegateResponse) delegateResponse)
            .collect(Collectors.toList());
    return ArtifactoryResourceMapper.toArtifactoryDockerResponse(artifactoryArtifactDelegateResponses);
  }

  public ArtifactoryResponseDTO toArtifactoryGenericResponse(
      List<ArtifactoryGenericArtifactDelegateResponse> artifactoryArtifactDelegateResponseList) {
    List<ArtifactoryBuildDetailsDTO> detailsDTOList =
        artifactoryArtifactDelegateResponseList.stream()
            .map(response -> toArtifactoryGenericBuildDetailsDTO(response.getArtifactPath()))
            .collect(Collectors.toList());
    return ArtifactoryResponseDTO.builder().buildDetailsList(detailsDTOList).build();
  }

  public ArtifactoryResponseDTO toArtifactoryDockerResponse(
      List<ArtifactoryArtifactDelegateResponse> artifactoryArtifactDelegateResponseList) {
    List<ArtifactoryBuildDetailsDTO> detailsDTOList =
        artifactoryArtifactDelegateResponseList.stream()
            .map(response -> toArtifactoryDockerBuildDetailsDTO(response.getBuildDetails(), response.getArtifactPath()))
            .collect(Collectors.toList());
    return ArtifactoryResponseDTO.builder().buildDetailsList(detailsDTOList).build();
  }

  public ArtifactoryDockerBuildDetailsDTO toArtifactoryDockerBuildDetailsDTO(
      ArtifactBuildDetailsNG artifactBuildDetailsNG, String imagePath) {
    return ArtifactoryDockerBuildDetailsDTO.builder()
        .tag(artifactBuildDetailsNG.getNumber())
        .buildUrl(artifactBuildDetailsNG.getBuildUrl())
        .labels(artifactBuildDetailsNG.getLabelsMap())
        .metadata(artifactBuildDetailsNG.getMetadata())
        .imagePath(imagePath)
        .build();
  }

  public ArtifactoryGenericBuildDetailsDTO toArtifactoryGenericBuildDetailsDTO(String artifactPath) {
    return ArtifactoryGenericBuildDetailsDTO.builder().artifactPath(artifactPath).build();
  }
}
