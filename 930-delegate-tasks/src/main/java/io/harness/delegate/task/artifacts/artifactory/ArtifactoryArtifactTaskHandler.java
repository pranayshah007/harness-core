/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.artifactory;

import static io.harness.artifactory.service.ArtifactoryRegistryService.DEFAULT_ARTIFACT_DIRECTORY;
import static io.harness.artifactory.service.ArtifactoryRegistryService.DEFAULT_ARTIFACT_FILTER;
import static io.harness.artifactory.service.ArtifactoryRegistryService.MAX_NO_OF_TAGS_PER_ARTIFACT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.artifactory.service.ArtifactoryRegistryService;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.mappers.ArtifactoryRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDP)
public class ArtifactoryArtifactTaskHandler extends DelegateArtifactTaskHandler<ArtifactSourceDelegateRequest> {
  private final ArtifactoryRegistryService artifactoryRegistryService;
  private final SecretDecryptionService secretDecryptionService;
  private ArtifactoryNgService artifactoryNgService;
  private ArtifactoryRequestMapper artifactoryRequestMapper;

  @Override
  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(
      ArtifactSourceDelegateRequest artifactSourceDelegateRequest) {
    ArtifactoryArtifactDelegateRequest attributesRequest =
        (ArtifactoryArtifactDelegateRequest) artifactSourceDelegateRequest;

    BuildDetailsInternal lastSuccessfulBuild;

    ArtifactoryConfigRequest artifactoryConfig =
        ArtifactoryRequestResponseMapper.toArtifactoryInternalConfig(attributesRequest);

    if (isRegex(attributesRequest)) {
      lastSuccessfulBuild = artifactoryRegistryService.getLastSuccessfulBuildFromRegex(artifactoryConfig,
          attributesRequest.getRepositoryName(), attributesRequest.getArtifactPath(),
          attributesRequest.getRepositoryFormat(), attributesRequest.getTagRegex());

    } else {
      lastSuccessfulBuild =
          artifactoryRegistryService.verifyBuildNumber(artifactoryConfig, attributesRequest.getRepositoryName(),
              attributesRequest.getArtifactPath(), attributesRequest.getRepositoryFormat(), attributesRequest.getTag());
    }

    ArtifactoryArtifactDelegateResponse artifactoryDockerArtifactDelegateResponse =
        ArtifactoryRequestResponseMapper.toArtifactoryDockerResponse(lastSuccessfulBuild, attributesRequest);

    return getSuccessTaskExecutionResponse(Collections.singletonList(artifactoryDockerArtifactDelegateResponse));
  }

  @Override
  public ArtifactTaskExecutionResponse getBuilds(ArtifactSourceDelegateRequest artifactSourceDelegateRequest) {
    if (artifactSourceDelegateRequest instanceof ArtifactoryGenericArtifactDelegateRequest) {
      return fetchBuildsForArtifactoryGeneric(
          (ArtifactoryGenericArtifactDelegateRequest) artifactSourceDelegateRequest, null);
    } else {
      return fetchBuildsForArtifactoryDocker((ArtifactoryArtifactDelegateRequest) artifactSourceDelegateRequest);
    }
  }

  @Override
  public ArtifactTaskExecutionResponse validateArtifactServer(
      ArtifactSourceDelegateRequest artifactSourceDelegateRequest) {
    ArtifactoryArtifactDelegateRequest attributesRequest =
        (ArtifactoryArtifactDelegateRequest) artifactSourceDelegateRequest;
    boolean isServerValidated = artifactoryRegistryService.validateCredentials(
        ArtifactoryRequestResponseMapper.toArtifactoryInternalConfig(attributesRequest));
    return ArtifactTaskExecutionResponse.builder().isArtifactServerValid(isServerValidated).build();
  }

  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(
      List<ArtifactoryArtifactDelegateResponse> responseList) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }

  public ArtifactTaskExecutionResponse getSuccessTaskExecutionResponseGeneric(
      List<ArtifactoryGenericArtifactDelegateResponse> responseList) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }

  boolean isRegex(ArtifactoryArtifactDelegateRequest artifactDelegateRequest) {
    return EmptyPredicate.isNotEmpty(artifactDelegateRequest.getTagRegex());
  }

  public void decryptRequestDTOs(ArtifactSourceDelegateRequest artifactoryRequest) {
    if (artifactoryRequest instanceof ArtifactoryGenericArtifactDelegateRequest) {
      ArtifactoryGenericArtifactDelegateRequest artifactoryGenericArtifactDelegateRequest =
          (ArtifactoryGenericArtifactDelegateRequest) artifactoryRequest;
      if (artifactoryGenericArtifactDelegateRequest.getArtifactoryConnectorDTO().getAuth() != null) {
        secretDecryptionService.decrypt(
            artifactoryGenericArtifactDelegateRequest.getArtifactoryConnectorDTO().getAuth().getCredentials(),
            artifactoryGenericArtifactDelegateRequest.getEncryptedDataDetails());
      }
    } else {
      ArtifactoryArtifactDelegateRequest artifactoryDockerArtifactDelegateRequest =
          (ArtifactoryArtifactDelegateRequest) artifactoryRequest;
      if (artifactoryDockerArtifactDelegateRequest.getArtifactoryConnectorDTO().getAuth() != null) {
        secretDecryptionService.decrypt(
            artifactoryDockerArtifactDelegateRequest.getArtifactoryConnectorDTO().getAuth().getCredentials(),
            artifactoryDockerArtifactDelegateRequest.getEncryptedDataDetails());
      }
    }
  }

  private ArtifactTaskExecutionResponse fetchBuildsForArtifactoryDocker(
      ArtifactoryArtifactDelegateRequest attributesRequest) {
    List<BuildDetailsInternal> builds = artifactoryRegistryService.getBuilds(
        ArtifactoryRequestResponseMapper.toArtifactoryInternalConfig(attributesRequest),
        attributesRequest.getRepositoryName(), attributesRequest.getArtifactPath(),
        attributesRequest.getRepositoryFormat());
    List<ArtifactoryArtifactDelegateResponse> artifactoryDockerArtifactDelegateResponseList =
        builds.stream()
            .sorted(new BuildDetailsInternalComparatorDescending())
            .map(build -> ArtifactoryRequestResponseMapper.toArtifactoryDockerResponse(build, attributesRequest))
            .collect(Collectors.toList());
    return getSuccessTaskExecutionResponse(artifactoryDockerArtifactDelegateResponseList);
  }

  private ArtifactTaskExecutionResponse fetchBuildsForArtifactoryGeneric(
      ArtifactoryGenericArtifactDelegateRequest artifactoryGenericArtifactDelegateRequest,
      LogCallback executionLogCallback) {
    decryptRequestDTOs(artifactoryGenericArtifactDelegateRequest);
    ArtifactoryConfigRequest artifactoryConfigRequest = artifactoryRequestMapper.toArtifactoryRequest(
        artifactoryGenericArtifactDelegateRequest.getArtifactoryConnectorDTO());
    String artifactDirectory = artifactoryGenericArtifactDelegateRequest.getArtifactDirectory();
    if (EmptyPredicate.isEmpty(artifactDirectory)) {
      saveLogs(executionLogCallback,
          "Artifact Directory is Empty, assuming Artifacts are present in root of the repository");
      artifactDirectory = DEFAULT_ARTIFACT_DIRECTORY;
    }
    String filePath = Paths.get(artifactDirectory, DEFAULT_ARTIFACT_FILTER).toString();

    List<BuildDetails> buildDetails = artifactoryNgService.getArtifactList(artifactoryConfigRequest,
        artifactoryGenericArtifactDelegateRequest.getRepositoryName(), filePath, MAX_NO_OF_TAGS_PER_ARTIFACT,
        artifactoryGenericArtifactDelegateRequest.getArtifactPathFilter(), artifactDirectory);
    String finalArtifactDirectory = artifactDirectory;
    buildDetails = buildDetails.stream()
                       .map(buildDetail -> {
                         String artifactoryPath = buildDetail.getArtifactPath();
                         if (!finalArtifactDirectory.equals(".")) {
                           artifactoryPath = buildDetail.getArtifactPath().replaceFirst(finalArtifactDirectory, "");
                         }
                         if (!artifactoryPath.isEmpty() && artifactoryPath.charAt(0) == '/') {
                           artifactoryPath = artifactoryPath.substring(1);
                         }
                         buildDetail.setArtifactPath(artifactoryPath);
                         return buildDetail;
                       })
                       .collect(Collectors.toList());
    List<ArtifactoryGenericArtifactDelegateResponse> artifactDelegateResponses =
        buildDetails.stream()
            .map(build
                -> ArtifactoryRequestResponseMapper.toArtifactoryGenericResponse(
                    build, artifactoryGenericArtifactDelegateRequest))
            .collect(Collectors.toList());
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(artifactDelegateResponses)
        .buildDetails(buildDetails)
        .build();
  }

  private void saveLogs(LogCallback executionLogCallback, String message) {
    if (executionLogCallback != null) {
      executionLogCallback.saveExecutionLog(message);
    }
  }
}
