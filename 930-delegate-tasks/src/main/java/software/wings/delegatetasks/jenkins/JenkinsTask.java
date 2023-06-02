/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.ExecutionContext.DELEGATE;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.dto.Log.Builder.aLog;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidCredentialsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;

import software.wings.beans.JenkinsConfig;
import software.wings.beans.JenkinsSubTaskType;
import software.wings.beans.command.JenkinsTaskParams;
import software.wings.beans.dto.Log;
import software.wings.helpers.ext.jenkins.Jenkins;
import software.wings.helpers.ext.jenkins.model.CustomBuildWithDetails;
import software.wings.service.impl.jenkins.JenkinsUtils;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.sm.states.JenkinsExecutionResponse;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.BuildResult;
import com.offbytwo.jenkins.model.BuildWithDetails;
import com.offbytwo.jenkins.model.QueueReference;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.conn.ConnectTimeoutException;

/**
 * Created by rishi on 12/14/16.
 */
@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class JenkinsTask extends AbstractDelegateRunnableTask {
  @Inject private EncryptionService encryptionService;
  @Inject private DelegateLogService logService;
  @Inject private JenkinsUtils jenkinsUtil;
  @Inject private TimeLimiter timeLimiter;
  @Inject @Named("jenkinsExecutor") private ExecutorService jenkinsExecutor;
  private static final int MAX_RETRY = 5;

  public JenkinsTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> postExecute, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, postExecute, preExecute);
  }

  @Override
  public JenkinsExecutionResponse run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public JenkinsExecutionResponse run(Object[] parameters) {
    return run((JenkinsTaskParams) parameters[0]);
  }

  public JenkinsExecutionResponse run(JenkinsTaskParams jenkinsTaskParams) {
    return executeInternal(jenkinsTaskParams);
  }

  private ExecutionStatus executeStartTask(JenkinsTaskParams jenkinsTaskParams,
      JenkinsExecutionResponse jenkinsExecutionResponse, JenkinsConfig jenkinsConfig, Jenkins jenkins, String msg)
      throws IOException {
    ExecutionStatus executionStatus = ExecutionStatus.SUCCESS;
    log.info("In Jenkins Task Triggering Job {}", jenkinsTaskParams.getJobName());
    logService.save(getAccountId(),
        constructLog(jenkinsTaskParams.getActivityId(), jenkinsTaskParams.getUnitName(), jenkinsTaskParams.getAppId(),
            LogLevel.INFO, "Triggering Jenkins Job : " + jenkinsTaskParams.getJobName(), RUNNING));

    QueueReference queueReference = jenkins.trigger(jenkinsTaskParams.getJobName(), jenkinsTaskParams);
    String queueItemUrl = queueReference != null ? queueReference.getQueueItemUrlPart() : null;

    // Check if jenkins job start is successful
    if (queueReference != null && isNotEmpty(queueItemUrl)) {
      if (jenkinsConfig.isUseConnectorUrlForJobExecution()) {
        queueItemUrl = updateQueueItemUrl(queueItemUrl, jenkinsConfig.getJenkinsUrl());
        queueReference = createQueueReference(queueItemUrl);
      }

      log.info("Triggered Job successfully with queued Build URL {} ", queueItemUrl);

      jenkinsExecutionResponse.setQueuedBuildUrl(queueItemUrl);

      logService.save(getAccountId(),
          constructLog(jenkinsTaskParams.getActivityId(), jenkinsTaskParams.getUnitName(), jenkinsTaskParams.getAppId(),
              LogLevel.INFO,
              "Triggered Job successfully with queued Build URL : " + queueItemUrl + " and remaining Time (sec): "
                  + (jenkinsTaskParams.getTimeout() - (System.currentTimeMillis() - jenkinsTaskParams.getStartTs()))
                      / 1000,
              RUNNING));
    } else {
      log.error("The Job was not triggered successfully with queued Build URL {} ", queueItemUrl);
      executionStatus = ExecutionStatus.FAILED;
      jenkinsExecutionResponse.setErrorMessage(msg);
    }

    Build jenkinsBuild = waitForJobToStartExecution(jenkins, queueReference, jenkinsConfig);
    jenkinsExecutionResponse.setBuildNumber(String.valueOf(jenkinsBuild.getNumber()));
    jenkinsExecutionResponse.setJobUrl(jenkinsBuild.getUrl());
    return executionStatus;
  }

  private ExecutionStatus executePollTask(JenkinsTaskParams jenkinsTaskParams,
      JenkinsExecutionResponse jenkinsExecutionResponse, JenkinsConfig jenkinsConfig, Jenkins jenkins, String msg)
      throws IOException, InterruptedException {
    jenkinsExecutionResponse.setSubTaskType(JenkinsSubTaskType.POLL_TASK);
    jenkinsExecutionResponse.setActivityId(jenkinsTaskParams.getActivityId());
    ExecutionStatus executionStatus = ExecutionStatus.SUCCESS;

    // Get jenkins build from queued URL
    String queuedBuildUrl = jenkinsTaskParams.getQueuedBuildUrl();

    if (jenkinsConfig.isUseConnectorUrlForJobExecution()) {
      queuedBuildUrl = updateQueueItemUrl(queuedBuildUrl, jenkinsConfig.getJenkinsUrl());
    }

    log.info("The Jenkins queued url {} and retrieving build information. {}", queuedBuildUrl, msg);
    Build jenkinsBuild = jenkins.getBuild(new QueueReference(queuedBuildUrl), jenkinsConfig);
    if (jenkinsBuild == null) {
      log.error(
          "Error occurred while retrieving the build {} status.  Job might have been deleted between poll intervals",
          queuedBuildUrl);
      logService.save(getAccountId(),
          constructLog(jenkinsTaskParams.getActivityId(), jenkinsTaskParams.getUnitName(), jenkinsTaskParams.getAppId(),
              LogLevel.INFO, "Failed to get the build status " + queuedBuildUrl, FAILURE));
      jenkinsExecutionResponse.setErrorMessage(
          "Failed to get the build status. Job might have been deleted between poll intervals.");
      jenkinsExecutionResponse.setExecutionStatus(ExecutionStatus.FAILED);
      executionStatus = ExecutionStatus.FAILED;
      return executionStatus;
    }

    jenkinsExecutionResponse.setBuildNumber(String.valueOf(jenkinsBuild.getNumber()));
    jenkinsExecutionResponse.setJobUrl(jenkinsBuild.getUrl());

    logService.save(getAccountId(),
        constructLog(jenkinsTaskParams.getActivityId(), jenkinsTaskParams.getUnitName(), jenkinsTaskParams.getAppId(),
            LogLevel.INFO,
            "Waiting for Jenkins task completion. Remaining time (sec): "
                + (jenkinsTaskParams.getTimeout() - (System.currentTimeMillis() - jenkinsTaskParams.getStartTs()))
                    / 1000,
            RUNNING));

    BuildWithDetails jenkinsBuildWithDetails =
        waitForJobExecutionToFinish(jenkinsBuild, jenkinsTaskParams.getActivityId(), jenkinsTaskParams.getUnitName(),
            jenkinsTaskParams.getAppId(), jenkinsConfig);
    jenkinsExecutionResponse.setJobUrl(jenkinsBuildWithDetails.getUrl());

    if (jenkinsTaskParams.isInjectEnvVars()) {
      logService.save(getAccountId(),
          constructLog(jenkinsTaskParams.getActivityId(), jenkinsTaskParams.getUnitName(), jenkinsTaskParams.getAppId(),
              LogLevel.INFO, "Collecting environment variables for Jenkins task", RUNNING));

      try {
        jenkinsExecutionResponse.setEnvVars(jenkins.getEnvVars(jenkinsBuildWithDetails.getUrl()));
      } catch (WingsException e) {
        logService.save(getAccountId(),
            constructLog(jenkinsTaskParams.getActivityId(), jenkinsTaskParams.getUnitName(),
                jenkinsTaskParams.getAppId(), LogLevel.ERROR,
                (String) e.getParams().getOrDefault("message", "Failed to collect environment variables from Jenkins"),
                FAILURE));
        throw e;
      }
    }

    logService.save(getAccountId(),
        constructLog(jenkinsTaskParams.getActivityId(), jenkinsTaskParams.getUnitName(), jenkinsTaskParams.getAppId(),
            LogLevel.INFO, "Jenkins task execution complete", SUCCESS));

    BuildResult buildResult = jenkinsBuildWithDetails.getResult();
    jenkinsExecutionResponse.setJenkinsResult(buildResult.toString());
    jenkinsExecutionResponse.setBuildNumber(String.valueOf(jenkinsBuildWithDetails.getNumber()));
    jenkinsExecutionResponse.setDescription(jenkinsBuildWithDetails.getDescription());
    jenkinsExecutionResponse.setBuildDisplayName(jenkinsBuildWithDetails.getDisplayName());
    jenkinsExecutionResponse.setBuildFullDisplayName(jenkinsBuildWithDetails.getFullDisplayName());

    try {
      jenkinsExecutionResponse.setJobParameters(jenkinsBuildWithDetails.getParameters());
    } catch (Exception e) { // cause buildWithDetails.getParameters() can throw NPE, unexpected exception
      log.error("Error occurred while retrieving build parameters for build number {}",
          jenkinsBuildWithDetails.getNumber(), e);
      jenkinsExecutionResponse.setErrorMessage(ExceptionUtils.getMessage(e));
    }

    if (buildResult != BuildResult.SUCCESS
        && (buildResult != BuildResult.UNSTABLE || !jenkinsTaskParams.isUnstableSuccess())) {
      executionStatus = ExecutionStatus.FAILED;
    }
    return executionStatus;
  }

  private JenkinsExecutionResponse executeInternal(JenkinsTaskParams jenkinsTaskParams) {
    JenkinsExecutionResponse jenkinsExecutionResponse = new JenkinsExecutionResponse();
    ExecutionStatus executionStatus = ExecutionStatus.SUCCESS;

    JenkinsConfig jenkinsConfig = jenkinsTaskParams.getJenkinsConfig();
    encryptionService.decrypt(jenkinsConfig, jenkinsTaskParams.getEncryptedDataDetails(), false);
    Jenkins jenkins = jenkinsUtil.getJenkins(jenkinsConfig);
    String msg = "Error occurred while starting Jenkins task\n";
    switch (jenkinsTaskParams.getSubTaskType()) {
      case START_TASK:
        try {
          if (jenkinsTaskParams.isTimeoutSupported()) {
            executionStatus =
                HTimeLimiter.callInterruptible(timeLimiter, Duration.ofMillis(jenkinsTaskParams.getTimeout()),
                    () -> executeStartTask(jenkinsTaskParams, jenkinsExecutionResponse, jenkinsConfig, jenkins, msg));
          } else {
            executionStatus =
                executeStartTask(jenkinsTaskParams, jenkinsExecutionResponse, jenkinsConfig, jenkins, msg);
          }
        } catch (TimeoutException e) {
          logService.save(getAccountId(),
              constructLog(jenkinsTaskParams.getActivityId(), jenkinsTaskParams.getUnitName(),
                  jenkinsTaskParams.getAppId(), LogLevel.ERROR, msg + e.toString(), FAILURE));
          executionStatus = ExecutionStatus.FAILED;
          jenkinsExecutionResponse.setErrorMessage(ExceptionUtils.getMessage(e));
          jenkinsExecutionResponse.setTimeoutError(true);
        } catch (WingsException e) {
          logService.save(getAccountId(),
              constructLog(jenkinsTaskParams.getActivityId(), jenkinsTaskParams.getUnitName(),
                  jenkinsTaskParams.getAppId(), LogLevel.ERROR, msg + e.toString(), FAILURE));
          ExceptionLogger.logProcessedMessages(e, DELEGATE, log);
          executionStatus = ExecutionStatus.FAILED;
          jenkinsExecutionResponse.setErrorMessage(ExceptionUtils.getMessage(e));
        } catch (Exception e) {
          logService.save(getAccountId(),
              constructLog(jenkinsTaskParams.getActivityId(), jenkinsTaskParams.getUnitName(),
                  jenkinsTaskParams.getAppId(), LogLevel.ERROR, msg + e.toString(), FAILURE));
          log.error(msg, e);
          executionStatus = ExecutionStatus.FAILED;
          jenkinsExecutionResponse.setErrorMessage(ExceptionUtils.getMessage(e));
        }
        jenkinsExecutionResponse.setExecutionStatus(executionStatus);
        jenkinsExecutionResponse.setSubTaskType(JenkinsSubTaskType.START_TASK);
        jenkinsExecutionResponse.setActivityId(jenkinsTaskParams.getActivityId());
        return jenkinsExecutionResponse;

      case POLL_TASK:
        try {
          if (jenkinsTaskParams.isTimeoutSupported()) {
            executionStatus =
                HTimeLimiter.callInterruptible(timeLimiter, Duration.ofMinutes(jenkinsTaskParams.getTimeout()),
                    () -> executePollTask(jenkinsTaskParams, jenkinsExecutionResponse, jenkinsConfig, jenkins, msg));
          } else {
            executionStatus = executePollTask(jenkinsTaskParams, jenkinsExecutionResponse, jenkinsConfig, jenkins, msg);
          }
        } catch (TimeoutException e) {
          logService.save(getAccountId(),
              constructLog(jenkinsTaskParams.getActivityId(), jenkinsTaskParams.getUnitName(),
                  jenkinsTaskParams.getAppId(), LogLevel.ERROR, msg + e.toString(), FAILURE));
          executionStatus = ExecutionStatus.FAILED;
          jenkinsExecutionResponse.setErrorMessage(ExceptionUtils.getMessage(e));
          jenkinsExecutionResponse.setTimeoutError(true);
        } catch (WingsException e) {
          ExceptionLogger.logProcessedMessages(e, DELEGATE, log);
          executionStatus = ExecutionStatus.FAILED;
          jenkinsExecutionResponse.setErrorMessage(ExceptionUtils.getMessage(e));
        } catch (Exception e) {
          log.error("Error occurred while running Jenkins task", e);
          executionStatus = ExecutionStatus.FAILED;
          jenkinsExecutionResponse.setErrorMessage(ExceptionUtils.getMessage(e));
        }
        break;
      default:
        jenkinsExecutionResponse.setExecutionStatus(ExecutionStatus.ERROR);
        throw new InvalidRequestException("Unhandled case for Jenkins Sub task, neither start nor poll sub task.");
    }
    jenkinsExecutionResponse.setExecutionStatus(executionStatus);
    return jenkinsExecutionResponse;
  }

  private BuildWithDetails waitForJobExecutionToFinish(Build jenkinsBuild, String activityId, String unitName,
      String appId, JenkinsConfig jenkinsConfig) throws IOException {
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

        if (jenkinsConfig.isUseConnectorUrlForJobExecution()) {
          jenkinsBuildWithDetails.setUrl(buildUrl);
        }

        final CustomBuildWithDetails finalJenkinsBuildWithDetails = jenkinsBuildWithDetails;

        saveConsoleLogs = jenkinsExecutor.submit(() -> {
          saveConsoleLogsAsync(
              jenkinsBuild, finalJenkinsBuildWithDetails, consoleLogsSent, activityId, unitName, appId);
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

  private Log constructLog(String activityId, String stateName, String appId, LogLevel logLevel, String logLine,
      CommandExecutionStatus commandExecutionStatus) {
    return aLog()
        .activityId(activityId)
        .commandUnitName(stateName)
        .appId(appId)
        .logLevel(logLevel)
        .logLine(logLine)
        .executionResult(commandExecutionStatus)
        .build();
  }

  private void saveConsoleLogs(BuildWithDetails jenkinsBuildWithDetails, AtomicInteger consoleLogsAlreadySent,
      String activityId, String stateName, CommandExecutionStatus commandExecutionStatus, String appId)
      throws IOException {
    String consoleOutputText = jenkinsBuildWithDetails.getConsoleOutputText();
    if (isNotBlank(consoleOutputText)) {
      String[] consoleLines = consoleOutputText.split("\r\n");
      Arrays.stream(consoleLines, consoleLogsAlreadySent.get(), consoleLines.length)
          .map(line
              -> aLog()
                     .activityId(activityId)
                     .commandUnitName(stateName)
                     .appId(appId)
                     .logLevel(LogLevel.INFO)
                     .logLine(line)
                     .executionResult(commandExecutionStatus)
                     .build())
          .forEachOrdered(logObject -> {
            logService.save(getAccountId(), logObject);
            consoleLogsAlreadySent.incrementAndGet();
          });
    }
  }

  private void saveConsoleLogsAsync(Build jenkinsBuild, BuildWithDetails jenkinsBuildWithDetails,
      AtomicInteger consoleLogsSent, String activityId, String stateName, String appId) throws HttpResponseException {
    try {
      saveConsoleLogs(jenkinsBuildWithDetails, consoleLogsSent, activityId, stateName, RUNNING, appId);
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

  private Build waitForJobToStartExecution(
      Jenkins jenkins, QueueReference queueReference, JenkinsConfig jenkinsConfig) {
    Build jenkinsBuild = null;
    int retry = 0;
    do {
      log.info(
          "Waiting for job {} to start execution with URL {}", queueReference, queueReference.getQueueItemUrlPart());
      sleep(Duration.ofSeconds(1));
      try {
        jenkinsBuild = jenkins.getBuild(queueReference, jenkinsConfig);
        if (jenkinsBuild != null) {
          log.info("Job started and Build No {}", jenkinsBuild.getNumber());
        }
      } catch (IOException e) {
        log.error("Error occurred while waiting for Job to start execution.", e);
        if (e instanceof HttpResponseException) {
          if (((HttpResponseException) e).getStatusCode() == 401) {
            throw new InvalidCredentialsException("Invalid Jenkins credentials", WingsException.USER);
          } else if (((HttpResponseException) e).getStatusCode() == 403) {
            throw new UnauthorizedException("User not authorized to access jenkins", WingsException.USER);
          } else if (((HttpResponseException) e).getStatusCode() == 500) {
            log.info("Failed to retrieve job details at url {}, Retrying (retry count {})  ",
                queueReference.getQueueItemUrlPart(), retry);
            if (retry < MAX_RETRY) {
              retry++;
              continue;
            } else {
              throw new GeneralException(String.format(
                  "Error retrieving job details at url %s: %s", queueReference.getQueueItemUrlPart(), e.getMessage()));
            }
          }
          throw new GeneralException(e.getMessage());
        }
      }
    } while (jenkinsBuild == null);
    return jenkinsBuild;
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
}
