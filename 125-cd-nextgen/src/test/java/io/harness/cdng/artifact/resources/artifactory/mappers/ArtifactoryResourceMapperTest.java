/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.artifactory.mappers;

import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static software.wings.utils.RepositoryType.docker;
import static software.wings.utils.RepositoryType.generic;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryDockerBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryGenericBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryResponseDTO;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.rule.Owner;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ArtifactoryResourceMapperTest extends CategoryTest {
  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testToArtifactoryDockerResponse() {
    List<ArtifactoryArtifactDelegateResponse> artifactoryArtifactDelegateResponses =
        Lists.newArrayList(ArtifactoryArtifactDelegateResponse.builder()
                               .buildDetails(ArtifactBuildDetailsNG.builder().build())
                               .artifactPath("/")
                               .build());
    ArtifactoryResponseDTO nexusResponseDTO =
        ArtifactoryResourceMapper.toArtifactoryDockerResponse(artifactoryArtifactDelegateResponses);
    assertThat(nexusResponseDTO).isNotNull();
    assertThat(nexusResponseDTO.getBuildDetailsList()).isNotEmpty();
    assertThat(nexusResponseDTO.getBuildDetailsList().get(0))
        .isEqualTo(ArtifactoryDockerBuildDetailsDTO.builder().imagePath("/").build());
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetArtifactoryGenericResponse() {
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder()
            .buildDetails(Arrays.asList(BuildDetails.Builder.aBuildDetails().withArtifactPath("PATH").build()))
            .build();
    String repositoryFormat = generic.name();
    ArtifactoryResponseDTO artifactoryResponseDTO =
        ArtifactoryResourceMapper.getArtifactoryResponseDTO(artifactTaskExecutionResponse, repositoryFormat);
    assertThat(artifactoryResponseDTO.getBuildDetailsList().size()).isEqualTo(1);
    assertThat(artifactoryResponseDTO.getBuildDetailsList().get(0))
        .isEqualTo(ArtifactoryGenericBuildDetailsDTO.builder().artifactPath("PATH").build());
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetArtifactoryDockerResponse() {
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse =
        ArtifactTaskExecutionResponse.builder()
            .artifactDelegateResponse(
                ArtifactoryArtifactDelegateResponse.builder()
                    .buildDetails(
                        ArtifactBuildDetailsNG.builder().number("123").buildUrl("a").metadata(new HashMap<>()).build())
                    .artifactPath("b")
                    .build())
            .build();
    String repositoryFormat = docker.name();
    ArtifactoryResponseDTO artifactoryResponseDTO =
        ArtifactoryResourceMapper.getArtifactoryResponseDTO(artifactTaskExecutionResponse, repositoryFormat);
    assertThat(artifactoryResponseDTO.getBuildDetailsList().size()).isEqualTo(1);
    assertThat(artifactoryResponseDTO.getBuildDetailsList().get(0))
        .isEqualTo(ArtifactoryDockerBuildDetailsDTO.builder()
                       .tag("123")
                       .buildUrl("a")
                       .metadata(new HashMap<>())
                       .imagePath("b")
                       .build());
  }
}
