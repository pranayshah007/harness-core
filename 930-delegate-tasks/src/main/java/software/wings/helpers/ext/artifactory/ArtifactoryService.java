/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.artifactory;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.delegate.task.ListNotifyResponseData;

import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.utils.RepositoryType;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Created by sgurubelli on 6/27/17.
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._960_API_SERVICES)
@BreakDependencyOn("software.wings.beans.artifact.ArtifactStreamAttributes")
@BreakDependencyOn("io.harness.delegate.task.ListNotifyResponseData")
public interface ArtifactoryService {
  List<BuildDetails> getBuilds(ArtifactoryConfigRequest artifactoryConfig,
      ArtifactStreamAttributes artifactStreamAttributes, int maxNumberOfBuilds);

  /**
   * @param artifactoryConfig
   * @param repositoryName
   * @param artifactPath
   * @param repositoryType
   * @param maxVersions
   * @return
   */
  List<BuildDetails> getFilePaths(ArtifactoryConfigRequest artifactoryConfig, String repositoryName,
      String artifactPath, String repositoryType, int maxVersions);

  /**
   * Get Repositories
   *
   * @param artifactoryConfig
   * @return map RepoId and Name
   */
  Map<String, String> getRepositories(ArtifactoryConfigRequest artifactoryConfig);

  /**
   * Get Repositories
   *
   * @return map RepoId and Name
   */
  Map<String, String> getRepositories(ArtifactoryConfigRequest artifactoryConfig, String packageType);

  Map<String, String> getRepositories(ArtifactoryConfigRequest artifactoryConfig, RepositoryType repositoryType);

  /***
   * Get docker tags
   * @param artifactoryConfig the Artifactory Config
   * @param repoKey
   * @return List of Repo paths or docker images
   */
  List<String> getRepoPaths(ArtifactoryConfigRequest artifactoryConfig, String repoKey);

  ListNotifyResponseData downloadArtifacts(ArtifactoryConfigRequest artifactoryConfig, String repoType,
      Map<String, String> metadata, String delegateId, String taskId, String accountId);

  Pair<String, InputStream> downloadArtifact(
      ArtifactoryConfigRequest artifactoryConfig, String repositoryName, Map<String, String> metadata);

  boolean validateArtifactPath(
      ArtifactoryConfigRequest artifactoryConfig, String repoType, String artifactPath, String repositoryType);

  Long getFileSize(ArtifactoryConfigRequest artifactoryConfig, Map<String, String> metadata);

  List<HelmChart> getHelmCharts(ArtifactoryConfigRequest artifactoryConfig, String repositoryName, String chartName,
      int maxVersions, String version, boolean isRegex);
}
