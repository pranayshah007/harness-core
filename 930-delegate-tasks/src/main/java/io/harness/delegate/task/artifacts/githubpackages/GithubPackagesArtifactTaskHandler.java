/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.githubpackages;

import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.comparator.BuildDetailsComparatorDescending;
import io.harness.artifacts.githubpackages.service.GithubPackagesRegistryService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.mappers.GithubPackagesRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.utils.GithubPackageUtils;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.jenkins.BuildDetails;

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
public class GithubPackagesArtifactTaskHandler
    extends DelegateArtifactTaskHandler<GithubPackagesArtifactDelegateRequest> {
  private final SecretDecryptionService secretDecryptionService;
  private final GithubPackagesRegistryService githubPackagesRegistryService;

  public ArtifactTaskExecutionResponse getBuilds(GithubPackagesArtifactDelegateRequest attributes) {
    List<BuildDetails> builds = githubPackagesRegistryService.getBuilds(
        GithubPackagesRequestResponseMapper.toGithubPackagesInternalConfig(attributes), attributes.getPackageName(),
        attributes.getPackageType(), attributes.getOrg(), attributes.getVersionRegex());

    List<GithubPackagesArtifactDelegateResponse> githubPackagesArtifactDelegateResponses =
        builds.stream()
            .sorted(new BuildDetailsComparatorDescending())
            .map(build -> GithubPackagesRequestResponseMapper.toGithubPackagesResponse(build, attributes))
            .collect(Collectors.toList());

    return getSuccessTaskExecutionResponse(githubPackagesArtifactDelegateResponses, builds);
  }

  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(GithubPackagesArtifactDelegateRequest attributesRequest) {
    BuildDetails lastSuccessfulBuild;

    if (isRegex(attributesRequest)) {
      lastSuccessfulBuild = githubPackagesRegistryService.getLastSuccessfulBuildFromRegex(
          GithubPackagesRequestResponseMapper.toGithubPackagesInternalConfig(attributesRequest),
          attributesRequest.getPackageName(), attributesRequest.getPackageType(), attributesRequest.getVersionRegex(),
          attributesRequest.getOrg());

    } else {
      lastSuccessfulBuild = githubPackagesRegistryService.getBuild(
          GithubPackagesRequestResponseMapper.toGithubPackagesInternalConfig(attributesRequest),
          attributesRequest.getPackageName(), attributesRequest.getPackageType(), attributesRequest.getVersion(),
          attributesRequest.getOrg());
    }

    if (attributesRequest.getPackageType().equals("maven")) {
      String url = githubPackagesRegistryService.fetchDownloadUrl(
          GithubPackagesRequestResponseMapper.toGithubPackagesInternalConfig(attributesRequest),
          attributesRequest.getPackageType(), attributesRequest.getOrg(), attributesRequest.getArtifactId(),
          attributesRequest.getUser(), attributesRequest.getExtension(), attributesRequest.getRepository(),
          attributesRequest.getPackageName(), lastSuccessfulBuild.getNumber(), attributesRequest.getGroupId());
      Map<String, String> metaData = lastSuccessfulBuild.getMetadata();
      metaData.put(ArtifactMetadataKeys.url, url);
      lastSuccessfulBuild.setMetadata(metaData);
    }

    GithubPackagesArtifactDelegateResponse githubPackagesArtifactDelegateResponse =
        GithubPackagesRequestResponseMapper.toGithubPackagesResponse(lastSuccessfulBuild, attributesRequest);

    return getSuccessTaskExecutionResponse(Collections.singletonList(githubPackagesArtifactDelegateResponse),
        Collections.singletonList(lastSuccessfulBuild));
  }

  public ArtifactTaskExecutionResponse listPackages(GithubPackagesArtifactDelegateRequest attributes) {
    List<Map<String, String>> packageDetails = githubPackagesRegistryService.listPackages(
        GithubPackagesRequestResponseMapper.toGithubPackagesInternalConfig(attributes), attributes.getPackageType(),
        attributes.getOrg());
    return getSuccessTaskExecutionResponse(
        GithubPackagesRequestResponseMapper.toGithubPackagesResponse(packageDetails, attributes), null);
  }

  public void decryptRequestDTOs(GithubPackagesArtifactDelegateRequest attributes) {
    GithubPackageUtils.decryptRequestDTOs(
        attributes.getGithubConnectorDTO(), attributes.getEncryptedDataDetails(), secretDecryptionService);
  }

  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(
      List<GithubPackagesArtifactDelegateResponse> responseList, List<BuildDetails> buildDetails) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .buildDetails(buildDetails)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }

  boolean isRegex(GithubPackagesArtifactDelegateRequest artifactDelegateRequest) {
    return EmptyPredicate.isNotEmpty(artifactDelegateRequest.getVersionRegex());
  }
}
