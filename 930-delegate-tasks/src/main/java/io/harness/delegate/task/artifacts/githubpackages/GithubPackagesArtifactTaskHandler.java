/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.githubpackages;

import io.harness.artifacts.comparator.BuildDetailsComparatorDescending;
import io.harness.artifacts.githubpackages.service.GithubPackagesRegistryService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.mappers.GithubPackagesRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
public class GithubPackagesArtifactTaskHandler
    extends DelegateArtifactTaskHandler<GithubPackagesArtifactDelegateRequest> {
  private static final int ARTIFACT_RETENTION_SIZE = 25;
  private static final int MAX_RETRY = 5;
  private static final int MAX_NO_OF_TAGS_PER_IMAGE = 10000;

  private final SecretDecryptionService secretDecryptionService;
  private final GithubPackagesRegistryService githubPackagesRegistryService;

  public ArtifactTaskExecutionResponse getBuilds(GithubPackagesArtifactDelegateRequest attributes) {
    List<BuildDetails> builds = githubPackagesRegistryService.getBuilds(
        GithubPackagesRequestResponseMapper.toGithubPackagesInternalConfig(attributes), attributes.getPackageName(),
        attributes.getPackageType(), MAX_NO_OF_TAGS_PER_IMAGE);

    List<GithubPackagesArtifactDelegateResponse> githubPackagesArtifactDelegateResponses =
        builds.stream()
            .sorted(new BuildDetailsComparatorDescending())
            .map(build -> GithubPackagesRequestResponseMapper.toGithubPackagesResponse(build, attributes))
            .collect(Collectors.toList());

    return getSuccessTaskExecutionResponse(githubPackagesArtifactDelegateResponses);
  }

  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(GithubPackagesArtifactDelegateRequest attributesRequest) {
    BuildDetails lastSuccessfulBuild;

    if (isRegex(attributesRequest)) {
      lastSuccessfulBuild = githubPackagesRegistryService.getLastSuccessfulBuildFromRegex(
          GithubPackagesRequestResponseMapper.toGithubPackagesInternalConfig(attributesRequest),
          attributesRequest.getPackageName(), attributesRequest.getPackageType(), attributesRequest.getVersionRegex());

    } else {
      lastSuccessfulBuild = githubPackagesRegistryService.getBuild(
          GithubPackagesRequestResponseMapper.toGithubPackagesInternalConfig(attributesRequest),
          attributesRequest.getPackageName(), attributesRequest.getPackageType(), attributesRequest.getVersion());
    }

    GithubPackagesArtifactDelegateResponse githubPackagesArtifactDelegateResponse =
        GithubPackagesRequestResponseMapper.toGithubPackagesResponse(lastSuccessfulBuild, attributesRequest);

    return getSuccessTaskExecutionResponse(Collections.singletonList(githubPackagesArtifactDelegateResponse));
  }

  public void decryptRequestDTOs(GithubPackagesArtifactDelegateRequest attributes) {
    if (attributes.getGithubConnectorDTO().getAuthentication() != null) {
      secretDecryptionService.decrypt(attributes.getGithubConnectorDTO().getAuthentication().getCredentials(),
          attributes.getEncryptedDataDetails());
    }
  }

  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(
      List<GithubPackagesArtifactDelegateResponse> responseList) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }

  boolean isRegex(GithubPackagesArtifactDelegateRequest artifactDelegateRequest) {
    return EmptyPredicate.isNotEmpty(artifactDelegateRequest.getVersionRegex());
  }
}
