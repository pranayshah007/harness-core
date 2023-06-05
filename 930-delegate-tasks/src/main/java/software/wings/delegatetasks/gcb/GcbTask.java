/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.dto.Log.Builder.aLog;
import static software.wings.delegatetasks.GcbDelegateResponse.failedGcbTaskResponse;
import static software.wings.delegatetasks.GcbDelegateResponse.gcbDelegateResponseOf;
import static software.wings.delegatetasks.GcbDelegateResponse.timeoutGcbTaskResponse;
import static software.wings.sm.states.gcbconfigs.GcbRemoteBuildSpec.RemoteFileSource.BRANCH;

import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
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
import io.harness.delegate.task.artifacts.gcr.exceptions.GcbClientException;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.InterruptedRuntimeException;
import io.harness.exception.UnsupportedOperationException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.serializer.JsonUtils;

import software.wings.beans.command.GcbTaskParams;
import software.wings.beans.dto.Log;
import software.wings.beans.yaml.GitFetchFilesRequest;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.helpers.ext.gcb.GcbService;
import software.wings.helpers.ext.gcb.models.BuildOperationDetails;
import software.wings.helpers.ext.gcb.models.GcbBuildDetails;
import software.wings.helpers.ext.gcb.models.GcbTrigger;
import software.wings.helpers.ext.gcb.models.RepoSource;
import software.wings.helpers.ext.gcb.models.RepoSource.RepoSourceBuilder;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.yaml.GitClient;
import software.wings.sm.states.gcbconfigs.GcbOptions;
import software.wings.sm.states.gcbconfigs.GcbRemoteBuildSpec;
import software.wings.sm.states.gcbconfigs.GcbTriggerBuildSpec;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by rishi on 12/14/16.
 */
@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class GcbTask extends AbstractDelegateRunnableTask {
  @Inject private EncryptionService encryptionService;
  @Inject private DelegateLogService logService;
  @Inject private GcbService gcbService;
  @Inject private GitClient git;
  @Inject TimeLimiter timeLimiter;
  private final AtomicInteger alreadyLogged = new AtomicInteger(0); // move to taskParams

  public GcbTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> postExecute, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, postExecute, preExecute);
  }

  @Override
  public GcbDelegateResponse run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public GcbDelegateResponse run(Object[] parameters) {
    return run((GcbTaskParams) parameters[0]);
  }

  public GcbDelegateResponse run(GcbTaskParams params) {
    try {
      switch (params.getType()) {
        case FETCH_TRIGGERS:
          if (params.isTimeoutSupported()) {
            return HTimeLimiter.callInterruptible(
                timeLimiter, Duration.ofMillis(params.getTimeout()), () -> fetchTriggers(params));
          }
          return fetchTriggers(params);
        case START:
          if (params.isTimeoutSupported()) {
            return HTimeLimiter.callInterruptible(
                timeLimiter, Duration.ofMillis(params.getTimeout()), () -> startGcbBuild(params));
          }
          return startGcbBuild(params);
        case POLL:
          if (params.isTimeoutSupported()) {
            return HTimeLimiter.callInterruptible(
                timeLimiter, Duration.ofMillis(params.getTimeout()), () -> pollGcbBuild(params));
          }
          return pollGcbBuild(params);
        case CANCEL:
          if (params.isTimeoutSupported()) {
            return HTimeLimiter.callInterruptible(
                timeLimiter, Duration.ofMillis(params.getTimeout()), () -> cancelBuild(params));
          }
          return cancelBuild(params);
        default:
          throw new UnsupportedOperationException(format("Unsupported TaskType: %s", params.getType()));
      }
    } catch (TimeoutException e) {
      log.warn("GCB_TASK - GCB task failed due to: ", e);
      return timeoutGcbTaskResponse(params, "Timed out while waiting for task to complete");
    } catch (Exception e) {
      log.warn("GCB_TASK - GCB task failed due to: ", e);
      return failedGcbTaskResponse(params, e.getMessage());
    }
  }

  private GcbDelegateResponse fetchTriggers(GcbTaskParams params) {
    GcbDelegateResponse response = new GcbDelegateResponse(ExecutionStatus.NEW, null, params, null, false);
    List<String> allTriggers = gcbService.getAllTriggers(params.getGcpConfig(), params.getEncryptedDataDetails())
                                   .stream()
                                   .map(GcbTrigger::getName)
                                   .collect(Collectors.toList());
    response.setTriggers(allTriggers);
    return response;
  }

  protected GcbDelegateResponse pollGcbBuild(final @NotNull GcbTaskParams params) {
    try {
      GcbBuildDetails build;
      do {
        sleep(Duration.ofSeconds(params.getPollFrequency()));
        build = gcbService.getBuild(params.getGcpConfig(), params.getEncryptedDataDetails(), params.getBuildId());
        String gcbOutput = gcbService.fetchBuildLogs(
            params.getGcpConfig(), params.getEncryptedDataDetails(), build.getLogsBucket(), params.getBuildId());

        saveConsoleLogs(alreadyLogged, params.getActivityId(), params.getUnitName(),
            build.getStatus().getCommandExecutionStatus(), params.getAppId(), gcbOutput);
      } while (build.isWorking());
      return gcbDelegateResponseOf(params, build);
    } catch (InterruptedRuntimeException e) {
      log.error("GCB_TASK - GCB poll task failed due to: ", e);
      return GcbDelegateResponse.interruptedGcbTask(params);
    }
  }

  protected GcbDelegateResponse startGcbBuild(final @NotNull GcbTaskParams params) {
    BuildOperationDetails buildOperationDetails = initiateBuild(params);
    GcbBuildDetails build = buildOperationDetails.getOperationMeta().getBuild();
    params.setBuildId(build.getId());
    params.setBuildName(buildOperationDetails.getName());
    return gcbDelegateResponseOf(params, build);
  }

  protected GcbDelegateResponse cancelBuild(final @NotNull GcbTaskParams params) {
    try {
      gcbService.cancelBuild(params.getGcpConfig(), params.getEncryptedDataDetails(), params.getBuildId());
    } catch (GcbClientException e) {
      log.error("GCB_TASK - Failed to cancel GCB build due to: ", e);
      return gcbDelegateResponseOf(
          params, gcbService.getBuild(params.getGcpConfig(), params.getEncryptedDataDetails(), params.getBuildId()));
    }
    sleep(Duration.ofSeconds(10));
    GcbBuildDetails build =
        gcbService.getBuild(params.getGcpConfig(), params.getEncryptedDataDetails(), params.getBuildId());
    return gcbDelegateResponseOf(params, build);
  }

  BuildOperationDetails initiateBuild(final @NotNull GcbTaskParams params) {
    final GcbOptions options = params.getGcbOptions();
    switch (options.getSpecSource()) {
      case INLINE:
        return buildWith(params, fromJsonSpec(options.getInlineSpec()));
      case REMOTE:
        return buildWith(params, fromJsonSpec(fetchSpecFromGit(params)));
      case TRIGGER:
        return triggerGcb(params);
      default:
        throw new UnsupportedOperationException("Gcb option " + options.getSpecSource() + " not supported");
    }
  }

  protected BuildOperationDetails triggerGcb(final @NotNull GcbTaskParams params) {
    final GcbOptions options = requireNonNull(params.getGcbOptions());
    RepoSource source = repoSourceOf(options.getTriggerSpec(), params.getSubstitutions());
    return gcbService.runTrigger(
        params.getGcpConfig(), params.getEncryptedDataDetails(), options.getTriggerSpec().getName(), source);
  }

  GcbBuildDetails fromJsonSpec(final String jsonBuildSpec) {
    return JsonUtils.asObject(jsonBuildSpec, GcbBuildDetails.class);
  }

  String fetchSpecFromGit(final @NotNull GcbTaskParams params) {
    encryptionService.decrypt(params.getGitConfig(), params.getEncryptedDataDetails(), false);
    final GcbRemoteBuildSpec repositorySpec =
        requireNonNull(requireNonNull(params.getGcbOptions(), "gcbOptions should not be null").getRepositorySpec(),
            "repositorySpec should not be null");
    GitFetchFilesResult result = git.fetchFilesByPath(params.getGitConfig(),
        GitFetchFilesRequest.builder()
            .gitConnectorId(repositorySpec.getGitConfigId())
            .filePaths(singletonList(repositorySpec.getFilePath()))
            .fileExtensions(singletonList("json"))
            .branch(repositorySpec.getSourceId())
            .useBranch(repositorySpec.getFileSource() == BRANCH)
            .commitId(repositorySpec.getSourceId())
            .recursive(false)
            .build(),
        false);
    return result.getFiles().get(0).getFileContent();
  }

  BuildOperationDetails buildWith(final @NotNull GcbTaskParams params, GcbBuildDetails buildDetails) {
    return gcbService.createBuild(params.getGcpConfig(), params.getEncryptedDataDetails(), buildDetails);
  }

  RepoSource repoSourceOf(
      @NotNull final GcbTriggerBuildSpec triggerSpec, @Nullable final Map<String, String> substitutions) {
    RepoSourceBuilder builder = RepoSource.builder().substitutions(substitutions);
    switch (triggerSpec.getSource()) {
      case TAG:
        return builder.tagName(triggerSpec.getSourceId()).build();
      case BRANCH:
        return builder.branchName(triggerSpec.getSourceId()).build();
      case COMMIT:
        return builder.commitSha(triggerSpec.getSourceId()).build();
      default:
        throw new UnsupportedOperationException("Trigger source " + triggerSpec.getSource() + " not supported");
    }
  }

  // similar to JenkinsTask#sameConsoleLogs(activityId, stateName, commandExecutionStatus, appId, consoleOutput)
  void saveConsoleLogs(AtomicInteger logsCount, String activityId, String stateName,
      CommandExecutionStatus commandExecutionStatus, String appId, String consoleOutput) {
    if (isNotBlank(consoleOutput)) {
      String[] consoleLines = consoleOutput.split("\r?\n");
      int offset = alreadyLogged.get();
      for (int i = offset; i < consoleLines.length; i++) {
        Log logObject = aLog()
                            .activityId(activityId)
                            .commandUnitName(stateName)
                            .appId(appId)
                            .logLevel(INFO)
                            .logLine(consoleLines[i])
                            .executionResult(commandExecutionStatus)
                            .build();
        logService.save(getAccountId(), logObject);
        logsCount.incrementAndGet();
      }
    }
  }
}
