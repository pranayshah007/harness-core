/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ArtifactMetaInfo;

import software.wings.beans.AzureContainerRegistry;
import software.wings.beans.AzureImageDefinition;
import software.wings.beans.AzureImageGallery;
import software.wings.beans.AzureResourceGroup;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureDevopsProject;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.persistence.artifact.Artifact;
import software.wings.utils.RepositoryFormat;
import software.wings.utils.RepositoryType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by anubhaw on 8/18/16.
 */
@OwnedBy(CDC)
@TargetModule(_870_CG_ORCHESTRATION)
public interface BuildSourceService {
  /**
   * Gets jobs.
   *
   * @param appId         the app id
   * @param settingId     the jenkins setting id
   * @param parentJobName the jenkins parent job name (if any)
   * @return the jobs
   */
  default Set<JobDetails> getJobs(@NotEmpty String appId, @NotEmpty String settingId, @Nullable String parentJobName) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets plans.
   *
   * @param appId              the app id
   * @param settingId          the setting id
   * @param artifactStreamType artifact stream type
   * @return the plans
   */
  default Map<String, String> getPlans(@NotEmpty String appId, @NotEmpty String settingId, String artifactStreamType) {
    throw new UnsupportedOperationException();
  }

  /**
   * Get project.
   *
   * @param appId     the app id
   * @param settingId the setting id
   * @return the project (now GCS only)
   */
  default String getProject(String appId, String settingId) {
    throw new UnsupportedOperationException();
  }

  /**
   * Get buckets.
   *
   * @param appId     the app id
   * @param projectId the project id
   * @param settingId the setting id
   * @return the project (now GCS only)
   */
  default Map<String, String> getBuckets(String appId, String projectId, String settingId) {
    throw new UnsupportedOperationException();
  }

  /**
   * Get SMB paths.
   *
   * @param appId     the app id
   * @param settingId the setting id
   * @return the paths (now SMB only)
   */
  default List<String> getSmbPaths(String appId, String settingId) {
    throw new UnsupportedOperationException();
  }

  /**
   * Get Artifact paths by stream id.
   *
   * @param appId      the app id
   * @param settingId  the setting id
   * @param streamType artifact stream type
   * @return the paths
   */
  default List<String> getArtifactPathsByStreamType(String appId, String settingId, String streamType) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets plans.
   *
   * @param appId              the app id
   * @param settingId          the setting id
   * @param artifactStreamType artifact stream type
   * @return the plans
   */
  default Map<String, String> getPlans(@NotEmpty String appId, @NotEmpty String settingId, @NotEmpty String serviceId,
      String artifactStreamType, String repositoryType) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets artifact paths.
   *
   * @param appId              the app id
   * @param jobName            the job name
   * @param settingId          the setting id
   * @param groupId            the group id
   * @param artifactStreamType artifact stream type
   * @return the artifact paths
   */
  default Set<String> getArtifactPaths(@NotEmpty String appId, @NotEmpty String jobName, @NotEmpty String settingId,
      String groupId, String artifactStreamType) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets artifact paths.
   *
   * @param appId              the app id
   * @param jobName            the job name
   * @param settingId          the setting id
   * @param groupId            the group id
   * @param artifactStreamType artifact stream type
   * @param repositoryFormat   repository format
   * @return the artifact paths
   */
  default Set<String> getArtifactPaths(@NotEmpty String appId, @NotEmpty String jobName, @NotEmpty String settingId,
      String groupId, String artifactStreamType, String repositoryFormat) {
    throw new UnsupportedOperationException();
  }

  default BuildDetails getBuild(
      String appId, String artifactStreamId, String settingId, Map<String, Object> runtimeValues) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets builds.
   *
   * @param appId            the app id
   * @param artifactStreamId the artifact source id
   * @param settingId        the setting id
   * @return the builds
   */
  default List<BuildDetails> getBuilds(String appId, String artifactStreamId, String settingId) {
    throw new UnsupportedOperationException();
  }

  /***
   * Gets builds with the limit
   * @param appId
   * @param artifactStreamId
   * @param settingId
   * @param limit
   * @return
   */
  default List<BuildDetails> getBuilds(String appId, String artifactStreamId, String settingId, int limit) {
    throw new UnsupportedOperationException();
  }

  default List<BuildDetails> getNewBuilds(String appId, String artifactStreamId, String settingId) {
    throw new UnsupportedOperationException();
  }

  default List<BuildDetails> getBuilds(String artifactStreamId, String settingId, int limit) {
    throw new UnsupportedOperationException();
  }

  default List<Map<String, String>> getLabels(ArtifactStream artifactStream, List<String> buildNos) {
    throw new UnsupportedOperationException();
  }

  default ArtifactMetaInfo getArtifactMetaInfo(ArtifactStream artifactStream, String buildNo) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets last successful build.
   *
   * @param appId            the app id
   * @param artifactStreamId the artifact stream id
   * @param settingId        the setting id
   * @return the last successful build
   */
  default BuildDetails getLastSuccessfulBuild(String appId, String artifactStreamId, String settingId) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets group Id paths.
   *
   * @param appId     the app id
   * @param jobName   the job name
   * @param settingId the setting id
   * @return the groupId paths
   */
  default Set<String> getGroupIds(@NotEmpty String appId, @NotEmpty String jobName, @NotEmpty String settingId) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets group Id paths.
   *
   * @param appId            the app id
   * @param jobName          the job name
   * @param settingId        the setting id
   * @param repositoryFormat the repositoryFormat
   * @return the groupId paths
   */
  default Set<String> getGroupIds(
      @NotEmpty String appId, @NotEmpty String jobName, @NotEmpty String settingId, @NotEmpty String repositoryFormat) {
    throw new UnsupportedOperationException();
  }

  default Set<String> fetchNexusPackageNames(@NotEmpty String appId, @NotEmpty String repositoryName,
      @NotEmpty String repositoryFormat, @NotEmpty String settingId) {
    throw new UnsupportedOperationException();
  }

  default Set<String> fetchNexusPackageNames(
      @NotEmpty String repositoryName, @NotEmpty String repositoryFormat, @NotEmpty String settingId) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets group Id paths.
   *
   * @param jobName   the job name
   * @param settingId the setting id
   * @return the groupId paths
   */
  default Set<String> getGroupIds(@NotEmpty String jobName, @NotEmpty String settingId) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets group Id paths.
   *
   * @param jobName          the job name
   * @param settingId        the setting id
   * @param repositoryFormat the repositoryFormat
   * @return the groupId paths
   */
  default Set<String> getGroupIdsForRepositoryFormat(
      @NotEmpty String jobName, @NotEmpty String settingId, @NotEmpty String repositoryFormat) {
    throw new UnsupportedOperationException();
  }

  /**
   * Validate Artifact Stream
   *
   * @param appId                    the app id
   * @param settingId                the setting id
   * @param artifactStreamAttributes the artifact stream attributes
   * @return the boolean
   */
  default boolean validateArtifactSource(
      @NotEmpty String appId, @NotEmpty String settingId, ArtifactStreamAttributes artifactStreamAttributes) {
    throw new UnsupportedOperationException();
  }

  /**
   * Validate Artifact Stream
   *
   * @param artifactStream
   * @return
   */
  default boolean validateArtifactSource(ArtifactStream artifactStream) {
    throw new UnsupportedOperationException();
  }

  /**
   * Validate Artifact Stream
   *
   * @param artifactStream
   * @return
   */
  default void validateAndInferArtifactSource(ArtifactStream artifactStream) {
    throw new UnsupportedOperationException();
  }

  /**
   * Get Job details
   *
   * @param appId
   * @param settingId
   * @param jobName
   * @return
   */
  default JobDetails getJob(@NotEmpty String appId, @NotEmpty String settingId, @NotEmpty String jobName) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets build service.
   *
   * @param settingAttribute the setting attribute
   * @param appId            the app id
   * @return the build service
   */
  default BuildService getBuildService(SettingAttribute settingAttribute, String appId) {
    throw new UnsupportedOperationException();
  }

  /***
   * Collects an artifact
   * @param appId
   * @param artifactStreamId
   * @param buildDetails
   * @return
   */
  default Artifact collectArtifact(String appId, String artifactStreamId, BuildDetails buildDetails) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets jobs.
   *
   * @param settingId     the jenkins setting id
   * @param parentJobName the jenkins parent job name (if any)
   * @return the jobs
   */
  default Set<JobDetails> getJobs(@NotEmpty String settingId, @Nullable String parentJobName) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets build service.
   *
   * @param settingAttribute the setting attribute
   * @return the build service
   */
  default BuildService getBuildService(SettingAttribute settingAttribute) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets artifact paths.
   *
   * @param jobName            the job name
   * @param settingId          the setting id
   * @param groupId            the group id
   * @param artifactStreamType artifact stream type
   * @return the artifact paths
   */
  default Set<String> getArtifactPaths(
      @NotEmpty String jobName, @NotEmpty String settingId, String groupId, String artifactStreamType) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets artifact paths.
   *
   * @param jobName            the job name
   * @param settingId          the setting id
   * @param groupId            the group id
   * @param artifactStreamType artifact stream type
   * @param repositoryFormat   the repository Format
   * @return the artifact paths
   */
  default Set<String> getArtifactPathsForRepositoryFormat(@NotEmpty String jobName, @NotEmpty String settingId,
      String groupId, String artifactStreamType, String repositoryFormat) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets last successful build.
   *
   * @param artifactStreamId the artifact stream id
   * @param settingId        the setting id
   * @return the last successful build
   */
  default BuildDetails getLastSuccessfulBuild(String artifactStreamId, String settingId) {
    throw new UnsupportedOperationException();
  }

  default Artifact collectArtifact(String artifactStreamId, BuildDetails buildDetails) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets plans.
   *
   * @param settingId          the setting id
   * @param artifactStreamType artifact stream type
   * @return the plans
   */
  default Map<String, String> getPlans(@NotEmpty String settingId, String artifactStreamType) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets plans for repository format.
   *
   * @param settingId
   * @param streamType
   * @param repositoryFormat
   * @return
   */
  default Map<String, String> getPlansForRepositoryFormat(
      @NotEmpty String settingId, String streamType, RepositoryFormat repositoryFormat) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets plans for repository type.
   *
   * @param settingId
   * @param streamType
   * @param repositoryType
   * @return
   */
  default Map<String, String> getPlansForRepositoryType(
      @NotEmpty String settingId, String streamType, RepositoryType repositoryType) {
    throw new UnsupportedOperationException();
  }

  /**
   * Get project.
   *
   * @param settingId the setting id
   * @return the project (now GCS only)
   */
  default String getProject(String settingId) {
    throw new UnsupportedOperationException();
  }

  /**
   * Get buckets.
   *
   * @param projectId the project id
   * @param settingId the setting id
   * @return the project (now GCS only)
   */
  default Map<String, String> getBuckets(String projectId, String settingId) {
    throw new UnsupportedOperationException();
  }

  /**
   * Get SMB paths.
   *
   * @param settingId the setting id
   * @return the paths (now SMB only)
   */
  default List<String> getSmbPaths(String settingId) {
    throw new UnsupportedOperationException();
  }

  /**
   * Get Artifact paths by stream id.
   *
   * @param settingId  the setting id
   * @param streamType artifact stream type
   * @return the paths
   */
  default List<String> getArtifactPathsByStreamType(String settingId, String streamType) {
    throw new UnsupportedOperationException();
  }

  default List<AzureDevopsProject> getProjects(String settingId) {
    throw new UnsupportedOperationException();
  }

  default List<AzureArtifactsFeed> getFeeds(String settingId, String project) {
    throw new UnsupportedOperationException();
  }

  default List<AzureArtifactsPackage> getPackages(String settingId, String project, String feed, String protocolType) {
    throw new UnsupportedOperationException();
  }

  default List<String> getGcbTriggers(String settingId) {
    throw new UnsupportedOperationException();
  }

  default List<String> listAcrRepositories(String cloudProviderId, String subscriptionId, String registryName) {
    throw new UnsupportedOperationException();
  }

  default List<AzureContainerRegistry> listAzureContainerRegistries(String cloudProviderId, String subscriptionId) {
    throw new UnsupportedOperationException();
  }

  default List<String> listAzureContainerRegistryNames(String cloudProviderId, String subscriptionId) {
    throw new UnsupportedOperationException();
  }

  default Map<String, String> listSubscriptions(String settingId) {
    throw new UnsupportedOperationException();
  }

  default List<AzureImageGallery> listImageGalleries(
      String cloudProviderId, String subscriptionId, String resourceGroupName) {
    throw new UnsupportedOperationException();
  }

  default List<AzureImageDefinition> listImageDefinitions(
      String cloudProviderId, String subscriptionId, String resourceGroupName, String galleryName) {
    throw new UnsupportedOperationException();
  }

  default List<AzureResourceGroup> listResourceGroups(String cloudProviderId, String subscriptionId) {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets list of build artifacts by artifact stream id and filter path.
   *
   * @param artifacts the list af artifacts
   * @param artifactStream the artifact stream source
   * @return the list of builds artifacts
   */
  List<Artifact> listArtifactByArtifactStreamAndFilterPath(List<Artifact> artifacts, ArtifactStream artifactStream);
}
