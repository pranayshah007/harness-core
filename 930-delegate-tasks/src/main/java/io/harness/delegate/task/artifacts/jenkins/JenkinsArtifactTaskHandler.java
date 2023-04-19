/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.jenkins;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ExecutionContext.DELEGATE;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.dto.Log.Builder.aLog;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.artifacts.comparator.BuildDetailsComparatorDescending;
import io.harness.artifacts.jenkins.beans.JenkinsInternalConfig;
import io.harness.artifacts.jenkins.client.JenkinsClient;
import io.harness.artifacts.jenkins.client.JenkinsCustomServer;
import io.harness.artifacts.jenkins.service.JenkinsRegistryService;
import io.harness.artifacts.jenkins.service.JenkinsRegistryUtils;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.mappers.JenkinsRequestResponseMapper;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.jenkins.JenkinsBuildTaskNGResponse;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.helpers.ext.jenkins.model.CustomBuildWithDetails;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.offbytwo.jenkins.model.Artifact;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.BuildResult;
import com.offbytwo.jenkins.model.BuildWithDetails;
import com.offbytwo.jenkins.model.JobWithDetails;
import com.offbytwo.jenkins.model.QueueReference;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpResponseException;
import org.apache.http.conn.ConnectTimeoutException;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class JenkinsArtifactTaskHandler extends DelegateArtifactTaskHandler<JenkinsArtifactDelegateRequest> {
  private static final int ARTIFACT_RETENTION_SIZE = 25;
  private static final int MAX_RETRY = 5;
  private final SecretDecryptionService secretDecryptionService;
  private final JenkinsRegistryService jenkinsRegistryService;
  @Inject private JenkinsRegistryUtils jenkinsRegistryUtils;
  @Inject @Named("jenkinsExecutor") private ExecutorService jenkinsExecutor;

  @Override
  public ArtifactTaskExecutionResponse validateArtifactServer(JenkinsArtifactDelegateRequest attributesRequest) {
    boolean isServerValidated = jenkinsRegistryService.validateCredentials(
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(attributesRequest));
    return ArtifactTaskExecutionResponse.builder().isArtifactServerValid(isServerValidated).build();
  }

  @Override
  public ArtifactTaskExecutionResponse getJob(JenkinsArtifactDelegateRequest artifactDelegateRequest) {
    List<JobDetails> jobDetails =
        jenkinsRegistryService.getJobs(JenkinsRequestResponseMapper.toJenkinsInternalConfig(artifactDelegateRequest),
            artifactDelegateRequest.getParentJobName());
    return ArtifactTaskExecutionResponse.builder().jobDetails(jobDetails).build();
  }

  @Override
  public ArtifactTaskExecutionResponse getJobWithParamters(JenkinsArtifactDelegateRequest artifactDelegateRequest) {
    JobDetails jobDetails = jenkinsRegistryService.getJobWithParamters(
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(artifactDelegateRequest),
        artifactDelegateRequest.getJobName());
    List<JobDetails> details = new ArrayList<>();
    details.add(jobDetails);
    return ArtifactTaskExecutionResponse.builder().jobDetails(details).build();
  }

  @Override
  public ArtifactTaskExecutionResponse getArtifactPaths(JenkinsArtifactDelegateRequest artifactDelegateRequest) {
    try {
      JobWithDetails jobDetails = jenkinsRegistryService.getJobWithDetails(
          JenkinsRequestResponseMapper.toJenkinsInternalConfig(artifactDelegateRequest),
          artifactDelegateRequest.getJobName());
      List<String> artifactPath = Lists.newArrayList(jobDetails.getLastSuccessfulBuild()
                                                         .details()
                                                         .getArtifacts()
                                                         .stream()
                                                         .map(Artifact::getRelativePath)
                                                         .distinct()
                                                         .collect(toList()));
      return ArtifactTaskExecutionResponse.builder().artifactPath(artifactPath).build();
    } catch (WingsException e) {
      throw e;
    } catch (Exception ex) {
      throw new ArtifactServerException(
          "Error in artifact paths from jenkins server. Reason:" + ExceptionUtils.getMessage(ex), ex, USER);
    }
  }

  @Override
  public ArtifactTaskExecutionResponse getBuilds(JenkinsArtifactDelegateRequest attributesRequest) {
    List<BuildDetails> buildDetails =
        jenkinsRegistryService.getBuildsForJob(JenkinsRequestResponseMapper.toJenkinsInternalConfig(attributesRequest),
            attributesRequest.getJobName(), attributesRequest.getArtifactPaths(), ARTIFACT_RETENTION_SIZE);
    List<JenkinsArtifactDelegateResponse> jenkinsArtifactDelegateResponseList =
        buildDetails.stream()
            .sorted(new BuildDetailsComparatorDescending())
            .map(build -> JenkinsRequestResponseMapper.toJenkinsArtifactDelegateResponse(build, attributesRequest))
            .collect(Collectors.toList());
    return getSuccessTaskExecutionResponse(jenkinsArtifactDelegateResponseList, buildDetails);
  }

  @Override
  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(JenkinsArtifactDelegateRequest attributesRequest) {
    try {
      String jobName = URLEncoder.encode(attributesRequest.getJobName(), StandardCharsets.UTF_8.toString());
      if (isNotEmpty(attributesRequest.getBuildNumber())) {
        List<BuildDetails> buildDetails = jenkinsRegistryService.getBuildsForJob(
            JenkinsRequestResponseMapper.toJenkinsInternalConfig(attributesRequest), jobName,
            attributesRequest.getArtifactPaths(), ARTIFACT_RETENTION_SIZE);
        if (isNotEmpty(buildDetails)) {
          Pattern pattern = Pattern.compile(
              attributesRequest.getBuildNumber().replace(".", "\\.").replace("?", ".?").replace("*", ".*?"));
          buildDetails = buildDetails.stream()
                             .filter(buildDetail -> pattern.matcher(buildDetail.getNumber()).find())
                             .sorted(new BuildDetailsComparatorDescending())
                             .collect(toList());
        } else {
          throw NestedExceptionUtils.hintWithExplanationException(
              "Check if the version exist & check if the right connector chosen for fetching the build.",
              "Version not found ", new InvalidRequestException("Version not found"));
        }
        if (isNotEmpty(buildDetails) && buildDetails.get(0) != null) {
          JenkinsArtifactDelegateResponse jenkinsArtifactDelegateResponse =
              JenkinsRequestResponseMapper.toJenkinsArtifactDelegateResponse(buildDetails.get(0), attributesRequest);
          return getSuccessTaskExecutionResponse(Collections.singletonList(jenkinsArtifactDelegateResponse),
              Collections.singletonList(buildDetails.get(0)));
        } else {
          throw NestedExceptionUtils.hintWithExplanationException(
              "Check if the version exist & check if the right connector chosen for fetching the build.",
              "Version didn't matched ", new InvalidRequestException("Version didn't matched"));
        }
      }
      return getLastSuccessfulBuildForJob(attributesRequest, jobName);
    } catch (UnsupportedEncodingException e) {
      throw NestedExceptionUtils.hintWithExplanationException("JobName is not valid.",
          "Check the JobName provided is valid.", new UnsupportedEncodingException("JobName is not valid"));
    }
  }

  public ArtifactTaskExecutionResponse triggerBuild(
      JenkinsArtifactDelegateRequest attributesRequest, LogCallback executionLogCallback) {
    JenkinsBuildTaskNGResponse jenkinsBuildTaskNGResponse = new JenkinsBuildTaskNGResponse();
    ExecutionStatus executionStatus = ExecutionStatus.SUCCESS;
    String msg = "Error occurred while starting Jenkins task\n";
    try {
      JenkinsInternalConfig jenkinsInternalConfig =
          JenkinsRequestResponseMapper.toJenkinsInternalConfig(attributesRequest);
      QueueReference queueReference = jenkinsRegistryUtils.trigger(
          attributesRequest.getJobName(), jenkinsInternalConfig, attributesRequest.getJobParameter());
      String queueItemUrl = queueReference != null ? queueReference.getQueueItemUrlPart() : null;

      // Check if jenkins job start is successful
      if (queueReference != null && isNotEmpty(queueItemUrl)) {
        if (jenkinsInternalConfig.isUseConnectorUrlForJobExecution()) {
          queueItemUrl = updateQueueItemUrl(queueItemUrl, jenkinsInternalConfig.getJenkinsUrl());
          queueReference = createQueueReference(queueItemUrl);
        }
        log.info("Triggered Job successfully with queued Build URL {} ", queueItemUrl);
        jenkinsBuildTaskNGResponse.setQueuedBuildUrl(queueItemUrl);

        executionLogCallback.saveExecutionLog("Triggered Job successfully with queued Build URL : " + queueItemUrl
                + " and remaining Time (sec): "
                + (attributesRequest.getTimeout() - (System.currentTimeMillis() - attributesRequest.getStartTs()))
                    / 1000,
            LogLevel.INFO);
      } else {
        log.error("The Job was not triggered successfully with queued Build URL {} ", queueItemUrl);
        executionStatus = ExecutionStatus.FAILED;
        jenkinsBuildTaskNGResponse.setErrorMessage(msg);
        executionLogCallback.saveExecutionLog(
            "The Job was not triggered successfully with queued Build URL {} " + queueItemUrl, LogLevel.ERROR);
      }
      JenkinsCustomServer jenkinsServer = JenkinsClient.getJenkinsServer(jenkinsInternalConfig);
      Build jenkinsBuild = jenkinsRegistryUtils.waitForJobToStartExecution(queueReference, jenkinsInternalConfig);
      jenkinsBuildTaskNGResponse.setBuildNumber(String.valueOf(jenkinsBuild.getNumber()));
      jenkinsBuildTaskNGResponse.setJobUrl(jenkinsBuild.getUrl());
      executionLogCallback.saveExecutionLog(
          "The Job was build successfull. Build number " + jenkinsBuild.getNumber(), LogLevel.INFO);
    } catch (WingsException e) {
      executionLogCallback.saveExecutionLog(msg + e, LogLevel.ERROR);
      ExceptionLogger.logProcessedMessages(e, DELEGATE, log);
      throw e;
    } catch (IOException ex) {
      executionLogCallback.saveExecutionLog(msg + ex, LogLevel.ERROR);
      throw new InvalidRequestException("Failed to trigger the Jenkins Job" + ExceptionUtils.getMessage(ex), USER);
    } catch (URISyntaxException e) {
      executionLogCallback.saveExecutionLog("Invalid URI Syntax\n" + e, LogLevel.ERROR);
    } catch (Exception e) {
      executionLogCallback.saveExecutionLog(msg + e, LogLevel.ERROR);
      log.error(msg, e);
      executionStatus = ExecutionStatus.FAILED;
      jenkinsBuildTaskNGResponse.setErrorMessage(ExceptionUtils.getMessage(e));
      executionLogCallback.saveExecutionLog(msg + e, LogLevel.ERROR);
    }
    jenkinsBuildTaskNGResponse.setExecutionStatus(executionStatus);
    return ArtifactTaskExecutionResponse.builder().jenkinsBuildTaskNGResponse(jenkinsBuildTaskNGResponse).build();
  }

  public ArtifactTaskExecutionResponse pollTask(
      JenkinsArtifactDelegateRequest attributesRequest, LogCallback executionLogCallback) {
    JenkinsBuildTaskNGResponse jenkinsBuildTaskNGResponse = new JenkinsBuildTaskNGResponse();
    ExecutionStatus executionStatus = ExecutionStatus.SUCCESS;
    try {
      // Get jenkins build from queued URL
      String queuedBuildUrl = attributesRequest.getQueuedBuildUrl();
      JenkinsInternalConfig jenkinsInternalConfig =
          JenkinsRequestResponseMapper.toJenkinsInternalConfig(attributesRequest);

      if (jenkinsInternalConfig.isUseConnectorUrlForJobExecution()) {
        queuedBuildUrl = updateQueueItemUrl(queuedBuildUrl, jenkinsInternalConfig.getJenkinsUrl());
      }

      log.info("The Jenkins queued url {} and retrieving build information", queuedBuildUrl);
      Build jenkinsBuild = jenkinsRegistryUtils.getBuild(new QueueReference(queuedBuildUrl), jenkinsInternalConfig);
      if (jenkinsBuild == null) {
        log.error(
            "Error occurred while retrieving the build {} status.  Job might have been deleted between poll intervals",
            queuedBuildUrl);
        executionLogCallback.saveExecutionLog(
            "Failed to get the build status " + queuedBuildUrl, LogLevel.INFO, CommandExecutionStatus.FAILURE);
        jenkinsBuildTaskNGResponse.setErrorMessage(
            "Failed to get the build status. Job might have been deleted between poll intervals.");
        jenkinsBuildTaskNGResponse.setExecutionStatus(ExecutionStatus.FAILED);
        return ArtifactTaskExecutionResponse.builder().jenkinsBuildTaskNGResponse(jenkinsBuildTaskNGResponse).build();
      }

      jenkinsBuildTaskNGResponse.setBuildNumber(String.valueOf(jenkinsBuild.getNumber()));
      jenkinsBuildTaskNGResponse.setJobUrl(jenkinsBuild.getUrl());

      executionLogCallback.saveExecutionLog("Waiting for Jenkins task completion. Remaining time (sec): "
              + (attributesRequest.getTimeout() - (System.currentTimeMillis() - attributesRequest.getStartTs())) / 1000,
          LogLevel.INFO);

      BuildWithDetails jenkinsBuildWithDetails = waitForJobExecutionToFinish(
          jenkinsBuild, attributesRequest.getUnitName(), jenkinsInternalConfig, executionLogCallback);
      jenkinsBuildTaskNGResponse.setJobUrl(jenkinsBuildWithDetails.getUrl());

      executionLogCallback.saveExecutionLog("Collecting environment variables for Jenkins task", LogLevel.INFO);
      try {
        jenkinsBuildTaskNGResponse.setEnvVars(
            jenkinsRegistryUtils.getEnvVars(jenkinsBuildWithDetails.getUrl(), jenkinsInternalConfig));
      } catch (WingsException e) {
        executionLogCallback.saveExecutionLog(
            "Failed to collect environment variables from Jenkins: " + e.getMessage(), LogLevel.ERROR);
      }

      executionLogCallback.saveExecutionLog("Jenkins task execution complete ", LogLevel.INFO);

      BuildResult buildResult = jenkinsBuildWithDetails.getResult();
      jenkinsBuildTaskNGResponse.setJenkinsResult(buildResult.toString());
      jenkinsBuildTaskNGResponse.setBuildNumber(String.valueOf(jenkinsBuildWithDetails.getNumber()));
      jenkinsBuildTaskNGResponse.setDescription(jenkinsBuildWithDetails.getDescription());
      jenkinsBuildTaskNGResponse.setBuildDisplayName(jenkinsBuildWithDetails.getDisplayName());
      jenkinsBuildTaskNGResponse.setBuildFullDisplayName(jenkinsBuildWithDetails.getFullDisplayName());

      try {
        jenkinsBuildTaskNGResponse.setJobParameters(jenkinsBuildWithDetails.getParameters());
      } catch (Exception e) { // cause buildWithDetails.getParameters() can throw NPE, unexpected exception
        log.error("Error occurred while retrieving build parameters for build number {}",
            jenkinsBuildWithDetails.getNumber(), e);
        jenkinsBuildTaskNGResponse.setErrorMessage(ExceptionUtils.getMessage(e));
      }

      if (buildResult != BuildResult.SUCCESS
          && (buildResult != BuildResult.UNSTABLE || !attributesRequest.isUnstableStatusAsSuccess())) {
        executionStatus = ExecutionStatus.FAILED;
      }
      String consoleLogs = jenkinsRegistryUtils.getJenkinsConsoleLogs(
          jenkinsInternalConfig, attributesRequest.getJobName(), String.valueOf(jenkinsBuild.getNumber()));
      if (StringUtils.isNotBlank(consoleLogs)) {
        executionLogCallback.saveExecutionLog(consoleLogs, LogLevel.INFO);
      } else {
        executionLogCallback.saveExecutionLog("We could not fetch the logs from Jenkins", LogLevel.WARN);
      }
    } catch (WingsException e) {
      ExceptionLogger.logProcessedMessages(e, DELEGATE, log);
      executionStatus = ExecutionStatus.FAILED;
      jenkinsBuildTaskNGResponse.setErrorMessage(ExceptionUtils.getMessage(e));
    } catch (Exception e) {
      log.error("Error occurred while running Jenkins task", e);
      executionStatus = ExecutionStatus.FAILED;
      jenkinsBuildTaskNGResponse.setErrorMessage(ExceptionUtils.getMessage(e));
    }
    jenkinsBuildTaskNGResponse.setExecutionStatus(executionStatus);
    return ArtifactTaskExecutionResponse.builder().jenkinsBuildTaskNGResponse(jenkinsBuildTaskNGResponse).build();
  }

  private ArtifactTaskExecutionResponse getSuccessTaskExecutionResponse(
      List<JenkinsArtifactDelegateResponse> responseList, List<BuildDetails> buildDetails) {
    return ArtifactTaskExecutionResponse.builder()
        .artifactDelegateResponses(responseList)
        .buildDetails(buildDetails)
        .isArtifactSourceValid(true)
        .isArtifactServerValid(true)
        .build();
  }

  @Override
  public void decryptRequestDTOs(JenkinsArtifactDelegateRequest jenkinsArtifactDelegateRequest) {
    if (jenkinsArtifactDelegateRequest.getJenkinsConnectorDTO().getAuth() != null) {
      secretDecryptionService.decrypt(
          jenkinsArtifactDelegateRequest.getJenkinsConnectorDTO().getAuth().getCredentials(),
          jenkinsArtifactDelegateRequest.getEncryptedDataDetails());
    }
  }

  private QueueReference createQueueReference(String location) {
    return new QueueReference(location);
  }

  private String updateQueueItemUrl(String queueItemUrl, String jenkinsUrl) {
    if (jenkinsUrl.endsWith("/")) {
      jenkinsUrl = jenkinsUrl.substring(0, jenkinsUrl.length() - 1);
    }
    String[] queueItemUrlParts = queueItemUrl.split("/queue/");
    return jenkinsUrl.concat("/queue/").concat(queueItemUrlParts[1]);
  }

  private void saveLogs(LogCallback executionLogCallback, String message) {
    if (executionLogCallback != null) {
      executionLogCallback.saveExecutionLog(message);
    }
  }

  public BuildWithDetails waitForJobExecutionToFinish(Build jenkinsBuild, String unitName,
      JenkinsInternalConfig jenkinsInternalConfig, LogCallback logCallback) throws IOException {
    CustomBuildWithDetails jenkinsBuildWithDetails = null;
    AtomicInteger consoleLogsSent = new AtomicInteger();

    CustomBuildWithDetails customBuildWithDetails = new CustomBuildWithDetails(jenkinsBuild.details());
    String buildUrl = jenkinsBuild.getUrl();
    customBuildWithDetails.setUrl(buildUrl);

    do {
      log.info("Waiting for Job {} to finish execution", buildUrl);
      sleep(Duration.ofSeconds(5));
      Future<CustomBuildWithDetails> jenkinsBuildWithDetailsFuture = null;
      Future<Void> saveConsoleLogs = null;
      try {
        jenkinsBuildWithDetailsFuture = jenkinsExecutor.submit(customBuildWithDetails::details);
        jenkinsBuildWithDetails = jenkinsBuildWithDetailsFuture.get(180, TimeUnit.SECONDS);

        if (jenkinsInternalConfig.isUseConnectorUrlForJobExecution()) {
          jenkinsBuildWithDetails.setUrl(buildUrl);
        }

        final CustomBuildWithDetails finalJenkinsBuildWithDetails = jenkinsBuildWithDetails;

        saveConsoleLogs = jenkinsExecutor.submit(() -> {
          saveConsoleLogsAsync(jenkinsBuild, finalJenkinsBuildWithDetails, consoleLogsSent, unitName, logCallback);
          return null;
        });
        saveConsoleLogs.get(180, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.error("Thread interrupted while waiting for Job {} to finish execution. Reason {}. Retrying.", buildUrl,
            ExceptionUtils.getMessage(e));
      } catch (ExecutionException | TimeoutException e) {
        log.error("Exception occurred while waiting for Job {} to finish execution. Reason {}. Retrying.", buildUrl,
            ExceptionUtils.getMessage(e));
      } finally {
        if (jenkinsBuildWithDetailsFuture != null) {
          jenkinsBuildWithDetailsFuture.cancel(true);
        }
        if (saveConsoleLogs != null) {
          saveConsoleLogs.cancel(true);
        }
      }

    } while (jenkinsBuildWithDetails == null || jenkinsBuildWithDetails.isBuilding());
    log.info("Job {} execution completed. Status: {}", jenkinsBuildWithDetails.getNumber(),
        jenkinsBuildWithDetails.getResult());
    return jenkinsBuildWithDetails;
  }

  private void saveConsoleLogsAsync(Build jenkinsBuild, BuildWithDetails jenkinsBuildWithDetails,
      AtomicInteger consoleLogsSent, String stateName, LogCallback logCallback) throws HttpResponseException {
    try {
      saveConsoleLogs(jenkinsBuildWithDetails, consoleLogsSent, stateName, RUNNING, logCallback);
    } catch (SocketTimeoutException | ConnectTimeoutException e) {
      log.error("Timeout exception occurred while waiting for Job {} to finish execution. Reason {}. Retrying.",
          jenkinsBuild.getUrl(), ExceptionUtils.getMessage(e));
    } catch (HttpResponseException e) {
      if (e.getStatusCode() == 404) {
        log.error("Error occurred while waiting for Job {} to finish execution. Reason {}. Retrying.",
            jenkinsBuild.getUrl(), ExceptionUtils.getMessage(e), e);
        throw new HttpResponseException(e.getStatusCode(),
            "Job [" + jenkinsBuild.getUrl()
                + "] not found. Job might have been deleted from Jenkins Server between polling intervals");
      }
    } catch (IOException e) {
      log.error("Error occurred while waiting for Job {} to finish execution. Reason {}. Retrying.",
          jenkinsBuild.getUrl(), ExceptionUtils.getMessage(e));
    }
  }

  private void saveConsoleLogs(BuildWithDetails jenkinsBuildWithDetails, AtomicInteger consoleLogsAlreadySent,
      String stateName, CommandExecutionStatus commandExecutionStatus, LogCallback logCallback) throws IOException {
    String consoleOutputText = jenkinsBuildWithDetails.getConsoleOutputText();
    if (isNotBlank(consoleOutputText)) {
      String[] consoleLines = consoleOutputText.split("\r\n");
      Arrays.stream(consoleLines, consoleLogsAlreadySent.get(), consoleLines.length)
          .map(line
              -> aLog()
                     .commandUnitName(stateName)
                     .logLevel(LogLevel.INFO)
                     .logLine(line)
                     .executionResult(commandExecutionStatus)
                     .build())
          .forEachOrdered(logObject -> {
            logCallback.saveExecutionLog(logObject.getLogLine(), logObject.getLogLevel());
            consoleLogsAlreadySent.incrementAndGet();
          });
    }
  }

  private ArtifactTaskExecutionResponse getLastSuccessfulBuildForJob(
      JenkinsArtifactDelegateRequest attributesRequest, String jobName) {
    BuildDetails buildDetails = jenkinsRegistryService.getLastSuccessfulBuildForJob(
        JenkinsRequestResponseMapper.toJenkinsInternalConfig(attributesRequest), jobName,
        attributesRequest.getArtifactPaths());
    JenkinsArtifactDelegateResponse jenkinsArtifactDelegateResponse =
        JenkinsRequestResponseMapper.toJenkinsArtifactDelegateResponse(buildDetails, attributesRequest);
    return getSuccessTaskExecutionResponse(
        Collections.singletonList(jenkinsArtifactDelegateResponse), Collections.singletonList(buildDetails));
  }
}
