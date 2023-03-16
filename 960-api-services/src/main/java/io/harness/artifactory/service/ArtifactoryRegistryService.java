/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifactory.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifacts.beans.BuildDetailsInternal;

import java.util.List;
import java.util.Map;

@OwnedBy(CDP)
public interface ArtifactoryRegistryService {
  int MAX_NO_OF_TAGS_PER_ARTIFACT = 10000;
  String DEFAULT_ARTIFACT_FILTER = "*";
  String DEFAULT_ARTIFACT_DIRECTORY = "/";

  /**
   * Gets builds.
   *
   * @param artifactoryConfig the artifactory config
   * @param repositoryName    the repository name
   * @param artifactName      the artifact name
   * @param repoFormat        the repositroy format (docker,....)
   * @return the builds
   */
  List<BuildDetailsInternal> getBuilds(
      ArtifactoryConfigRequest artifactoryConfig, String repositoryName, String artifactName, String repoFormat);

  /**
   * Gets last successful build.
   *
   * @param artifactoryConfig the artifactory config
   * @param repositoryName    the repository name
   * @param artifactName      the artifact name
   * @param repoFormat        the repositroy format (docker,....)
   * @param tagRegex          the regular expression of tag value
   * @return the last successful build
   */
  BuildDetailsInternal getLastSuccessfulBuildFromRegex(ArtifactoryConfigRequest artifactoryConfig,
      String repositoryName, String artifactName, String repoFormat, String tagRegex);
  /**
   * Validates the Artifact Tag
   * @param artifactoryConfig       the artifactory config
   * @param repositoryName          the repository name
   * @param artifactName               the artifact name
   * @param repoFormat              the repositroy format (docker,....)
   * @param tag                     the repositroy iamge tag value
   */
  BuildDetailsInternal verifyBuildNumber(ArtifactoryConfigRequest artifactoryConfig, String repositoryName,
      String artifactName, String repoFormat, String tag);

  /**
   * Validate the credentials
   *
   * @param toArtifactoryInternalConfig the artifactory config
   * @return boolean validate
   */
  boolean validateCredentials(ArtifactoryConfigRequest toArtifactoryInternalConfig);

  /**
   * Fetching labels for docker artifactory
   *
   * @param artifactoryConfig the artifactory config
   * @param imageName the image name of docker
   * @param repositoryName the repository name
   * @return list of paired labels.

   */

  List<Map<String, String>> getLabels(
      ArtifactoryConfigRequest artifactoryConfig, String imageName, String repositoryName, String buildNos);
}
