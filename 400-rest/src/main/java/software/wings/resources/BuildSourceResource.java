/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.beans.FeatureName.SPG_ALLOW_FILTER_BY_PATHS_GCS;
import static io.harness.beans.FeatureName.SPG_ALLOW_GET_BUILD_SYNC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.security.PermissionAttribute.ResourceType.APPLICATION;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.ff.FeatureFlagService;
import io.harness.rest.RestResponse;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.common.BuildDetailsComparator;
import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureDevopsProject;
import software.wings.helpers.ext.gcs.GcsService;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.persistence.artifact.Artifact;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.BuildSourceService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by anubhaw on 8/18/16.
 */
@Api("build-sources")
@Path("/build-sources")
@Produces("application/json")
// ToBeRevisited, this resource would be used from both artifact stream and verification step ui.
// Need to find out which auth rule to apply since its only determined at runtime
@Scope(APPLICATION)
public class BuildSourceResource {
  @Inject private BuildSourceService buildSourceService;
  @Inject private GcsService gcsService;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private ArtifactService artifactService;
  @Inject private FeatureFlagService featureFlagService;

  /**
   * Gets jobs.
   *
   * @param settingId the setting id
   * @return the jobs
   */
  @GET
  @Path("jobs")
  @Timed
  @ExceptionMetered
  public RestResponse<Set<JobDetails>> getJobs(@QueryParam("appId") String appId,
      @QueryParam("settingId") String settingId, @QueryParam("parentJobName") String parentJobName) {
    return new RestResponse<>(buildSourceService.getJobs(appId, settingId, parentJobName));
  }

  /**
   * Gets bamboo plans.
   *
   * @param settingId the setting id
   * @return the bamboo plans
   */
  @GET
  @Path("plans")
  @Timed
  @ExceptionMetered
  public RestResponse<Map<String, String>> getBuildPlans(@QueryParam("appId") String appId,
      @QueryParam("settingId") String settingId, @QueryParam("serviceId") String serviceId,
      @QueryParam("streamType") String streamType, @QueryParam("repositoryType") String repositoryType,
      @QueryParam("repositoryFormat") String repositoryFormat) {
    if (isBlank(serviceId)) {
      return new RestResponse<>(buildSourceService.getPlans(appId, settingId, streamType));
    }
    if (isNotEmpty(repositoryFormat)) {
      return new RestResponse<>(buildSourceService.getPlans(appId, settingId, serviceId, streamType, repositoryFormat));
    }
    return new RestResponse<>(buildSourceService.getPlans(appId, settingId, serviceId, streamType, repositoryType));
  }

  /**
   * Get GCS projects
   *
   * @param settingId the setting id
   * @return the project for the service account
   */
  @GET
  @Path("project")
  @Timed
  @ExceptionMetered
  public RestResponse<String> getProject(@QueryParam("appId") String appId, @QueryParam("settingId") String settingId) {
    return new RestResponse<>(buildSourceService.getProject(appId, settingId));
  }

  /**
   * Get GCS buckets
   *
   * @param appId  the app Id
   * @param projectId GCS project Id
   * @param settingId the setting id
   * @return list of buckets
   */
  @GET
  @Path("buckets")
  @Timed
  @ExceptionMetered
  public RestResponse<Map<String, String>> getBuckets(@QueryParam("appId") String appId,
      @QueryParam("projectId") @NotEmpty String projectId, @QueryParam("settingId") String settingId) {
    return new RestResponse<>(buildSourceService.getBuckets(appId, projectId, settingId));
  }

  /**
   * Gets artifact paths.
   *
   * @param jobName   the job name
   * @param settingId the setting id
   * @return the artifact paths
   */
  @GET
  @Path("jobs/{jobName}/paths")
  @Timed
  @ExceptionMetered
  public RestResponse<Set<String>> getArtifactPaths(@QueryParam("appId") String appId,
      @PathParam("jobName") String jobName, @QueryParam("settingId") String settingId,
      @QueryParam("groupId") String groupId, @QueryParam("streamType") String streamType,
      @QueryParam("repositoryFormat") String repositoryFormat) {
    if (isNotEmpty(repositoryFormat)) {
      return new RestResponse<>(
          buildSourceService.getArtifactPaths(appId, jobName, settingId, groupId, streamType, repositoryFormat));
    }
    return new RestResponse<>(buildSourceService.getArtifactPaths(appId, jobName, settingId, groupId, streamType));
  }

  /**
   * Get SMB artifact paths.
   *
   * @param settingId the setting id
   * @return the artifact paths
   */
  @GET
  @Path("smb-paths")
  @Timed
  @ExceptionMetered
  public RestResponse<List<String>> getSmbPaths(
      @QueryParam("appId") String appId, @QueryParam("settingId") String settingId) {
    return new RestResponse<>(buildSourceService.getSmbPaths(appId, settingId));
  }

  /**
   * Get SFTP artifact paths.
   *
   * @param settingId the setting id
   * @return the artifact paths
   */
  @GET
  @Path("artifact-paths")
  @Timed
  @ExceptionMetered
  public RestResponse<List<String>> getSftpPaths(@QueryParam("appId") String appId,
      @QueryParam("settingId") String settingId, @QueryParam("streamType") String streamType) {
    return new RestResponse<>(buildSourceService.getArtifactPathsByStreamType(appId, settingId, streamType));
  }

  /**
   * Gets artifact paths.
   *
   * @param jobName   the job name
   * @param settingId the setting id
   * @return group Ids
   */
  @GET
  @Path("jobs/{jobName}/groupIds")
  @Timed
  @ExceptionMetered
  public RestResponse<Set<String>> getGroupIds(@QueryParam("appId") String appId, @PathParam("jobName") String jobName,
      @QueryParam("settingId") String settingId, @QueryParam("repositoryFormat") String repositoryFormat) {
    if (isNotEmpty(repositoryFormat)) {
      return new RestResponse<>(buildSourceService.getGroupIds(appId, jobName, settingId, repositoryFormat));
    }
    return new RestResponse<>(buildSourceService.getGroupIds(appId, jobName, settingId));
  }

  @GET
  @Path("nexus/repositories/{repositoryName}/packageNames")
  @Timed
  @ExceptionMetered
  public RestResponse<Set<String>> fetchPackageNames(@QueryParam("appId") String appId,
      @PathParam("repositoryName") String repositoryName, @QueryParam("repositoryFormat") String repositoryFormat,
      @QueryParam("settingId") String settingId) {
    return new RestResponse<>(
        buildSourceService.fetchNexusPackageNames(appId, repositoryName, repositoryFormat, settingId));
  }

  /**
   * Gets builds.
   *
   * @param appId            the app id
   * @param artifactStreamId the artifact source id
   * @return the builds
   */
  @GET
  @Path("builds")
  @Timed
  @ExceptionMetered
  public RestResponse<List<BuildDetails>> getBuilds(@QueryParam("appId") String appId,
      @QueryParam("artifactStreamId") String artifactStreamId, @QueryParam("settingId") String settingId) {
    List<BuildDetails> buildDetails = buildSourceService.getBuilds(appId, artifactStreamId, settingId);
    buildDetails = buildDetails.stream().sorted(new BuildDetailsComparator()).collect(toList());
    return new RestResponse<>(buildDetails);
  }

  /*
  This API is used in deploy form. If artifact collection is enabled, return builds from db, else create a delegate task
  and get the builds.
   */
  @GET
  @Path("buildsV2")
  @Timed
  @ExceptionMetered
  public RestResponse<List<BuildDetails>> getBuildsV2(
      @QueryParam("appId") String appId, @QueryParam("artifactStreamId") String artifactStreamId) {
    List<BuildDetails> buildDetails;
    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    if (!Boolean.FALSE.equals(artifactStream.getCollectionEnabled())) {
      if (featureFlagService.isEnabled(SPG_ALLOW_GET_BUILD_SYNC, artifactStream.getAccountId())) {
        buildDetails = buildSourceService.getBuilds(appId, artifactStreamId, artifactStream.getSettingId());
        buildDetails = buildDetails.stream().sorted(new BuildDetailsComparator()).collect(toList());
      } else {
        List<Artifact> artifacts =
            artifactService.listArtifactsByArtifactStreamId(artifactStream.getAccountId(), artifactStreamId);

        if (featureFlagService.isEnabled(SPG_ALLOW_FILTER_BY_PATHS_GCS, artifactStream.getAccountId())
            && ArtifactStreamType.GCS.name().equals(artifactStream.getArtifactStreamType())) {
          buildDetails =
              buildSourceService.listArtifactByArtifactStreamAndFilterPath(artifacts, artifactStream)
                  .stream()
                  .map(artifact -> BuildDetails.Builder.aBuildDetails().withNumber(artifact.getBuildNo()).build())
                  .collect(toList());
        } else {
          buildDetails =
              artifacts.stream()
                  .map(artifact -> BuildDetails.Builder.aBuildDetails().withNumber(artifact.getBuildNo()).build())
                  .collect(toList());
        }
      }
    } else {
      if (ArtifactStreamType.CUSTOM.name().equals(artifactStream.getArtifactStreamType())) {
        CustomArtifactStream customArtifactStream = (CustomArtifactStream) artifactStream;
        CustomArtifactStream.Script versionScript =
            customArtifactStream.getScripts()
                .stream()
                .filter(script
                    -> script.getAction() == null || script.getAction() == CustomArtifactStream.Action.FETCH_VERSIONS)
                .findFirst()
                .orElse(CustomArtifactStream.Script.builder().build());
        if (isEmpty(versionScript.getScriptString())) {
          return new RestResponse<>(new ArrayList<>());
        }
      }
      buildDetails = buildSourceService.getBuilds(appId, artifactStreamId, artifactStream.getSettingId());
      buildDetails = buildDetails.stream().sorted(new BuildDetailsComparator()).collect(toList());
    }
    return new RestResponse<>(buildDetails);
  }

  @GET
  @Path("jobs/{jobName}/details")
  @Timed
  @ExceptionMetered
  public RestResponse<JobDetails> getJob(@QueryParam("appId") String appId, @QueryParam("settingId") String settingId,
      @PathParam("jobName") String jobName) {
    return new RestResponse<>(buildSourceService.getJob(appId, settingId, jobName));
  }

  /***
   * Collects an artifact
   * @param appId
   * @param artifactStreamId
   * @param buildDetails
   * @return
   */
  @POST
  @Timed
  @ExceptionMetered
  public RestResponse<Artifact> collectArtifact(@QueryParam("appId") String appId,
      @QueryParam("artifactStreamId") String artifactStreamId, BuildDetails buildDetails) {
    return new RestResponse<>(buildSourceService.collectArtifact(appId, artifactStreamId, buildDetails));
  }

  @GET
  @Path("projects")
  @Timed
  @ExceptionMetered
  public RestResponse<List<AzureDevopsProject>> listProjects(
      @QueryParam("accountId") String accountId, @QueryParam("settingId") String settingId) {
    return new RestResponse<>(buildSourceService.getProjects(settingId));
  }

  @GET
  @Path("feeds")
  @Timed
  @ExceptionMetered
  public RestResponse<List<AzureArtifactsFeed>> listFeeds(@QueryParam("accountId") String accountId,
      @QueryParam("settingId") String settingId, @QueryParam("project") String project) {
    return new RestResponse<>(buildSourceService.getFeeds(settingId, project));
  }

  @GET
  @Path("feeds/{feed}/packages")
  @Timed
  @ExceptionMetered
  public RestResponse<List<AzureArtifactsPackage>> listPackages(@QueryParam("accountId") String accountId,
      @QueryParam("settingId") String settingId, @QueryParam("project") String project, @PathParam("feed") String feed,
      @QueryParam("protocolType") String protocolType) {
    return new RestResponse<>(buildSourceService.getPackages(settingId, project, feed, protocolType));
  }

  @GET
  @Path("gcb-triggers")
  @Timed
  @ExceptionMetered
  public RestResponse<List<String>> getTriggers(
      @QueryParam("accountId") String accountId, @QueryParam("settingId") String settingId) {
    return new RestResponse<>(buildSourceService.getGcbTriggers(settingId));
  }
}
