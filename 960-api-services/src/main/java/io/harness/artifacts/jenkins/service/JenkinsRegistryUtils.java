/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.jenkins.service;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.threading.Morpheus.quietSleep;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;
import static software.wings.helpers.ext.jenkins.JenkinsJobPathBuilder.constructParentJobPath;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.artifacts.jenkins.beans.JenkinsInternalConfig;
import io.harness.artifacts.jenkins.client.JenkinsClient;
import io.harness.artifacts.jenkins.client.JenkinsCustomServer;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.beans.artifact.ArtifactFileMetadata;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.WingsException;

import software.wings.common.BuildDetailsComparator;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.CustomJenkinsHttpClient;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.helpers.ext.jenkins.SvnBuildDetails;
import software.wings.helpers.ext.jenkins.SvnRevision;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.offbytwo.jenkins.model.Artifact;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.BuildResult;
import com.offbytwo.jenkins.model.BuildWithDetails;
import com.offbytwo.jenkins.model.FolderJob;
import com.offbytwo.jenkins.model.Job;
import com.offbytwo.jenkins.model.JobWithDetails;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.Stack;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpResponseException;

@Slf4j
public class JenkinsRegistryUtils {
  public static final String TOKEN_FIELD = "Bearer Token(HTTP Header)";
  public static final String USERNAME_PASSWORD = "UsernamePassword";
  private final String FOLDER_JOB_CLASS_NAME = "com.cloudbees.hudson.plugins.folder.Folder";
  private final String MULTI_BRANCH_JOB_CLASS_NAME =
      "org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject";
  private final String BLUE_STEEL_TEAM_FOLDER_CLASS_NAME =
      "com.cloudbees.opscenter.bluesteel.folder.BlueSteelTeamFolder";
  private final String ORGANIZATION_FOLDER_CLASS_NAME = "jenkins.branch.OrganizationFolder";
  private final String SERVER_ERROR = "Server Error";

  @Inject private ExecutorService executorService;
  @Inject private TimeLimiter timeLimiter;

  public boolean isRunning(JenkinsInternalConfig jenkinsInternalConfig) {
    try {
      CustomJenkinsHttpClient client = JenkinsClient.getJenkinsHttpClient(jenkinsInternalConfig);
      client.get("/");
      return true;
    } catch (IOException | URISyntaxException e) {
      throw prepareWingsException((IOException) e);
    }
  }

  public BuildDetails getLastSuccessfulBuildForJob(
      JenkinsInternalConfig jenkinsInternalConfig, String jobName, List<String> artifactPaths) throws IOException {
    JobWithDetails jobWithDetails = getJobWithDetails(jenkinsInternalConfig, jobName);
    if (jobWithDetails == null) {
      log.info("Job {} does not exist", jobName);
      return null;
    }

    Build lastSuccessfulBuild = jobWithDetails.getLastSuccessfulBuild();
    if (lastSuccessfulBuild == null) {
      log.info("There is no last successful build for job {}", jobName);
      return null;
    }
    BuildWithDetails buildWithDetails = lastSuccessfulBuild.details();
    return getBuildDetails(buildWithDetails, artifactPaths);
  }

  public List<BuildDetails> getBuildsForJob(JenkinsInternalConfig jenkinsInternalConfig, String jobname,
      List<String> artifactPaths, int lastN) throws IOException {
    return getBuildsForJob(jenkinsInternalConfig, jobname, artifactPaths, lastN, false);
  }

  public List<BuildDetails> getBuildsForJob(JenkinsInternalConfig jenkinsInternalConfig, String jobname,
      List<String> artifactPaths, int lastN, boolean allStatuses) throws IOException {
    JobWithDetails jobWithDetails = getJobWithDetails(jenkinsInternalConfig, jobname);
    if (jobWithDetails == null) {
      return Lists.newArrayList();
    }
    List<BuildDetails> buildDetails = Lists.newArrayList(
        jobWithDetails.getBuilds()
            .stream()
            .limit(lastN)
            .map(build -> {
              try {
                return build.details();
              } catch (IOException e) {
                return build;
              }
            })
            .filter(BuildWithDetails.class ::isInstance)
            .map(build -> (BuildWithDetails) build)
            .filter(!allStatuses ? build
                -> (build.getResult() == BuildResult.SUCCESS) && isNotEmpty(build.getArtifacts())
                                 : build
                -> (build.getResult() == BuildResult.SUCCESS || build.getResult() == BuildResult.UNSTABLE
                       || build.getResult() == BuildResult.FAILURE)
                    && isNotEmpty(build.getArtifacts()))
            .map(buildWithDetails1 -> getBuildDetails(buildWithDetails1, artifactPaths))
            .collect(toList()));
    return buildDetails.stream().sorted(new BuildDetailsComparator()).collect(toList());
  }

  public BuildDetails getBuildDetails(BuildWithDetails buildWithDetails, List<String> artifactPaths) {
    List<ArtifactFileMetadata> artifactFileMetadata = getArtifactFileMetadata(buildWithDetails, artifactPaths);
    BuildDetails buildDetails = aBuildDetails()
                                    .withNumber(String.valueOf(buildWithDetails.getNumber()))
                                    .withRevision(extractRevision(buildWithDetails))
                                    .withDescription(buildWithDetails.getDescription())
                                    .withBuildDisplayName(buildWithDetails.getDisplayName())
                                    .withBuildUrl(buildWithDetails.getUrl())
                                    .withBuildFullDisplayName(buildWithDetails.getFullDisplayName())
                                    .withStatus(BuildDetails.BuildStatus.valueOf(buildWithDetails.getResult().name()))
                                    .withUiDisplayName("Build# " + buildWithDetails.getNumber())
                                    .withArtifactDownloadMetadata(artifactFileMetadata)
                                    .build();
    populateBuildParams(buildWithDetails, buildDetails);
    return buildDetails;
  }

  private String extractRevision(BuildWithDetails buildWithDetails) {
    Optional<String> gitRevOpt =
        buildWithDetails.getActions()
            .stream()
            .filter(o -> ((Map<String, Object>) o).containsKey("lastBuiltRevision"))
            .map(o
                -> ((Map<String, Object>) (((Map<String, Object>) o).get("lastBuiltRevision"))).get("SHA1").toString())
            .findFirst();
    if (gitRevOpt.isPresent()) {
      return gitRevOpt.get();
    } else if (buildWithDetails.getChangeSet() != null && "svn".equals(buildWithDetails.getChangeSet().getKind())) {
      try {
        SvnBuildDetails svnBuildDetails =
            buildWithDetails.getClient().get(buildWithDetails.getUrl(), SvnBuildDetails.class);
        OptionalInt revision =
            svnBuildDetails.getChangeSet().getRevisions().stream().mapToInt(SvnRevision::getRevision).max();
        return Integer.toString(revision.getAsInt());
      } catch (Exception e) {
        return Long.toString(buildWithDetails.getTimestamp());
      }
    } else {
      return Long.toString(buildWithDetails.getTimestamp());
    }
  }

  public void populateBuildParams(BuildWithDetails buildWithDetails, BuildDetails buildDetails) {
    try {
      if (buildWithDetails.getParameters() != null) {
        buildDetails.setBuildParameters(buildWithDetails.getParameters());
      }
    } catch (Exception e) { // cause buildWithDetails.getParameters() can throw NPE
      // unexpected exception
      log.warn(
          "Error occurred while retrieving build parameters for build number {} ", buildWithDetails.getNumber(), e);
    }
  }

  private List<ArtifactFileMetadata> getArtifactFileMetadata(
      BuildWithDetails buildWithDetails, List<String> artifactPaths) {
    List<ArtifactFileMetadata> artifactFileMetadata = new ArrayList<>();
    if (isNotEmpty(artifactPaths)) {
      List<Artifact> buildArtifacts = buildWithDetails.getArtifacts();
      if (isNotEmpty(buildArtifacts)) {
        for (String artifactPath : artifactPaths) {
          // only if artifact path is not empty check if there is a match
          if (isNotEmpty(artifactPath.trim())) {
            Pattern pattern = Pattern.compile(artifactPath.replace(".", "\\.").replace("?", ".?").replace("*", ".*?"));
            Optional<Artifact> artifactOpt = buildWithDetails.getArtifacts()
                                                 .stream()
                                                 .filter(artifact -> pattern.matcher(artifact.getRelativePath()).find())
                                                 .findFirst();
            if (artifactOpt.isPresent()) {
              Artifact artifact = artifactOpt.get();
              String fileName = artifact.getFileName();
              String url = buildWithDetails.getUrl() + "artifact/" + artifact.getRelativePath();
              artifactFileMetadata.add(ArtifactFileMetadata.builder().fileName(fileName).url(url).build());
            }
          }
        }
      }
    }
    return artifactFileMetadata;
  }

  public JobWithDetails getJobWithDetails(JenkinsInternalConfig jenkinsInternalConfig, String jobname) {
    log.info("Retrieving job {}", jobname);
    try {
      return HTimeLimiter.callUninterruptible(timeLimiter, Duration.ofSeconds(120), () -> {
        while (true) {
          if (jobname == null) {
            sleep(ofSeconds(1L));
            continue;
          }

          JobPathDetails jobPathDetails = constructJobPathDetails(jobname);
          JobWithDetails jobWithDetails;

          try {
            JenkinsCustomServer jenkinsServer = JenkinsClient.getJenkinsServer(jenkinsInternalConfig);
            FolderJob folderJob = getFolderJob(jobPathDetails.getParentJobName(), jobPathDetails.getParentJobUrl());
            jobWithDetails = jenkinsServer.getJob(folderJob, jobPathDetails.getChildJobName());

          } catch (HttpResponseException e) {
            if (e.getStatusCode() == 500 || ExceptionUtils.getMessage(e).contains(SERVER_ERROR)) {
              log.warn("Error occurred while retrieving job with details {}. Retrying ", jobname, e);
              sleep(ofSeconds(1L));
              continue;
            } else {
              throw e;
            }
          }
          log.info("Retrieving job with details {} success", jobname);
          return singletonList(jobWithDetails).get(0);
        }
      });
    } catch (Exception e) {
      throw new ArtifactServerException(
          "Failure in fetching job with details: " + ExceptionUtils.getMessage(e), e, USER);
    }
  }

  public List<JobDetails> getJobs(JenkinsInternalConfig jenkinsInternalConfig, String parentJob) {
    try {
      return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(120), () -> {
        while (true) {
          List<JobDetails> details = getJobDetails(jenkinsInternalConfig, parentJob);
          if (details != null) {
            return details;
          }
          sleep(ofMillis(100L));
        }
      });
    } catch (Exception e) {
      throw new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER);
    }
  }

  private List<JobDetails> getJobDetails(JenkinsInternalConfig jenkinsInternalConfig, String parentJob) {
    List<JobDetails> result = new ArrayList<>(); // TODO:: extend jobDetails to keep track of prefix.
    try {
      JenkinsCustomServer jenkinsServer = JenkinsClient.getJenkinsServer(jenkinsInternalConfig);
      Stack<Job> jobs = new Stack<>();
      Queue<Future> futures = new ConcurrentLinkedQueue<>();
      if (isBlank(parentJob)) {
        return jenkinsServer.getJobs()
            .values()
            .stream()
            .map(job -> new JobDetails(getJobNameFromUrl(job.getUrl()), job.getUrl(), isFolderJob(job)))
            .collect(toList());
      } else {
        jobs.addAll(jenkinsServer.getJobs(new FolderJob(parentJob, "/job/" + parentJob + "/")).values());
      }

      while (!jobs.empty() || !futures.isEmpty()) {
        while (!jobs.empty()) {
          Job job = jobs.pop();
          if (isFolderJob(job)) {
            futures.add(executorService.submit(
                () -> jobs.addAll(jenkinsServer.getJobs(new FolderJob(job.getName(), job.getUrl())).values())));
          } else {
            String jobName = getJobNameFromUrl(job.getUrl());
            result.add(new JobDetails(jobName, job.getUrl(), false));
          }
        }
        while (!futures.isEmpty() && futures.peek().isDone()) {
          futures.poll().get();
        }
        quietSleep(ofMillis(10));
      }
      return result;
    } catch (Exception ex) {
      log.error("Error in fetching job lists ", ex);
      return result;
    }
  }

  private String getJobNameFromUrl(String url) {
    // TODO:: remove it post review. Extend jobDetails object
    // Each jenkins server could have a different base url.
    // Whichever is the format, the url after the base would always start with "/job/"
    String relativeUrl;
    String pattern = ".*?/job/";

    relativeUrl = url.replaceFirst(pattern, "");

    String[] parts = relativeUrl.split("/");
    StringBuilder nameBuilder = new StringBuilder();
    // We start with index 0 since /job/ has already been
    for (int idx = 0; idx <= parts.length - 1; idx = idx + 2) {
      nameBuilder.append('/').append(parts[idx]);
    }
    String name = nameBuilder.toString();
    name = name.charAt(0) == '/' ? name.substring(1) : name;
    return getNormalizedName(name);
  }

  protected String getNormalizedName(String jobName) {
    try {
      if (isNotEmpty(jobName)) {
        return URLDecoder.decode(jobName, Charsets.UTF_8.name());
      }
    } catch (UnsupportedEncodingException e) {
      log.warn("Failed to decode jobName {}", jobName, e);
    }
    return jobName;
  }

  private boolean isFolderJob(Job job) {
    // job.get_class().equals(FOLDER_JOB_CLASS_NAME) is to find if the jenkins job is of type folder.
    // (job instanceOf FolderJob) doesn't work
    return job.get_class().equals(FOLDER_JOB_CLASS_NAME) || job.get_class().equals(MULTI_BRANCH_JOB_CLASS_NAME)
        || job.get_class().equals(BLUE_STEEL_TEAM_FOLDER_CLASS_NAME)
        || job.get_class().equals(ORGANIZATION_FOLDER_CLASS_NAME);
  }

  private WingsException prepareWingsException(IOException e) {
    if (e instanceof HttpResponseException) {
      if (((HttpResponseException) e).getStatusCode() == 401) {
        throw new ArtifactServerException("Invalid Jenkins credentials", USER);
      } else if (((HttpResponseException) e).getStatusCode() == 403) {
        throw new ArtifactServerException("User not authorized to access jenkins", USER);
      }
    }
    throw new ArtifactServerException(ExceptionUtils.getMessage(e), e, USER);
  }

  /**
   * Constructs job path details by provided job name
   *
   * @param jobname        job name
   * @return job path details.
   */
  private JobPathDetails constructJobPathDetails(String jobname) {
    String parentJobName = null;
    String parentJobUrl = null;
    String childJobName;

    try {
      String decodedJobName = URLDecoder.decode(jobname, "UTF-8");

      String[] jobNameSplit = decodedJobName.split("/");
      int parts = jobNameSplit.length;
      if (parts > 1) {
        parentJobUrl = constructParentJobPath(jobNameSplit);
        parentJobName = jobNameSplit[parts - 2];
        childJobName = jobNameSplit[parts - 1];
      } else {
        childJobName = decodedJobName;
      }

      return new JobPathDetails(parentJobUrl, parentJobName, childJobName);

    } catch (UnsupportedEncodingException e) {
      throw new ArtifactServerException("Failure in decoding job name: " + ExceptionUtils.getMessage(e), e, USER);
    }
  }

  /**
   * Returns folder instance
   *
   * @param parentJobName      parent job name
   * @param parentJobUrl       parent job url
   * @return new folder.
   */
  private FolderJob getFolderJob(String parentJobName, String parentJobUrl) {
    FolderJob folderJob = null;
    if (parentJobName != null && parentJobName.length() > 0) {
      folderJob = new FolderJob(parentJobName, parentJobUrl);
    }
    return folderJob;
  }

  @Data
  private class JobPathDetails {
    String parentJobUrl;
    String parentJobName;
    String childJobName;

    JobPathDetails(String parentJobUrl, String parentJobName, String childJobName) {
      this.parentJobUrl = parentJobUrl;
      this.parentJobName = parentJobName;
      this.childJobName = childJobName;
    }
  }
}
