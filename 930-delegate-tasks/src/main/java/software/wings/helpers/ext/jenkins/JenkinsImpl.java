/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.jenkins;

import static io.harness.annotations.dev.HarnessModule._960_API_SERVICES;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.stream.StreamUtils.getInputStreamSize;
import static io.harness.threading.Morpheus.quietSleep;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;
import static software.wings.helpers.ext.jenkins.JenkinsJobPathBuilder.constructParentJobPath;
import static software.wings.helpers.ext.jenkins.JenkinsJobPathBuilder.getJenkinsJobPath;
import static software.wings.helpers.ext.jenkins.JenkinsJobPathBuilder.getJobPathFromJenkinsJobUrl;

import static java.lang.String.format;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.beans.artifact.ArtifactFileMetadata;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.network.Http;
import io.harness.serializer.JsonUtils;

import software.wings.beans.JenkinsConfig;
import software.wings.beans.command.JenkinsTaskParams;
import software.wings.common.BuildDetailsComparator;
import software.wings.helpers.ext.jenkins.BuildDetails.BuildStatus;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.jayway.jsonpath.DocumentContext;
import com.offbytwo.jenkins.model.Artifact;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.BuildResult;
import com.offbytwo.jenkins.model.BuildWithDetails;
import com.offbytwo.jenkins.model.Executable;
import com.offbytwo.jenkins.model.ExtractHeader;
import com.offbytwo.jenkins.model.FolderJob;
import com.offbytwo.jenkins.model.Job;
import com.offbytwo.jenkins.model.JobWithDetails;
import com.offbytwo.jenkins.model.QueueItem;
import com.offbytwo.jenkins.model.QueueReference;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
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
import java.util.stream.Collectors;
import javax.net.ssl.HostnameVerifier;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;

/**
 * The Class JenkinsImpl.
 */
@OwnedBy(CDC)
@TargetModule(_960_API_SERVICES)
@Slf4j
public class JenkinsImpl implements Jenkins {
  private final String FOLDER_JOB_CLASS_NAME = "com.cloudbees.hudson.plugins.folder.Folder";
  private final String MULTI_BRANCH_JOB_CLASS_NAME =
      "org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject";
  private final String BLUE_STEEL_TEAM_FOLDER_CLASS_NAME =
      "com.cloudbees.opscenter.bluesteel.folder.BlueSteelTeamFolder";
  private final String ORGANIZATION_FOLDER_CLASS_NAME = "jenkins.branch.OrganizationFolder";
  private final String SERVER_ERROR = "Server Error";

  @Inject private ExecutorService executorService;
  @Inject private TimeLimiter timeLimiter;

  private CustomJenkinsServer jenkinsServer;
  private CustomJenkinsHttpClient jenkinsHttpClient;

  /**
   * Instantiates a new jenkins impl.
   *
   * @param jenkinsUrl the jenkins url
   * @throws URISyntaxException the URI syntax exception
   */
  @AssistedInject
  public JenkinsImpl(@Assisted(value = "url") String jenkinsUrl) throws URISyntaxException {
    jenkinsHttpClient = new CustomJenkinsHttpClient(new URI(jenkinsUrl), getUnSafeBuilder());
    jenkinsServer = new CustomJenkinsServer(jenkinsHttpClient);
  }

  /**
   * Instantiates a new jenkins impl.
   *
   * @param jenkinsUrl the jenkins url
   * @param username   the username
   * @param password   the password
   * @throws URISyntaxException the URI syntax exception
   */
  @AssistedInject
  public JenkinsImpl(@Assisted(value = "url") String jenkinsUrl, @Assisted(value = "username") String username,
      @Assisted(value = "password") char[] password) throws URISyntaxException {
    jenkinsHttpClient =
        new CustomJenkinsHttpClient(new URI(jenkinsUrl), username, new String(password), getUnSafeBuilder());
    jenkinsServer = new CustomJenkinsServer(jenkinsHttpClient);
  }

  /**
   * Instantiates a new jenkins impl.
   *
   */
  @AssistedInject
  public JenkinsImpl(@Assisted(value = "url") String jenkinsUrl, @Assisted(value = "token") char[] token)
      throws URISyntaxException {
    jenkinsHttpClient = new CustomJenkinsHttpClient(new URI(jenkinsUrl), new String(token), getUnSafeBuilder());
    jenkinsServer = new CustomJenkinsServer(jenkinsHttpClient);
  }

  /* (non-Javadoc)
   * @see software.wings.helpers.ext.jenkins.Jenkins#getJobWithDetails(java.lang.String)
   */
  @Override
  public JobWithDetails getJobWithDetails(String jobname) {
    log.info("Retrieving job {}", jobname);
    try {
      return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(120), () -> {
        while (true) {
          if (jobname == null) {
            sleep(ofSeconds(1L));
            continue;
          }

          JobPathDetails jobPathDetails = constructJobPathDetails(jobname);
          JobWithDetails jobWithDetails;

          try {
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

  /* (non-Javadoc)
   * @see software.wings.helpers.ext.jenkins.Jenkins#getJob(java.lang.String)
   */
  @Override
  public Job getJob(String jobname, JenkinsConfig jenkinsConfig) {
    log.info("Retrieving job {}", jobname);
    try {
      return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(120), () -> {
        while (true) {
          if (jobname == null) {
            sleep(ofSeconds(1L));
            continue;
          }

          JobPathDetails jobPathDetails = constructJobPathDetails(jobname);
          Job job;

          try {
            FolderJob folderJob = getFolderJob(jobPathDetails.getParentJobName(), jobPathDetails.getParentJobUrl());
            job = jenkinsServer.createJob(folderJob, jobPathDetails.getChildJobName(), jenkinsConfig);

          } catch (HttpResponseException e) {
            if (e.getStatusCode() == 500 || ExceptionUtils.getMessage(e).contains(SERVER_ERROR)) {
              log.warn("Error occurred while retrieving job {}. Retrying ", jobname, e);
              sleep(ofSeconds(1L));
              continue;
            } else {
              throw e;
            }
          }
          log.info("Retrieving job {} success", jobname);
          return singletonList(job).get(0);
        }
      });
    } catch (Exception e) {
      throw new ArtifactServerException("Failure in fetching job: " + ExceptionUtils.getMessage(e), e, USER);
    }
  }

  @Override
  public List<JobDetails> getJobs(String parentJob) {
    try {
      return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(120), () -> {
        while (true) {
          List<JobDetails> details = getJobDetails(parentJob);
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

  private List<JobDetails> getJobDetails(String parentJob) {
    List<JobDetails> result = new ArrayList<>(); // TODO:: extend jobDetails to keep track of prefix.
    try {
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

  /**
   * This method generates the ui display name from the jenkins url.
   * The jenkins url looks like &lt;jenkins_base_url&gt;/job/job1/job/job2/job/job3
   * from which we have to show job1/job2/job3 in the ui.
   * @param url
   * @return
   */
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

  private boolean isFolderJob(Job job) {
    // job.get_class().equals(FOLDER_JOB_CLASS_NAME) is to find if the jenkins job is of type folder.
    // (job instanceOf FolderJob) doesn't work
    return job.get_class().equals(FOLDER_JOB_CLASS_NAME) || job.get_class().equals(MULTI_BRANCH_JOB_CLASS_NAME)
        || job.get_class().equals(BLUE_STEEL_TEAM_FOLDER_CLASS_NAME)
        || job.get_class().equals(ORGANIZATION_FOLDER_CLASS_NAME);
  }

  /* (non-Javadoc)
   * @see software.wings.helpers.ext.jenkins.Jenkins#getBuilds(java.lang.String, int)
   */
  @Override
  public List<BuildDetails> getBuildsForJob(String jobname, List<String> artifactPaths, int lastN) throws IOException {
    return getBuildsForJob(jobname, artifactPaths, lastN, false);
  }

  @Override
  public List<BuildDetails> getBuildsForJob(String jobname, List<String> artifactPaths, int lastN, boolean allStatuses)
      throws IOException {
    JobWithDetails jobWithDetails = getJobWithDetails(jobname);
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
                                    .withStatus(BuildStatus.valueOf(buildWithDetails.getResult().name()))
                                    .withUiDisplayName("Build# " + buildWithDetails.getNumber())
                                    .withArtifactDownloadMetadata(artifactFileMetadata)
                                    .build();
    populateBuildParams(buildWithDetails, buildDetails);
    return buildDetails;
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

  @Override
  public BuildDetails getLastSuccessfulBuildForJob(String jobName, List<String> artifactPaths) throws IOException {
    JobWithDetails jobWithDetails = getJobWithDetails(jobName);
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

  /* (non-Javadoc)
   * @see software.wings.helpers.ext.jenkins.Jenkins#trigger(java.lang.String)
   */
  @Override
  public QueueReference trigger(String jobname, JenkinsTaskParams jenkinsTaskParams) throws IOException {
    Map<String, String> parameters = jenkinsTaskParams.getParameters();
    JenkinsConfig jenkinsConfig = jenkinsTaskParams.getJenkinsConfig();

    Job job = getJob(jobname, jenkinsConfig);
    if (job == null) {
      throw new ArtifactServerException("No job [" + jobname + "] found", USER);
    }

    QueueReference queueReference;
    try {
      log.info("Triggering job {} ", job.getUrl());
      if (isEmpty(parameters)) {
        ExtractHeader location = job.getClient().post(job.getUrl() + "build", null, ExtractHeader.class, true);
        queueReference = new QueueReference(location.getLocation());
      } else {
        queueReference = job.build(parameters, true);
      }
      log.info("Triggering job {} success ", job.getUrl());
      return queueReference;
    } catch (HttpResponseException e) {
      if (e.getStatusCode() == 400 && isEmpty(parameters)) {
        throw new InvalidRequestException(
            format(
                "Failed to trigger job %s with url %s.%nThis might be because the Jenkins job requires parameters but none were provided in the Jenkins step.",
                jobname, job.getUrl()),
            USER);
      }
      throw e;
    } catch (IOException e) {
      throw new IOException(format("Failed to trigger job %s with url %s", jobname, job.getUrl()), e);
    }
  }

  /* (non-Javadoc)
   * @see software.wings.helpers.ext.jenkins.Jenkins#checkStatus(java.lang.String)
   */
  @Override
  public String checkStatus(String jobname) {
    throw new NotImplementedException("Not implemented");
  }

  /* (non-Javadoc)
   * @see software.wings.helpers.ext.jenkins.Jenkins#checkStatus(java.lang.String, java.lang.String)
   */
  @Override
  public String checkStatus(String jobname, String buildNo) {
    throw new NotImplementedException("Not implemented");
  }

  /* (non-Javadoc)
   * @see software.wings.helpers.ext.jenkins.Jenkins#checkArtifactStatus(java.lang.String, java.lang.String)
   */
  @Override
  public String checkArtifactStatus(String jobname, String artifactpathRegex) {
    throw new NotImplementedException("Not implemented");
  }

  /* (non-Javadoc)
   * @see software.wings.helpers.ext.jenkins.Jenkins#checkArtifactStatus(java.lang.String, java.lang.String,
   * java.lang.String)
   */
  @Override
  public String checkArtifactStatus(String jobname, String buildNo, String artifactpathRegex) {
    throw new NotImplementedException("Not implemented");
  }

  /* (non-Javadoc)
   * @see software.wings.helpers.ext.jenkins.Jenkins#downloadArtifacts(java.lang.String, java.lang.String)
   */
  @Override
  public Pair<String, InputStream> downloadArtifact(String jobname, String artifactpathRegex)
      throws IOException, URISyntaxException {
    JobWithDetails jobDetails = getJobWithDetails(jobname);
    if (jobDetails == null) {
      return null;
    }
    Build build = jobDetails.getLastCompletedBuild();
    return downloadArtifactFromABuild(build, artifactpathRegex);
  }

  /* (non-Javadoc)
   * @see software.wings.helpers.ext.jenkins.Jenkins#downloadArtifacts(java.lang.String, java.lang.String,
   * java.lang.String)
   */
  @Override
  public Pair<String, InputStream> downloadArtifact(String jobname, String buildNo, String artifactpathRegex)
      throws IOException, URISyntaxException {
    log.info("Downloading artifactpathRegex {}, buildNo {} and jobname {}", artifactpathRegex, buildNo, jobname);
    JobWithDetails jobDetails = getJobWithDetails(jobname);
    if (jobDetails == null) {
      return null;
    }
    Build build = jobDetails.getBuildByNumber(Integer.parseInt(buildNo));
    return downloadArtifactFromABuild(build, artifactpathRegex);
  }

  @Override
  public Build getBuild(QueueReference queueReference, JenkinsConfig jenkinsConfig) throws IOException {
    log.info("Retrieving queued item for job URL {}", queueReference.getQueueItemUrlPart());

    QueueItem queueItem = jenkinsServer.getQueueItem(queueReference);
    String buildUrl = null;

    if (queueItem == null) {
      log.info("Queue item value is null");
      return null;
    } else if (queueItem.getExecutable() == null) {
      log.info("Executable value is null");
      return null;
    } else if (queueItem.getTask() == null) {
      log.info("Task value is null");
      return null;
    } else {
      log.info("Queued item {} returned successfully", queueItem);
      log.info("Executable value {}", queueItem.getExecutable());
      log.info("Task value {}", queueItem.getTask());
    }

    log.info("Executable number is {}", queueItem.getExecutable().getNumber());
    log.info("Executable URL is {}", queueItem.getExecutable().getUrl());

    log.info("Task URL is {}", queueItem.getTask().getUrl());
    log.info("Task name is {}", queueItem.getTask().getName());

    log.info("Queue item URL is {}", queueItem.getUrl());
    log.info("Queue item ID is {}", queueItem.getId());

    if (jenkinsConfig.isUseConnectorUrlForJobExecution()) {
      buildUrl = getBuildUrl(jenkinsConfig.getJenkinsUrl(), getJobPathFromJenkinsJobUrl(queueItem.getTask().getUrl()),
          queueItem.getExecutable().getNumber().toString());

      configureExecutable(queueItem, buildUrl);
    }

    Build build = jenkinsServer.getBuild(queueItem);

    if (jenkinsConfig.isUseConnectorUrlForJobExecution()) {
      log.info("Retrieving build with URL {}", buildUrl);
      return createBuild(build, buildUrl);
    }

    log.info("Retrieving build with URL {}", build.getUrl());
    return build;
  }

  @Override
  public boolean isRunning() {
    try {
      this.jenkinsHttpClient.get("/");
      return true;
    } catch (IOException e) {
      throw prepareWingsException(e);
    }
  }

  @Override
  public Map<String, String> getEnvVars(String buildUrl) {
    if (isBlank(buildUrl)) {
      return new HashMap<>();
    }

    log.info("Retrieving environment variables for job {}", buildUrl);
    try {
      return HTimeLimiter.callInterruptible21(timeLimiter, Duration.ofSeconds(30), () -> {
        while (true) {
          String path = buildUrl;
          if (path.charAt(path.length() - 1) != '/') {
            path += '/';
          }
          path += "injectedEnvVars/api/json";

          String jsonString;
          try {
            jsonString = jenkinsHttpClient.get(path);
          } catch (HttpResponseException e) {
            if (e.getStatusCode() == 500 || ExceptionUtils.getMessage(e).contains(SERVER_ERROR)) {
              log.warn(
                  format("Error occurred while retrieving environment variables for job %s. Retrying", buildUrl), e);
              sleep(ofSeconds(1L));
              continue;
            } else {
              throw new InvalidRequestException("Failed to collect environment variables from Jenkins: " + path
                      + ".\nThis might be because 'Capture environment variables' is enabled in Jenkins step but EnvInject plugin is not installed in the Jenkins instance.",
                  USER);
            }
          }

          DocumentContext documentContext = JsonUtils.parseJson(jsonString);
          Map<String, String> envVars = documentContext.read("$['envMap']");
          if (isEmpty(envVars)) {
            envVars = new HashMap<>();
          } else {
            // NOTE: Removing environment variables where keys contain '.'. Storing and retrieving these keys is
            // throwing error with MongoDB and might also cause problems with expression evaluation as '.' is used as a
            // separator there.
            envVars = envVars.entrySet()
                          .stream()
                          .filter(entry -> !entry.getKey().contains("."))
                          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
          }

          log.info("Retrieving environment variables for job {} success", buildUrl);
          return envVars;
        }
      });
    } catch (Exception e) {
      throw new ArtifactServerException(
          "Failure in fetching environment variables for job: " + ExceptionUtils.getMessage(e), e, USER);
    }
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

  private Pair<String, InputStream> downloadArtifactFromABuild(Build build, String artifactpathRegex)
      throws IOException, URISyntaxException {
    if (build == null) {
      return null;
    }
    Pattern pattern = Pattern.compile(artifactpathRegex.replace(".", "\\.").replace("?", ".?").replace("*", ".*?"));
    BuildWithDetails buildWithDetails = build.details();
    Optional<Artifact> artifactOpt = buildWithDetails.getArtifacts()
                                         .stream()
                                         .filter(artifact -> pattern.matcher(artifact.getRelativePath()).find())
                                         .findFirst();
    if (artifactOpt.isPresent()) {
      return ImmutablePair.of(artifactOpt.get().getFileName(), buildWithDetails.downloadArtifact(artifactOpt.get()));
    } else {
      return null;
    }
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

  private HttpClientBuilder getUnSafeBuilder() {
    RequestConfig.Builder requestBuilder = RequestConfig.custom();
    requestBuilder.setConnectTimeout(150 * 1000);
    requestBuilder.setConnectionRequestTimeout(150 * 1000);

    HttpClientBuilder builder = HttpClientBuilder.create();
    builder.setDefaultRequestConfig(requestBuilder.build());
    try {
      // Set ssl context
      builder.setSSLContext(Http.getSslContext());
      // Create all-trusting host name verifier
      HostnameVerifier allHostsValid = (s, sslSession) -> true;
      builder.setSSLHostnameVerifier(allHostsValid);
    } catch (Exception ex) {
      log.warn("Installing trust managers");
    }
    return builder;
  }

  public long getFileSize(String jobName, String buildNo, String artifactPath) {
    long size;
    try {
      Pair<String, InputStream> fileInfo = downloadArtifact(jobName, buildNo, artifactPath);
      if (fileInfo == null) {
        throw new InvalidArtifactServerException(format("Failed to get file size for artifact: %s", artifactPath));
      }
      size = getInputStreamSize(fileInfo.getRight());
      fileInfo.getRight().close();
    } catch (IOException | URISyntaxException e) {
      throw new InvalidArtifactServerException(ExceptionUtils.getMessage(e), e);
    }
    log.info(format("Computed file size: [%d] bytes for artifact Path: %s", size, artifactPath));
    return size;
  }

  /**
   * Configures new executable property for Queue item
   *
   * @param queueItem      the queue item
   * @param buildUrl       the build URL
   */
  private void configureExecutable(QueueItem queueItem, String buildUrl) {
    Executable executable = new Executable();
    executable.setUrl(buildUrl);
    executable.setNumber(queueItem.getExecutable().getNumber());
    queueItem.setExecutable(executable);
  }

  /**
   * Form and returns new build url from URL, job path and job name
   *
   * @param url          the URL
   * @param jobPath      the job path
   * @param jobNumber    the job number
   * @return build url.
   */
  private String getBuildUrl(String url, String jobPath, String jobNumber) {
    if (url.endsWith("/")) {
      url = url.substring(0, url.length() - 1);
    }

    return url.concat(getJenkinsJobPath(jobPath)).concat(jobNumber).concat("/");
  }

  /**
   * Creates build with new url and number
   *
   * @param build          existing build with Jenkins master URL
   * @param buildUrl       build url with Jenkins connector URL
   * @return new build.
   */
  private Build createBuild(Build build, String buildUrl) {
    Build newBuild = new Build(build.getNumber(), buildUrl);
    newBuild.setClient(jenkinsHttpClient);
    return newBuild;
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
