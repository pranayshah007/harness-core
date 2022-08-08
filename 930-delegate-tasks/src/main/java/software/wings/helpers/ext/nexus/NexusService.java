/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.nexus;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.nexus.NexusRequest;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.helpers.ext.jenkins.BuildDetails;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Created by srinivas on 3/28/17.
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._960_API_SERVICES)
public interface NexusService {
  /**
   * Get Artifact paths under repository
   *
   * @param repoId
   * @return List&lt;String&gt; artifact paths
   */
  List<String> getArtifactPaths(NexusRequest nexusConfig, String repoId);

  /**
   * Get Artifact paths for a given repo from the given relative path
   *
   * @param repoId
   * @return List&lt;String&gt; artifact paths
   */
  List<String> getArtifactPaths(NexusRequest nexusConfig, String repoId, String name);

  Pair<String, InputStream> downloadArtifacts(NexusRequest nexusConfig,
      ArtifactStreamAttributes artifactStreamAttributes, Map<String, String> metadata, String delegateId, String taskId,
      String accountId, ListNotifyResponseData res);

  /***
   * Get GroupId paths
   * @param nexusConfig
   * @param repoId
   * @return
   */
  List<String> getGroupIdPaths(NexusRequest nexusConfig, String repoId, String repositoryFormat);

  /***
   *
   * @param nexusConfig
   * @param repoId the repoId
   * @param path the path
   * @return
   */
  List<String> getArtifactNames(NexusRequest nexusConfig, String repoId, String path);

  /***
   *
   * @param nexusConfig
   * @param repoId the repoId
   * @param path the path
   * @param repositoryFormat the repositoryFormat
   * @return
   */
  List<String> getArtifactNames(NexusRequest nexusConfig, String repoId, String path, String repositoryFormat);

  /**
   * @param nexusConfig
   * @param repoId       Repository Type
   * @param groupId      Group Id
   * @param artifactName artifact name
   * @param extension    extension
   * @param classifier   classifier
   * @return list of versions
   */
  List<BuildDetails> getVersions(NexusRequest nexusConfig, String repoId, String groupId, String artifactName,
      String extension, String classifier);

  List<BuildDetails> getVersions(String repositoryFormat, NexusRequest nexusConfig, String repoId, String packageName,
      Set<String> collectedBuilds);

  @SuppressWarnings("squid:S00107")
  List<BuildDetails> getVersion(NexusRequest nexusConfig, String repoId, String groupId, String artifactName,
      String extension, String classifier, String buildNo);

  List<BuildDetails> getVersion(
      String repositoryFormat, NexusRequest nexusConfig, String repoId, String packageName, String buildNo);

  /**
   * Gets the latest version of the given artifact
   *
   * @param nexusConfig
   * @param repoId
   * @param groupId
   * @param artifactName
   * @return
   */
  BuildDetails getLatestVersion(NexusRequest nexusConfig, String repoId, String groupId, String artifactName);

  /**
   * @param nexusConfig
   * @param artifactStreamAttributes
   * @param maxNumberOfBuilds
   * @return
   */
  List<BuildDetails> getBuilds(
      NexusRequest nexusConfig, ArtifactStreamAttributes artifactStreamAttributes, int maxNumberOfBuilds);

  /**
   * @param nexusConfig
   * @param repoId       Repository Type
   * @param groupId      Group Id
   * @param artifactName artifact name
   * @param extension    extension
   * @param classifier   classifier
   * @return true if versions exist
   */
  boolean existsVersion(NexusRequest nexusConfig, String repoId, String groupId, String artifactName, String extension,
      String classifier);

  Pair<String, InputStream> downloadArtifactByUrl(NexusRequest nexusConfig, String artifactName, String artifactUrl);

  long getFileSize(NexusRequest nexusConfig, String artifactName, String artifactUrl);
}
