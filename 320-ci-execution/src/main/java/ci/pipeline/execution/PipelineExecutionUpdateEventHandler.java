/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package ci.pipeline.execution;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.PmsCommonConstants.AUTO_ABORT_PIPELINE_THROUGH_TRIGGER;
import static io.harness.pms.execution.utils.StatusUtils.isFinalStatus;
import static io.harness.steps.StepUtils.buildAbstractions;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ci.CICleanupTaskParams;
import io.harness.encryption.Scope;
import io.harness.logserviceclient.CILogServiceUtils;
import io.harness.logstreaming.LogStreamingHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.states.codebase.CodeBaseTaskStep;
import io.harness.steps.StepUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class PipelineExecutionUpdateEventHandler implements OrchestrationEventHandler {
  @Inject private GitBuildStatusUtility gitBuildStatusUtility;
  @Inject private StageCleanupUtility stageCleanupUtility;
  @Inject private CILogServiceUtils ciLogServiceUtils;

  private final int MAX_ATTEMPTS = 3;
  @Inject @Named("ciEventHandlerExecutor") private ExecutorService executorService;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @Override
  public void handleEvent(OrchestrationEvent event) {
    Ambiance ambiance = event.getAmbiance();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    Level level = AmbianceUtils.obtainCurrentLevel(ambiance);
    Status status = event.getStatus();
    executorService.submit(() -> {
      sendGitStatus(level, ambiance, status, event, accountId);
      sendCleanupRequest(level, ambiance, status, accountId);
    });
  }

  private void sendCleanupRequest(Level level, Ambiance ambiance, Status status, String accountId) {
    try {
      RetryPolicy<Object> retryPolicy = getRetryPolicy(format("[Retrying failed call to clean pod attempt: {}"),
          format("Failed to clean pod after retrying {} times"));

      Failsafe.with(retryPolicy).run(() -> {
        if (level.getStepType().getStepCategory() == StepCategory.STAGE && isFinalStatus(status)) {
          CICleanupTaskParams ciCleanupTaskParams = stageCleanupUtility.buildAndfetchCleanUpParameters(ambiance);

          log.info("Received event with status {} to clean planExecutionId {}, stage {}", status,
              ambiance.getPlanExecutionId(), level.getIdentifier());
          List<TaskSelector> taskSelectors = stageCleanupUtility.fetchDelegateSelector(ambiance);

          Map<String, String> abstractions = buildAbstractions(ambiance, Scope.PROJECT);
          DelegateTaskRequest delegateTaskRequest =
              DelegateTaskRequest.builder()
                  .accountId(accountId)
                  .taskSelectors(taskSelectors.stream().map(TaskSelector::getSelector).collect(Collectors.toList()))
                  .taskSetupAbstractions(abstractions)
                  .executionTimeout(java.time.Duration.ofSeconds(900))
                  .taskType("CI_CLEANUP")
                  .taskParameters(ciCleanupTaskParams)
                  .taskDescription("CI cleanup pod task")
                  .build();

          String taskId = delegateGrpcClientWrapper.submitAsyncTask(delegateTaskRequest, Duration.ZERO);
          log.info("Submitted cleanup request with taskId {} for planExecutionId {}, stage {}", taskId,
              ambiance.getPlanExecutionId(), level.getIdentifier());

          // If there are any leftover logs still in the stream (this might be possible in specific cases
          // like in k8s node pressure evictions) - then this is where we move all of them to blob storage.
          ciLogServiceUtils.closeLogStream(AmbianceUtils.getAccountId(ambiance), getLogKey(ambiance), true, true);
        }
      });
    } catch (Exception ex) {
      log.error("Failed to send cleanup call for node {}", level.getRuntimeId(), ex);
    }
  }

  private String getLogKey(Ambiance ambiance) {
    LinkedHashMap<String, String> logAbstractions = StepUtils.generateLogAbstractions(ambiance);
    return LogStreamingHelper.generateLogBaseKey(logAbstractions);
  }

  private void sendGitStatus(
      Level level, Ambiance ambiance, Status status, OrchestrationEvent event, String accountId) {
    try {
      if (gitBuildStatusUtility.shouldSendStatus(level.getStepType().getStepCategory())
          || gitBuildStatusUtility.isCodeBaseStepSucceeded(level, status)) {
        log.info("Received event with status {} to update git status for stage {}, planExecutionId {}", status,
            level.getIdentifier(), ambiance.getPlanExecutionId());
        if (isAutoAbortThroughTrigger(event)) {
          log.info("Skipping updating Git status as execution was Auto aborted by trigger due to newer execution");
        } else {
          if (level.getStepType().getStepCategory() == StepCategory.STAGE) {
            gitBuildStatusUtility.sendStatusToGit(status, event.getResolvedStepParameters(), ambiance, accountId);
          } else if (level.getStepType().getType().equals(CodeBaseTaskStep.STEP_TYPE.getType())) {
            // It sends Running if codebase step successfully fetched commit sha via api token
            gitBuildStatusUtility.sendStatusToGit(
                Status.RUNNING, event.getResolvedStepParameters(), ambiance, accountId);
          }
        }
      }
    } catch (Exception ex) {
      log.error("Failed to send git status update task for node {}, planExecutionId {}", level.getRuntimeId(),
          ambiance.getPlanExecutionId(), ex);
    }
  }

  // When trigger has "Auto Abort Prev Executions" ebanled, it will abort prev running execution and start a new one.
  // e.g. pull_request  event for same PR
  private boolean isAutoAbortThroughTrigger(OrchestrationEvent event) {
    if (isEmpty(event.getTags())) {
      return false;
    }

    boolean isAutoAbort = false;
    if (event.getTags().contains(AUTO_ABORT_PIPELINE_THROUGH_TRIGGER)) {
      isAutoAbort = true;
    }

    return isAutoAbort;
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withBackoff(5, 60, ChronoUnit.SECONDS)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}
