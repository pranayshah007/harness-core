/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.docker;

import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.artifacts.docker.service.DockerRegistryService;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.mappers.DockerRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
public class DockerArtifactTaskHandler extends DelegateArtifactTaskHandler<DockerArtifactDelegateRequest> {
  private final DockerRegistryService dockerRegistryService;
  private final SecretDecryptionService secretDecryptionService;
  private final String ACCEPT_ALL_REGEX = "*";

  @Override
  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(DockerArtifactDelegateRequest attributesRequest) {
    BuildDetailsInternal lastSuccessfulBuild;
    ArtifactMetaInfo artifactMetaInfo = null;
    List<Map<String, String>> labels;
    if (isRegex(attributesRequest)) {
      lastSuccessfulBuild = dockerRegistryService.getLastSuccessfulBuildFromRegex(
          DockerRequestResponseMapper.toDockerInternalConfig(attributesRequest), attributesRequest.getImagePath(),
          attributesRequest.getTagRegex());
      labels = dockerRegistryService.getLabels(DockerRequestResponseMapper.toDockerInternalConfig(attributesRequest),
          attributesRequest.getImagePath(), Collections.singletonList(lastSuccessfulBuild.getNumber()));
    } else {
      lastSuccessfulBuild =
          dockerRegistryService.verifyBuildNumber(DockerRequestResponseMapper.toDockerInternalConfig(attributesRequest),
              attributesRequest.getImagePath(), attributesRequest.getTag());
      labels = dockerRegistryService.getLabels(DockerRequestResponseMapper.toDockerInternalConfig(attributesRequest),
          attributesRequest.getImagePath(), Collections.singletonList(attributesRequest.getTag()));
    }
    artifactMetaInfo = getArtifactMedataInfo(attributesRequest);
    DockerArtifactDelegateResponse dockerArtifactDelegateResponse;
    if (EmptyPredicate.isNotEmpty(labels)) {
      dockerArtifactDelegateResponse = DockerRequestResponseMapper.toDockerResponse(
          lastSuccessfulBuild, attributesRequest, labels.get(0), artifactMetaInfo);
    } else {
      dockerArtifactDelegateResponse =
          DockerRequestResponseMapper.toDockerResponse(lastSuccessfulBuild, attributesRequest);
    }
    return getSuccessTaskExecutionResponse(Collections.singletonList(dockerArtifactDelegateResponse));
  }

  @Override
  public ArtifactTaskExecutionResponse getBuilds(DockerArtifactDelegateRequest attributesRequest) {
    List<BuildDetailsInternal> builds = dockerRegistryService.getBuilds(
        DockerRequestResponseMapper.toDockerInternalConfig(attributesRequest), attributesRequest.getImagePath(),
        DockerRegistryService.MAX_NO_OF_TAGS_PER_IMAGE, attributesRequest.getTagRegex());
    List<DockerArtifactDelegateResponse> dockerArtifactDelegateResponseList =
        builds.stream()
            .sorted(new BuildDetailsInternalComparatorDescending())
            .map(build -> DockerRequestResponseMapper.toDockerResponse(build, attributesRequest))
            .collect(Collectors.toList());
    return getSuccessTaskExecutionResponse(dockerArtifactDelegateResponseList);
  }

  @Override
  public ArtifactTaskExecutionResponse getLabels(DockerArtifactDelegateRequest attributesRequest) {
    List<Map<String, String>> labels =
        dockerRegistryService.getLabels(DockerRequestResponseMapper.toDockerInternalConfig(attributesRequest),
            attributesRequest.getImagePath(), attributesRequest.getTagsList());
    return getSuccessTaskExecutionResponse(DockerRequestResponseMapper.toDockerResponse(labels, attributesRequest));
  }

  @Override
  public ArtifactTaskExecutionResponse validateArtifactServer(DockerArtifactDelegateRequest attributesRequest) {
    boolean isServerValidated = dockerRegistryService.validateCredentials(
        DockerRequestResponseMapper.toDockerInternalConfig(attributesRequest));
    return ArtifactTaskExecutionResponse.builder().isArtifactServerValid(isServerValidated).build();
  }

  @Override
  public ArtifactTaskExecutionResponse validateArtifactImage(DockerArtifactDelegateRequest attributesRequest) {
    boolean isArtifactImageValid = dockerRegistryService.verifyImageName(
        DockerRequestResponseMapper.toDockerInternalConfig(attributesRequest), attributesRequest.getImagePath());
    return ArtifactTaskExecutionResponse.builder().isArtifactSourceValid(isArtifactImageValid).build();
  }

  public ArtifactMetaInfo getArtifactMedataInfo(DockerArtifactDelegateRequest attributesRequest) {
    boolean shouldFetchDockerV2DigestSHA256 =
        Boolean.TRUE.equals(attributesRequest.getShouldFetchDockerV2DigestSHA256());
    return dockerRegistryService.getArtifactMetaInfo(
        DockerRequestResponseMapper.toDockerInternalConfig(attributesRequest), attributesRequest.getImagePath(),
        attributesRequest.getTag(), shouldFetchDockerV2DigestSHA256);
  }

  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(
      List<DockerArtifactDelegateResponse> responseList) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }

  boolean isRegex(DockerArtifactDelegateRequest artifactDelegateRequest) {
    return EmptyPredicate.isNotEmpty(artifactDelegateRequest.getTagRegex());
  }

  public void decryptRequestDTOs(DockerArtifactDelegateRequest dockerRequest) {
    if (dockerRequest.getDockerConnectorDTO().getAuth() != null) {
      secretDecryptionService.decrypt(
          dockerRequest.getDockerConnectorDTO().getAuth().getCredentials(), dockerRequest.getEncryptedDataDetails());
    }
  }
}
