/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifactory;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(HarnessTeam.CDP)
public interface ArtifactoryNgService {
  List<BuildDetails> getBuildDetails(
      ArtifactoryConfigRequest artifactoryConfig, String repositoryName, String artifactPath, int maxVersions);

  List<BuildDetails> getArtifactList(ArtifactoryConfigRequest artifactoryConfig, String repositoryName,
      String artifactPath, int maxVersions, String artifactPathFilter, String artifactDirectory);

  BuildDetails getLatestArtifact(ArtifactoryConfigRequest artifactoryConfig, String repositoryName,
      String ArtifactPathDirectory, String artifactPathFilter, String artifactPath, int maxVersions);

  Map<String, String> getRepositories(ArtifactoryConfigRequest artifactoryConfig, String packageType);

  List<ArtifactoryImagePath> getImagePaths(ArtifactoryConfigRequest artifactoryConfig, String repoKey);

  InputStream downloadArtifacts(ArtifactoryConfigRequest artifactoryConfig, String repoKey,
      Map<String, String> metadata, String artifactPathMetadataKey, String artifactFileNameMetadataKey);

  Long getFileSize(
      ArtifactoryConfigRequest artifactoryConfig, Map<String, String> metadata, String artifactPathMetadataKey);
}