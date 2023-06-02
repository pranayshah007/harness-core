/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.nexus.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.nexus.NexusRequest;

import java.util.List;
import java.util.Map;

@OwnedBy(CDP)
public interface NexusRegistryService {
  int MAX_NO_OF_TAGS_PER_ARTIFACT = 10000;

  /**
   * Gets builds.
   *
   * @param nexusConfig  the nexus config
   * @param artifactName the artifact name
   * @param maxBuilds
   * @return the builds
   */
  List<BuildDetailsInternal> getBuilds(NexusRequest nexusConfig, String repositoryName, String port,
      String artifactName, String repoFormat, String groupId, String artifactId, String extension, String classifier,
      String packageName, String group, int maxBuilds);

  /**
   * Gets the last successful build with input as tag regex.
   *
   * @param nexusConfig  the nexus config
   * @param artifactName the artifact name
   * @param tagRegex     tag regex
   * @param maxBuilds
   * @return the last successful build
   */
  BuildDetailsInternal getLastSuccessfulBuildFromRegex(NexusRequest nexusConfig, String repository, String port,
      String artifactName, String repositoryFormat, String tagRegex, String groupId, String artifactId,
      String extension, String classifier, String packageName, String group, int maxBuilds);

  /**
   * Validates the Artifact Tag
   *
   * @param nexusConfig  the nexus config
   * @param artifactName the artifact name
   * @param maxBuilds
   */
  BuildDetailsInternal verifyBuildNumber(NexusRequest nexusConfig, String repository, String port, String artifactName,
      String repositoryFormat, String tag, String groupId, String artifactId, String extension, String classifier,
      String packageName, String group, int maxBuilds);

  /**
   * Validate the credentials
   *
   * @param nexusConfig the nexus config
   * @return boolean validate
   */
  boolean validateCredentials(NexusRequest nexusConfig);
  Map<String, String> getRepository(NexusRequest nexusConfig, String repositoryFormat);
}
