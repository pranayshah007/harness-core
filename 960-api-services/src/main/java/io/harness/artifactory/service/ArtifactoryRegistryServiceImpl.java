/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifactory.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryClientImpl;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.exception.ArtifactoryRegistryException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.expression.RegexFunctor;

import software.wings.utils.RepositoryFormat;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class ArtifactoryRegistryServiceImpl implements ArtifactoryRegistryService {
  @Inject ArtifactoryClientImpl artifactoryClient;

  @Override
  public List<BuildDetailsInternal> getBuilds(ArtifactoryConfigRequest artifactoryConfig, String repositoryName,
      String artifactName, String repositoryFormat, int maxNumberOfBuilds) {
    if (RepositoryFormat.docker.name().equals(repositoryFormat)) {
      return artifactoryClient.getArtifactsDetails(
          artifactoryConfig, repositoryName, artifactName, repositoryFormat, maxNumberOfBuilds);
    }
    throw NestedExceptionUtils.hintWithExplanationException("Please check your artifact YAML configuration.",
        String.format("RepositoryFormat [%s] is an invalid value.", repositoryFormat),
        new ArtifactoryRegistryException(
            "Invalid value for RepositoryFormat field. Currently only 'docker' repository format is supported."));
  }

  @Override
  public BuildDetailsInternal getLastSuccessfulBuildFromRegex(ArtifactoryConfigRequest artifactoryConfig,
      String repositoryName, String artifactName, String repositoryFormat, String tagRegex) {
    try {
      Pattern.compile(tagRegex);
    } catch (PatternSyntaxException e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check tagRegex field in Artifactory artifact configuration.",
          String.format("TagRegex field contains an invalid regex value '%s'.", tagRegex),
          new ArtifactoryRegistryException(e.getMessage()));
    }

    List<BuildDetailsInternal> builds =
        getBuilds(artifactoryConfig, repositoryName, artifactName, repositoryFormat, MAX_NO_OF_TAGS_PER_ARTIFACT);
    builds = builds.stream()
                 .filter(build -> new RegexFunctor().match(tagRegex, build.getNumber()))
                 .sorted(new BuildDetailsInternalComparatorDescending())
                 .collect(Collectors.toList());

    if (builds.isEmpty()) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check tagRegex field in Artifactory artifact configuration.",
          String.format(
              "Could not find any tags that match regex [%s] for Artifactory repository [%s] for %s artifact [%s] in registry [%s].",
              tagRegex, repositoryName, repositoryFormat, artifactName, artifactoryConfig.getArtifactoryUrl()),
          new ArtifactoryRegistryException(
              String.format("Could not find an artifact tag that matches tagRegex '%s'", tagRegex)));
    }
    return builds.get(0);
  }

  @Override
  public BuildDetailsInternal verifyBuildNumber(ArtifactoryConfigRequest artifactoryConfig, String repositoryName,
      String artifactName, String repoFormat, String tag) {
    return getBuildNumber(artifactoryConfig, repositoryName, artifactName, repoFormat, tag);
  }

  private BuildDetailsInternal getBuildNumber(ArtifactoryConfigRequest artifactoryConfig, String repository,
      String artifactName, String repositoryFormat, String tag) {
    List<BuildDetailsInternal> builds =
        getBuilds(artifactoryConfig, repository, artifactName, repositoryFormat, MAX_NO_OF_TAGS_PER_ARTIFACT);
    builds = builds.stream().filter(build -> build.getNumber().equals(tag)).collect(Collectors.toList());

    if (builds.size() == 0) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check your Artifactory repository for artifact tag existence.",
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

  @Override
  public boolean validateCredentials(ArtifactoryConfigRequest artifactoryConfig) {
    return artifactoryClient.validateArtifactServer(artifactoryConfig);
  }
}
