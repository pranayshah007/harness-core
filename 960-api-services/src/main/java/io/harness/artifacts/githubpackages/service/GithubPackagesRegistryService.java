/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.githubpackages.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.githubpackages.beans.GithubPackagesInternalConfig;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDC)
public interface GithubPackagesRegistryService {
  /**
   * Get builds
   */
  List<BuildDetails> getBuilds(GithubPackagesInternalConfig githubPackagesInternalConfig, String packageName,
      String packageType, String org, String versionRegex);

  /**
   * Get last successful build
   */
  BuildDetails getLastSuccessfulBuildFromRegex(GithubPackagesInternalConfig toGithubPackagesInternalConfig,
      String packageName, String packageType, String versionRegex, String org);

  /**
   * Get build
   */
  BuildDetails getBuild(GithubPackagesInternalConfig toGithubPackagesInternalConfig, String packageName,
      String packageType, String version, String org);

  List<Map<String, String>> listPackages(
      GithubPackagesInternalConfig githubPackagesInternalConfig, String packageType, String org);

  String fetchDownloadUrl(GithubPackagesInternalConfig githubPackagesInternalConfig, String packageType, String org,
      String artifactId, String user, String extension, String repository, String packageName, String version,
      String groupId);

  Pair<String, InputStream> downloadArtifactByUrl(
      GithubPackagesInternalConfig githubPackagesInternalConfig, String artifactName, String artifactUrl);
}
