/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.PmsCommonConstants.AUTO_ABORT_PIPELINE_THROUGH_TRIGGER;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.pms.execution.utils.StatusUtils.isFinalStatus;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.StepExecutionParameters;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.steps.CILogKeyMetadata;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.logserviceclient.CILogServiceUtils;
import io.harness.ci.states.codebase.CodeBaseTaskStep;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.AckRequest;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;
import io.harness.logging.AutoLogContext;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.repositories.CIAccountExecutionMetadataRepository;
import io.harness.repositories.CILogKeyRepository;
import io.harness.repositories.CIStageOutputRepository;
import io.harness.repositories.CIStepStatusRepository;
import io.harness.repositories.StepExecutionParametersRepository;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class PipelineExecutionUpdateEventHandler implements OrchestrationEventHandler {
  @Inject private GitBuildStatusUtility gitBuildStatusUtility;
  @Inject private StageCleanupUtility stageCleanupUtility;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject private CILogServiceUtils ciLogServiceUtils;
  @Inject private CILicenseService ciLicenseService;
  @Inject private CIAccountExecutionMetadataRepository ciAccountExecutionMetadataRepository;
  @Inject private QueueExecutionUtils queueExecutionUtils;
  @Inject private HsqsClientService hsqsClientService;
  @Inject private StepExecutionParametersRepository stepExecutionParametersRepository;

  @Inject private CILogKeyRepository ciLogKeyRepository;

  private final String SERVICE_NAME_CI = "ci";
  private final int MAX_ATTEMPTS = 3;
  @Inject @Named("ciEventHandlerExecutor") private ExecutorService executorService;
  @Inject @Named("ciRatelimitHandlerExecutor") private ExecutorService ciRatelimitHandlerExecutor;
  @Inject CIStageOutputRepository ciStageOutputRepository;
  @Inject protected CIStepStatusRepository ciStepStatusRepository;

  @Override
  public void handleEvent(OrchestrationEvent event) {
    Ambiance ambiance = event.getAmbiance();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    Level level = AmbianceUtils.obtainCurrentLevel(ambiance);
    String serviceName = event.getServiceName();
    Status status = event.getStatus();
    ciRatelimitHandlerExecutor.submit(() -> { updateDailyBuildCount(level, status, serviceName, accountId); });
    executorService.submit(() -> {
      try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
        sendGitStatus(level, ambiance, status, event, accountId);
        sendCleanupRequest(level, ambiance, status, accountId);
      }
    });
  }

  private void deleteCIStageOutputs(Ambiance ambiance) {
    String stageExecutionId = ambiance.getStageExecutionId();
    try {
      ciStageOutputRepository.deleteFirstByStageExecutionId(stageExecutionId);
    } catch (Exception e) {
      log.error("Error while deleting CI outputs for stageExecutionId " + stageExecutionId, e);
    }
  }

  private void deleteCIStepStatusMetadata(Ambiance ambiance) {
    String stageExecutionId = ambiance.getStageExecutionId();
    try {
      ciStepStatusRepository.deleteByStageExecutionId(stageExecutionId);
    } catch (Exception e) {
      log.error("Error while deleting CI StepStatusMetadata for stageExecutionId " + stageExecutionId, e);
    }
  }

  private void deleteCILogKeysMetadata(Ambiance ambiance) {
    String stageExecutionId = ambiance.getStageExecutionId();
    try {
      ciLogKeyRepository.deleteByStageExecutionId(stageExecutionId);
    } catch (Exception e) {
      log.error("Error while deleting CLogKeyMetadata for stageExecutionId " + stageExecutionId, e);
    }
  }

  private void deleteCIStepParameters(Ambiance ambiance) {
    String stageRunTimeId = AmbianceUtils.getStageRuntimeIdAmbiance(ambiance);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    try {
      stepExecutionParametersRepository.deleteAllByAccountIdAndStageRunTimeId(accountId, stageRunTimeId);
    } catch (Exception e) {
      log.error("Error while deleting CI StepStatusMetadata for stageExecutionId " + stageRunTimeId, e);
    }
  }

  private void sendCleanupRequest(Level level, Ambiance ambiance, Status status, String accountId) {
    try {
      RetryPolicy<Object> retryPolicy = getRetryPolicy(format("[Retrying failed call to clean pod attempt: {}"),
          format("Failed to clean pod after retrying {} times"));

      Failsafe.with(retryPolicy).run(() -> {
        if (level.getStepType().getStepCategory() == StepCategory.STAGE && isFinalStatus(status)) {
          // TODO: Once Robust Cleanup implementation is done shift this after response from delegate is received.
          try {
            String topic = ciExecutionServiceConfig.getQueueServiceClientConfig().getTopic();
            CIExecutionMetadata ciExecutionMetadata =
                queueExecutionUtils.deleteActiveExecutionRecord(ambiance.getStageExecutionId());
            if (ciExecutionMetadata != null && StringUtils.isNotBlank(ciExecutionMetadata.getQueueId())) {
              // ack the request so that its not processed again.
              AckRequest ackRequest = AckRequest.builder()
                                          .itemId(ciExecutionMetadata.getQueueId())
                                          .consumerName(topic)
                                          .topic(topic)
                                          .subTopic(accountId)
                                          .build();
              hsqsClientService.ack(ackRequest);
            }
          } catch (Exception ex) {
            log.info("failed to remove execution record from db", ex);
          }

          deleteCIStageOutputs(ambiance);
          deleteCIStepStatusMetadata(ambiance);
          deleteCIStepParameters(ambiance);

          log.info("Received event with status {} to clean planExecutionId {}, stage {}", status,
              ambiance.getPlanExecutionId(), level.getIdentifier());
          stageCleanupUtility.submitCleanupRequest(ambiance, level.getIdentifier());

          String logKey = getLogKey(ambiance);

          // Get all keys list from executionID
          CILogKeyMetadata ciLogKeyMetadata = ciLogKeyRepository.findByStageExecutionId(ambiance.getStageExecutionId());

          // If there are any leftover logs still in the stream (this might be possible in specific cases
          // like in k8s node pressure evictions) - then this is where we move all of them to blob storage.
          if (ciLogKeyMetadata != null) {
            for (String key : ciLogKeyMetadata.getLogKeys()) {
              ciLogServiceUtils.closeLogStream(AmbianceUtils.getAccountId(ambiance), key, true, false);
            }
            deleteCILogKeysMetadata(ambiance);
          } else {
            log.warn("Log keys not found in DB, deleting with prefix");
            // Append '/' at the end of the prefix if it's not present so that it doesn't close log streams
            // for a different key.
            if (!logKey.endsWith("/")) {
              logKey = logKey + "/";
            }
            ciLogServiceUtils.closeLogStream(AmbianceUtils.getAccountId(ambiance), logKey, true, true);
          }

          // Now Delete the build from db while cleanup is happening. \
        } else if (level.getStepType().getStepCategory() == StepCategory.STAGE) {
          log.info("Skipping cleanup for stageExecutionID {} and stepCategory {} with status and pipeline {}",
              ambiance.getStageExecutionId(), level.getStepType().getStepCategory(), status,
              ambiance.getMetadata().getPipelineIdentifier());
        }
      });
    } catch (Exception ex) {
      log.error("Failed to send cleanup call for node {}", level.getRuntimeId(), ex);
    }
  }

  private String getLogKey(Ambiance ambiance) {
    return LogStreamingStepClientFactory.getLogBaseKey(ambiance);
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
          String runTimeId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
          Optional<StepExecutionParameters> stepExecutionParameters =
              stepExecutionParametersRepository.findFirstByAccountIdAndRunTimeId(accountId, runTimeId);
          StepParameters stepParameters;
          if (stepExecutionParameters.isPresent()) {
            try {
              StepExecutionParameters executionParameters = stepExecutionParameters.get();
              stepParameters =
                  RecastOrchestrationUtils.fromJson(executionParameters.getStepParameters(), StepParameters.class);
            } catch (Exception ex) {
              log.error("Error in deserialization", ex);
              stepParameters = event.getResolvedStepParameters();
            }
          } else {
            stepParameters = event.getResolvedStepParameters();
          }
          if (level.getStepType().getStepCategory() == StepCategory.STAGE) {
            gitBuildStatusUtility.sendStatusToGit(status, stepParameters, ambiance, accountId);
          } else if (level.getStepType().getType().equals(CodeBaseTaskStep.STEP_TYPE.getType())) {
            // It sends Running if codebase step successfully fetched commit sha via api token
            gitBuildStatusUtility.sendStatusToGit(Status.RUNNING, stepParameters, ambiance, accountId);
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

  private void updateDailyBuildCount(Level level, Status status, String serviceName, String accountId) {
    LicensesWithSummaryDTO licensesWithSummaryDTO = ciLicenseService.getLicenseSummary(accountId);
    if (licensesWithSummaryDTO == null) {
      throw new CIStageExecutionException("Please enable CI free plan or reach out to support.");
    }
    if (licensesWithSummaryDTO != null && licensesWithSummaryDTO.getEdition() == Edition.FREE) {
      if (level != null && serviceName.equalsIgnoreCase(SERVICE_NAME_CI)
          && level.getStepType().getStepCategory() == StepCategory.STAGE && (status == RUNNING)) {
        ciAccountExecutionMetadataRepository.updateCIDailyBuilds(accountId, level.getStartTs());
      }
    }
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
