/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifactory;

import static io.harness.artifactory.ArtifactoryClientImpl.getArtifactoryClient;
import static io.harness.artifactory.ArtifactoryClientImpl.handleAndRethrow;
import static io.harness.artifactory.ArtifactoryClientImpl.handleErrorResponse;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.jfrog.artifactory.client.ArtifactoryRequest.ContentType.JSON;
import static org.jfrog.artifactory.client.ArtifactoryRequest.Method.GET;
import static org.jfrog.artifactory.client.model.impl.PackageTypeImpl.docker;
import static org.jfrog.artifactory.client.model.impl.PackageTypeImpl.maven;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.DTO.ImagePathDtos;
import io.harness.artifactory.DTO.ImagePathsDTO;
import io.harness.artifacts.comparator.BuildDetailsComparatorDescending;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ArtifactoryRegistryException;
import io.harness.exception.NestedExceptionUtils;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.utils.RepositoryType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryResponse;
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl;
import org.jfrog.artifactory.client.model.impl.PackageTypeImpl;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class ArtifactoryNgServiceImpl implements ArtifactoryNgService {
  @Inject ArtifactoryClientImpl artifactoryClient;

  @Override
  public List<BuildDetails> getBuildDetails(
      ArtifactoryConfigRequest artifactoryConfig, String repositoryName, String artifactPath, int maxVersions) {
    return artifactoryClient.getBuildDetails(artifactoryConfig, repositoryName, artifactPath, maxVersions);
  }

  @Override
  public List<BuildDetails> getArtifactList(
      ArtifactoryConfigRequest artifactoryConfig, String repositoryName, String artifactPath, int maxVersions) {
    return artifactoryClient.getArtifactList(artifactoryConfig, repositoryName, artifactPath, maxVersions);
  }

  @Override
  public BuildDetails getLatestArtifact(ArtifactoryConfigRequest artifactoryConfig, String repositoryName,
      String artifactDirectory, String artifactPathFilter, String artifactPath, int maxVersions) {
    if (EmptyPredicate.isEmpty(artifactPath) && EmptyPredicate.isEmpty(artifactPathFilter)) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check ArtifactPath/ArtifactPathFilter field in Artifactory artifact configuration.",
          "Both Artifact Path and Artifact Path Filter cannot be empty",
          new ArtifactoryRegistryException("Could not find an artifact"));
    } else if (EmptyPredicate.isEmpty(artifactPathFilter)) {
      artifactPathFilter = artifactPath;
    }

    String filePath = Paths.get(artifactDirectory, artifactPathFilter).toString();

    List<BuildDetails> buildDetails =
        artifactoryClient.getArtifactList(artifactoryConfig, repositoryName, filePath, maxVersions);

    buildDetails = buildDetails.stream().sorted(new BuildDetailsComparatorDescending()).collect(Collectors.toList());
    if (buildDetails.isEmpty()) {
      if (EmptyPredicate.isEmpty(artifactPath)) {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Please check artifactPathFilter or artifactDirectory or repository field in Artifactory artifact .",
            String.format("Could not find any Artifact that match artifactPathFilter [%s] for Artifactory repository"
                    + " [%s] for generic artifactDirectory [%s] in registry [%s].",
                artifactPathFilter, repositoryName, artifactDirectory, artifactoryConfig.getArtifactoryUrl()),
            new ArtifactoryRegistryException(
                String.format("Could not find an artifact that matches artifactPathFilter '%s'", artifactPathFilter)));
      } else {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Please check artifactPath or artifactDirectory or repository field in Artifactory artifact configuration.",
            String.format("Could not find any Artifact with artifactPath [%s] for Artifactory repository"
                    + " [%s] for generic artifactDirectory [%s] in registry [%s].",
                artifactPath, repositoryName, artifactDirectory, artifactoryConfig.getArtifactoryUrl()),
            new ArtifactoryRegistryException(
                String.format("Could not find an artifact with artifactPath '%s'", artifactPath)));
      }
    }
    return buildDetails.get(0);
  }

  @Override
  public Map<String, String> getRepositories(ArtifactoryConfigRequest artifactoryConfig, String packageType) {
    RepositoryType repositoryType = RepositoryType.valueOf(packageType);

    switch (repositoryType) {
      case docker:
        return artifactoryClient.getRepositories(artifactoryConfig, Arrays.asList(docker));
      case maven:
        return artifactoryClient.getRepositories(artifactoryConfig, Arrays.asList(maven));
      case generic:
      case any:
      default:
        return artifactoryClient.getRepositories(artifactoryConfig,
            Arrays.stream(PackageTypeImpl.values()).filter(type -> docker != type).collect(toList()));
    }
  }

  @Override
  public ImagePathDtos getImagePaths(ArtifactoryConfigRequest artifactoryConfig, String repoKey) {
    return listDockerImages(getArtifactoryClient(artifactoryConfig), repoKey);
  }
  private ImagePathDtos listDockerImages(Artifactory artifactory, String repoKey) {
    List<String> images = new ArrayList<>();
    String errorOnListingDockerimages = "Error occurred while listing docker images from artifactory %s for Repo %s";
    try {
      log.info("Retrieving docker images from artifactory url {} and repo key {}", artifactory.getUri(), repoKey);
      ArtifactoryResponse artifactoryResponse = artifactory.restCall(new ArtifactoryRequestImpl()
                                                                         .apiUrl("api/docker/" + repoKey + "/v2"
                                                                             + "/_catalog")
                                                                         .method(GET)
                                                                         .responseType(JSON));
      handleErrorResponse(artifactoryResponse);
      Map response = artifactoryResponse.parseBody(Map.class);
      if (response != null) {
        images = (List<String>) response.get("repositories");
        if (isEmpty(images)) {
          log.info("No docker images from artifactory url {} and repo key {}", artifactory.getUri(), repoKey);
          images = new ArrayList<>();
        }
        log.info("Retrieving images from artifactory url {} and repo key {} success. Images {}", artifactory.getUri(),
            repoKey, images);
      }
    } catch (SocketTimeoutException e) {
      log.error(format(errorOnListingDockerimages, artifactory, repoKey), e);
      return ImagePathDtos.builder()
          .imagePaths(images.stream()
                          .map(imagepath -> (ImagePathsDTO.builder().imagePath(imagepath).build()))
                          .collect(Collectors.toList()))
          .build();

    } catch (Exception e) {
      log.error(format(errorOnListingDockerimages, artifactory, repoKey), e);
      handleAndRethrow(e, USER);
    }
    return ImagePathDtos.builder()
        .imagePaths(images.stream()
                        .map(imagepath -> (ImagePathsDTO.builder().imagePath(imagepath).build()))
                        .collect(Collectors.toList()))
        .build();
  }
  @Override
  public InputStream downloadArtifacts(ArtifactoryConfigRequest artifactoryConfig, String repoKey,
      Map<String, String> metadata, String artifactPathMetadataKey, String artifactFileNameMetadataKey) {
    return artifactoryClient.downloadArtifacts(
        artifactoryConfig, repoKey, metadata, artifactPathMetadataKey, artifactFileNameMetadataKey);
  }

  @Override
  public Long getFileSize(
      ArtifactoryConfigRequest artifactoryConfig, Map<String, String> metadata, String artifactPathMetadataKey) {
    return artifactoryClient.getFileSize(artifactoryConfig, metadata, artifactPathMetadataKey);
  }
}
