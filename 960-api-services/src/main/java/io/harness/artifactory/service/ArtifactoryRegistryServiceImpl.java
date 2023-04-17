/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifactory.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.artifactory.ArtifactoryClientImpl.getBaseUrl;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifact.ArtifactUtilities;
import io.harness.artifactory.ArtifactoryClientImpl;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.artifacts.gar.service.GARUtils;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ArtifactoryRegistryException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;

import software.wings.utils.RepositoryFormat;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class ArtifactoryRegistryServiceImpl implements ArtifactoryRegistryService {
  @Inject ArtifactoryClientImpl artifactoryClient;

  private static final String COULD_NOT_FETCH_IMAGE_MANIFEST = "Could not fetch image manifest";
  private static final String ARTIFACT_EXISTENCE_MESSAGE =
      "Please check your Artifactory repository for artifact existence";
  private static final String YAML_CONFIGURATION_MESSAGE = "Please check your artifact YAML configuration.";
  private static final String DOCKER_FORMAT_IS_SUPPORTED =
      "Invalid value for RepositoryFormat field. Currently only 'docker' repository format is supported.";
  private static final String REPOSITORY_FORMAT_INVALID_VALUE = "RepositoryFormat [%s] is an invalid value.";

  @Override
  public List<BuildDetailsInternal> getBuilds(
      ArtifactoryConfigRequest artifactoryConfig, String repositoryName, String artifactName, String repositoryFormat) {
    if (RepositoryFormat.docker.name().equals(repositoryFormat)) {
      return artifactoryClient.getArtifactsDetails(artifactoryConfig, repositoryName, artifactName, repositoryFormat);
    }
    throw NestedExceptionUtils.hintWithExplanationException(YAML_CONFIGURATION_MESSAGE,
        String.format(REPOSITORY_FORMAT_INVALID_VALUE, repositoryFormat),
        new ArtifactoryRegistryException(DOCKER_FORMAT_IS_SUPPORTED));
  }

  @Override
  public BuildDetailsInternal getLastSuccessfulBuildFromRegex(ArtifactoryConfigRequest artifactoryConfig,
      String repositoryName, String artifactName, String repositoryFormat, String tagRegex) {
    List<BuildDetailsInternal> builds = getBuilds(artifactoryConfig, repositoryName, artifactName, repositoryFormat);

    Pattern pattern = Pattern.compile(tagRegex.replace(".", "\\.").replace("?", ".?").replace("*", ".*?"));

    if (EmptyPredicate.isEmpty(builds)) {
      throw new InvalidRequestException("There are no builds for this repositoryName: [" + repositoryName
          + "], artifactName: [" + artifactName + "], and tagRegex: [" + tagRegex + "].");
    }

    List<BuildDetailsInternal> buildsResponse =
        builds.stream()
            .filter(build -> !build.getNumber().endsWith("/") && pattern.matcher(build.getNumber()).find())
            .sorted(new BuildDetailsInternalComparatorDescending())
            .collect(Collectors.toList());

    if (buildsResponse.isEmpty()) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check tagRegex field in Artifactory artifact configuration.",
          String.format(
              "Could not find any tags that match regex [%s] for Artifactory repository [%s] for %s artifact [%s] in registry [%s].",
              tagRegex, repositoryName, repositoryFormat, artifactName, artifactoryConfig.getArtifactoryUrl()),
          new ArtifactoryRegistryException(
              String.format("Could not find an artifact tag that matches tagRegex '%s'", tagRegex)));
    }

    ArtifactMetaInfo artifactMetaInfo = ArtifactMetaInfo.builder().build();
    try {
      artifactMetaInfo = artifactoryClient.getArtifactMetaInfo(
          artifactoryConfig, artifactName, repositoryName, buildsResponse.get(0).getNumber());
    } catch (Exception e) {
      log.error(COULD_NOT_FETCH_IMAGE_MANIFEST, e);
    }
    return constructBuildDetailsInternal(
        repositoryName, artifactName, artifactoryConfig, buildsResponse.get(0).getNumber(), artifactMetaInfo);
  }

  @Override
  public BuildDetailsInternal verifyBuildNumber(ArtifactoryConfigRequest artifactoryConfig, String repositoryName,
      String artifactName, String repoFormat, String tag) {
    return getBuildNumber(artifactoryConfig, repositoryName, artifactName, repoFormat, tag);
  }

  private BuildDetailsInternal getBuildNumber(ArtifactoryConfigRequest artifactoryConfig, String repository,
      String artifactName, String repositoryFormat, String tag) {
    ArtifactMetaInfo artifactMetaInfo = ArtifactMetaInfo.builder().build();
    try {
      artifactMetaInfo = artifactoryClient.getArtifactMetaInfo(artifactoryConfig, artifactName, repository, tag);
    } catch (Exception e) {
      log.error(COULD_NOT_FETCH_IMAGE_MANIFEST, e);
    }

    if (EmptyPredicate.isEmpty(artifactMetaInfo.getShaV2()) && EmptyPredicate.isEmpty(artifactMetaInfo.getSha())) {
      List<BuildDetailsInternal> builds = getBuilds(artifactoryConfig, repository, artifactName, repositoryFormat);
      builds = builds.stream().filter(build -> build.getNumber().equals(tag)).collect(Collectors.toList());
      if (builds.size() == 0) {
        throw NestedExceptionUtils.hintWithExplanationException(ARTIFACT_EXISTENCE_MESSAGE,
            String.format(
                "Did not find any artifacts for tag [%s] in Artifactory repository [%s] for %s artifact [%s] in registry [%s].",
                tag, repository, repositoryFormat, artifactName, artifactoryConfig.getArtifactoryUrl()),
            new ArtifactoryRegistryException(String.format("Artifact tag '%s' not found.", tag)));
      } else if (builds.size() == 1) {
        return builds.get(0);
      }

      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check your Artifactory repository for artifacts with same tag.",
          String.format(
              "Found multiple artifacts for tag [%s] in Artifactory repository [%s] for %s artifact [%s] in registry [%s].",
              tag, repository, repositoryFormat, artifactName, artifactoryConfig.getArtifactoryUrl()),
          new ArtifactoryRegistryException(
              String.format("Found multiple artifact tags '%s', but expected only one.", tag)));
    }

    return constructBuildDetailsInternal(repository, artifactName, artifactoryConfig, tag, artifactMetaInfo);
  }

  private BuildDetailsInternal constructBuildDetailsInternal(String repository, String artifactName,
      ArtifactoryConfigRequest artifactoryConfig, String tag, ArtifactMetaInfo artifactMetaInfo) {
    String repositoryKey = ArtifactUtilities.trimSlashforwardChars(repository);
    String artifactPath = ArtifactUtilities.trimSlashforwardChars(artifactName);
    String repoName = ArtifactUtilities.getArtifactoryRepositoryName(artifactoryConfig.getArtifactoryUrl(),
        ArtifactUtilities.trimSlashforwardChars(artifactoryConfig.getArtifactRepositoryUrl()), repositoryKey,
        artifactPath);
    String tagUrl = getBaseUrl(artifactoryConfig) + repositoryKey + "/" + artifactPath + "/";
    String registryHostname = ArtifactUtilities.extractRegistryHost(repoName);
    Map<String, String> metadata = new HashMap();
    metadata.put(ArtifactMetadataKeys.IMAGE, GARUtils.getImageName(repoName, tag));
    metadata.put(ArtifactMetadataKeys.TAG, tag);
    metadata.put(ArtifactMetadataKeys.REGISTRY_HOSTNAME, registryHostname);
    return BuildDetailsInternal.builder()
        .number(tag)
        .buildUrl(tagUrl + tag)
        .metadata(metadata)
        .uiDisplayName("Tag# " + tag)
        .artifactMetaInfo(artifactMetaInfo)
        .build();
  }

  @Override
  public boolean validateCredentials(ArtifactoryConfigRequest artifactoryConfig) {
    return artifactoryClient.validateArtifactServer(artifactoryConfig);
  }

  @Override
  public List<Map<String, String>> getLabels(
      ArtifactoryConfigRequest artifactoryConfig, String imageName, String repositoryName, String buildNos) {
    // Calling getLabels implemented from cg side.

    try {
      return artifactoryClient.getLabels(artifactoryConfig, imageName, repositoryName, buildNos);

    } catch (Exception e) {
      // Catching this exception because artifactory repository api is returning 404 for some open source images

      log.error("Error occurred while fetching artifactory labels", e);

      return Collections.emptyList();
    }
  }
}
