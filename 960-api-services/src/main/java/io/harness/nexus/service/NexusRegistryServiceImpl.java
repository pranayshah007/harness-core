/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.nexus.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorAscending;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.NexusRegistryException;
import io.harness.expression.RegexFunctor;
import io.harness.nexus.NexusClientImpl;
import io.harness.nexus.NexusRequest;

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
public class NexusRegistryServiceImpl implements NexusRegistryService {
  private static final int MAX_NUMBER_OF_BUILDS = 250;
  @Inject NexusClientImpl nexusClient;

  @Override
  public List<BuildDetailsInternal> getBuilds(NexusRequest nexusConfig, String repositoryName, String port,
      String artifactName, String repositoryFormat, int maxNumberOfBuilds) {
    if (RepositoryFormat.docker.name().equalsIgnoreCase(repositoryFormat)) {
      List<BuildDetailsInternal> buildDetails;
      buildDetails =
          nexusClient.getArtifactsVersions(nexusConfig, repositoryName, port, artifactName, repositoryFormat);
      buildDetails.sort(new BuildDetailsInternalComparatorAscending());
      return buildDetails;
    }

    throw NestedExceptionUtils.hintWithExplanationException("Please check your artifact YAML configuration.",
        String.format("repositoryFormat [%s] is an invalid value.", repositoryFormat),
        new NexusRegistryException(
            "Invalid value for repositoryFormat field. Currently only 'docker' repository format is supported."));
  }

  private List<BuildDetailsInternal> getBuildDetails(NexusRequest nexusConfig, String repository, String port,
      String artifactName, String repositoryFormat, String tag) {
    if (RepositoryFormat.docker.name().equalsIgnoreCase(repositoryFormat)) {
      List<BuildDetailsInternal> buildDetails;
      buildDetails = nexusClient.getBuildDetails(nexusConfig, repository, port, artifactName, repositoryFormat, tag);
      buildDetails.sort(new BuildDetailsInternalComparatorAscending());
      return buildDetails;
    }

    throw NestedExceptionUtils.hintWithExplanationException("Please check your artifact YAML configuration.",
        String.format("repositoryFormat [%s] is an invalid value.", repositoryFormat),
        new NexusRegistryException(
            "Invalid value for repositoryFormat field. Currently only 'docker' repository format is supported."));
  }

  @Override
  public BuildDetailsInternal getLastSuccessfulBuildFromRegex(NexusRequest nexusConfig, String repository, String port,
      String artifactName, String repositoryFormat, String tagRegex) {
    try {
      Pattern.compile(tagRegex);
    } catch (PatternSyntaxException e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check tagRegex field in Nexus artifact configuration.",
          String.format("TagRegex field contains an invalid regex value '%s'.", tagRegex),
          new NexusRegistryException(e.getMessage()));
    }

    List<BuildDetailsInternal> builds =
        getBuilds(nexusConfig, repository, port, artifactName, repositoryFormat, MAX_NUMBER_OF_BUILDS);
    builds = builds.stream()
                 .filter(build -> new RegexFunctor().match(tagRegex, build.getNumber()))
                 .sorted(new BuildDetailsInternalComparatorDescending())
                 .collect(Collectors.toList());

    if (builds.isEmpty()) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check tagRegex field in Nexus artifact configuration.",
          String.format(
              "Could not find any tags that match regex '%s' for Nexus repository [%s] for %s artifact [%s] in registry [%s].",
              tagRegex, repository, repositoryFormat, artifactName, nexusConfig.getNexusUrl()),
          new NexusRegistryException(
              String.format("Could not find an artifact tag that matches tagRegex '%s'", tagRegex)));
    }
    return builds.get(0);
  }

  @Override
  public BuildDetailsInternal verifyBuildNumber(NexusRequest nexusConfig, String repository, String port,
      String artifactName, String repositoryFormat, String tag) {
    return getBuildNumber(nexusConfig, repository, port, artifactName, repositoryFormat, tag);
  }

  @Override
  public boolean validateCredentials(NexusRequest nexusConfig) {
    return nexusClient.isRunning(nexusConfig);
  }

  private BuildDetailsInternal getBuildNumber(NexusRequest nexusConfig, String repository, String port,
      String artifactName, String repositoryFormat, String tag) {
    List<BuildDetailsInternal> builds =
        getBuildDetails(nexusConfig, repository, port, artifactName, repositoryFormat, tag);
    builds = builds.stream().filter(build -> build.getNumber().equals(tag)).collect(Collectors.toList());

    if (builds.size() == 1) {
      return builds.get(0);
    }

    throw NestedExceptionUtils.hintWithExplanationException(
        "Please check your Nexus repository for artifacts with same tag.",
        String.format(
            "Found multiple artifacts for tag [%s] in Nexus repository [%s] for %s artifact [%s] in registry [%s].",
            tag, repository, repositoryFormat, artifactName, nexusConfig.getNexusUrl()),
        new NexusRegistryException(String.format("Found multiple artifact tags ('%s'), but expected only one.", tag)));
  }
}
