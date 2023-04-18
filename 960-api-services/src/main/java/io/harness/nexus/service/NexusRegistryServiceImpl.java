/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.nexus.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifact.ArtifactUtilities;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.artifacts.gar.service.GARUtils;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.NexusRegistryException;
import io.harness.expression.RegexFunctor;
import io.harness.nexus.NexusClientImpl;
import io.harness.nexus.NexusHelper;
import io.harness.nexus.NexusRequest;

import software.wings.utils.RepositoryFormat;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class NexusRegistryServiceImpl implements NexusRegistryService {
  @Inject NexusClientImpl nexusClient;

  private static final String COULD_NOT_FETCH_IMAGE_MANIFEST = "Could not fetch image manifest";

  @Override
  public List<BuildDetailsInternal> getBuilds(NexusRequest nexusConfig, String repositoryName, String port,
      String artifactName, String repositoryFormat, String groupId, String artifactId, String extension,
      String classifier, String packageName, String group, int maxBuilds) {
    List<BuildDetailsInternal> buildDetails;
    if (RepositoryFormat.docker.name().equalsIgnoreCase(repositoryFormat)) {
      buildDetails = nexusClient.getDockerArtifactVersions(
          nexusConfig, repositoryName, port, artifactName, repositoryFormat, maxBuilds);
      return buildDetails;
    } else if (RepositoryFormat.maven.name().equalsIgnoreCase(repositoryFormat)) {
      buildDetails = nexusClient.getArtifactsVersions(
          nexusConfig, repositoryName, groupId, artifactId, extension, classifier, maxBuilds);
      return buildDetails;
    } else if (RepositoryFormat.npm.name().equalsIgnoreCase(repositoryFormat)
        || RepositoryFormat.nuget.name().equalsIgnoreCase(repositoryFormat)) {
      buildDetails = nexusClient.getArtifactsVersions(nexusConfig, repositoryFormat, repositoryName, packageName);
      return buildDetails;
    } else if (RepositoryFormat.raw.name().equalsIgnoreCase(repositoryFormat)) {
      buildDetails = nexusClient.getPackageNames(nexusConfig, repositoryName, group);
      return buildDetails;
    } else {
      throw NestedExceptionUtils.hintWithExplanationException("Please check your artifact YAML configuration.",
          String.format("repositoryFormat [%s] is an invalid value.", repositoryFormat),
          new NexusRegistryException(
              "Invalid value for repositoryFormat field. Currently only 'docker' repository format is supported."));
    }
  }

  private List<BuildDetailsInternal> getBuildDetails(NexusRequest nexusConfig, String repository, String port,
      String artifactName, String repositoryFormat, String tag) {
    if (RepositoryFormat.docker.name().equalsIgnoreCase(repositoryFormat)) {
      List<BuildDetailsInternal> buildDetails;
      buildDetails = nexusClient.getBuildDetails(nexusConfig, repository, port, artifactName, repositoryFormat, tag);
      return buildDetails;
    }
    throw NestedExceptionUtils.hintWithExplanationException("Please check your artifact YAML configuration.",
        String.format("repositoryFormat [%s] is an invalid value.", repositoryFormat),
        new NexusRegistryException(
            "Invalid value for repositoryFormat field. Currently only 'docker' repository format is supported."));
  }

  @Override
  public BuildDetailsInternal getLastSuccessfulBuildFromRegex(NexusRequest nexusConfig, String repository, String port,
      String artifactName, String repositoryFormat, String tagRegex, String groupId, String artifactId,
      String extension, String classifier, String packageName, String group, int maxBuilds) {
    try {
      Pattern.compile(tagRegex);
    } catch (PatternSyntaxException e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check tagRegex field in Nexus artifact configuration.",
          String.format("TagRegex field contains an invalid regex value '%s'.", tagRegex),
          new NexusRegistryException(e.getMessage()));
    }

    List<BuildDetailsInternal> builds = getBuilds(nexusConfig, repository, port, artifactName, repositoryFormat,
        groupId, artifactId, extension, classifier, packageName, group, maxBuilds);
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
    if (RepositoryFormat.docker.name().equals(repositoryFormat)) {
      return verifyBuildNumber(nexusConfig, repository, port, artifactName, repositoryFormat, builds.get(0).getNumber(),
          groupId, artifactId, extension, classifier, packageName, group, maxBuilds);
    }
    return builds.get(0);
  }

  @Override
  public BuildDetailsInternal verifyBuildNumber(NexusRequest nexusConfig, String repository, String port,
      String artifactName, String repositoryFormat, String tag, String groupId, String artifactId, String extension,
      String classifier, String packageName, String group, int maxBuilds) {
    if (RepositoryFormat.docker.name().equalsIgnoreCase(repositoryFormat)) {
      ArtifactMetaInfo artifactMetaInfo = ArtifactMetaInfo.builder().build();
      try {
        ArtifactMetaInfo artifactMetaInfoSchemaVersion1 =
            nexusClient.getArtifactMetaInfo(nexusConfig, repository, artifactName, tag, true);
        if (artifactMetaInfoSchemaVersion1 != null) {
          artifactMetaInfo.setSha(artifactMetaInfoSchemaVersion1.getSha());
          artifactMetaInfo.setLabels(artifactMetaInfoSchemaVersion1.getLabels());
        }
      } catch (Exception e) {
        log.error(COULD_NOT_FETCH_IMAGE_MANIFEST, e);
      }
      try {
        ArtifactMetaInfo artifactMetaInfoSchemaVersion2 =
            nexusClient.getArtifactMetaInfo(nexusConfig, repository, artifactName, tag, false);
        if (artifactMetaInfoSchemaVersion2 != null) {
          artifactMetaInfo.setShaV2(artifactMetaInfoSchemaVersion2.getSha());
        }
      } catch (Exception e) {
        log.error(COULD_NOT_FETCH_IMAGE_MANIFEST, e);
      }

      if (EmptyPredicate.isEmpty(artifactMetaInfo.getSha()) && EmptyPredicate.isEmpty(artifactMetaInfo.getShaV2())) {
        return getBuildNumber(nexusConfig, repository, port, artifactName, repositoryFormat, tag);
      }
      String repoName = ArtifactUtilities.getNexusRepositoryNameNG(
          nexusConfig.getNexusUrl(), port, nexusConfig.getArtifactRepositoryUrl(), artifactName);
      String registryHostname = ArtifactUtilities.extractRegistryHost(repoName);
      Map<String, String> metadata = new HashMap<>();
      metadata.put(ArtifactMetadataKeys.IMAGE, getImage(repoName, tag));
      metadata.put(ArtifactMetadataKeys.TAG, tag);
      metadata.put(ArtifactMetadataKeys.REGISTRY_HOSTNAME, registryHostname);
      String baseUrl = NexusHelper.getBaseUrl(nexusConfig);
      String buildUrl = String.format("%srepository/%s/v2/%s/manifests/%s", baseUrl, repository, artifactName, tag);
      String artifactPath = String.format("v2/%s/manifests/%s", artifactName, tag);
      return BuildDetailsInternal.builder()
          .number(tag)
          .metadata(metadata)
          .artifactMetaInfo(artifactMetaInfo)
          .artifactPath(artifactPath)
          .buildUrl(buildUrl)
          .build();
    } else {
      return getBuildNumber(nexusConfig, repository, port, artifactName, repositoryFormat, tag, groupId, artifactId,
          extension, classifier, packageName, group, maxBuilds);
    }
  }

  private String getImage(String repoName, String tag) {
    if (GARUtils.isSHA(tag)) {
      return String.format("%s@%s", repoName, tag);
    }
    return String.format("%s:%s", repoName, tag);
  }

  @Override
  public boolean validateCredentials(NexusRequest nexusConfig) {
    return nexusClient.isRunning(nexusConfig);
  }

  @Override
  public Map<String, String> getRepository(NexusRequest nexusConfig, String repositoryFormat) {
    return nexusClient.getRepositories(nexusConfig, repositoryFormat);
  }

  private BuildDetailsInternal getBuildNumber(NexusRequest nexusConfig, String repository, String port,
      String artifactName, String repositoryFormat, String tag, String groupId, String artifactId, String extension,
      String classifier, String packageName, String group, int maxBuilds) {
    List<BuildDetailsInternal> builds = getBuilds(nexusConfig, repository, port, artifactName, repositoryFormat,
        groupId, artifactId, extension, classifier, packageName, group, maxBuilds);
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
