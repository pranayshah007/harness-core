/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.PmsCommonConstants.AUTO_ABORT_PIPELINE_THROUGH_TRIGGER;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.pms.execution.utils.StatusUtils.isFinalStatus;
import static io.harness.steps.StepUtils.buildAbstractions;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.dto.CITaskDetails;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.outcomes.VmDetailsOutcome;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.logserviceclient.CILogServiceUtils;
import io.harness.ci.states.codebase.CodeBaseTaskStep;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ci.CICleanupTaskParams;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.CIVmCleanupTaskParams;
import io.harness.encryption.Scope;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.hsqs.client.api.HsqsClientService;
import io.harness.hsqs.client.model.AckRequest;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;
import io.harness.logstreaming.LogStreamingHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.repositories.CIAccountExecutionMetadataRepository;
import io.harness.repositories.CIStageOutputRepository;
import io.harness.repositories.CITaskDetailsRepository;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.StepUtils;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.fabric8.utils.Strings;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class PipelineExecutionUpdateEventHandler implements OrchestrationEventHandler {
  @Inject private OutcomeService outcomeService;
  @Inject private GitBuildStatusUtility gitBuildStatusUtility;
  @Inject private StageCleanupUtility stageCleanupUtility;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject private CILogServiceUtils ciLogServiceUtils;
  @Inject private CILicenseService ciLicenseService;
  @Inject private CITaskDetailsRepository ciTaskDetailsRepository;
  @Inject private CIAccountExecutionMetadataRepository ciAccountExecutionMetadataRepository;
  @Inject private QueueExecutionUtils queueExecutionUtils;
  @Inject private HsqsClientService hsqsClientService;

  private final String SERVICE_NAME_CI = "ci";
  private final int MAX_ATTEMPTS = 3;
  private final int WAIT_TIME_IN_SECOND = 30;
  @Inject @Named("ciEventHandlerExecutor") private ExecutorService executorService;
  @Inject @Named("ciRatelimitHandlerExecutor") private ExecutorService ciRatelimitHandlerExecutor;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject CIStageOutputRepository ciStageOutputRepository;

  @Override
  public void handleEvent(OrchestrationEvent event) {
    Ambiance ambiance = event.getAmbiance();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    Level level = AmbianceUtils.obtainCurrentLevel(ambiance);
    String serviceName = event.getServiceName();
    Status status = event.getStatus();
    ciRatelimitHandlerExecutor.submit(() -> { updateDailyBuildCount(level, status, serviceName, accountId); });
    executorService.submit(() -> {
      sendGitStatus(level, ambiance, status, event, accountId);
      sendCleanupRequest(level, ambiance, status, accountId);
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
          CICleanupTaskParams ciCleanupTaskParams = stageCleanupUtility.buildAndfetchCleanUpParameters(ambiance);

          if (ciCleanupTaskParams == null) {
            return;
          }

          log.info("Received event with status {} to clean planExecutionId {}, stage {}", status,
              ambiance.getPlanExecutionId(), level.getIdentifier());

          DelegateTaskRequest delegateTaskRequest =
              getDelegateCleanupTaskRequest(ambiance, ciCleanupTaskParams, accountId);

          String taskId = delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
          log.info("Submitted cleanup request with taskId {} for planExecutionId {}, stage {}", taskId,
              ambiance.getPlanExecutionId(), level.getIdentifier());

          String logKey = getLogKey(ambiance);

          // Append '/' at the end of the prefix if it's not present so that it doesn't close log streams
          // for a different key.
          if (!logKey.endsWith("/")) {
            logKey = logKey + "/";
          }

          // If there are any leftover logs still in the stream (this might be possible in specific cases
          // like in k8s node pressure evictions) - then this is where we move all of them to blob storage.
          ciLogServiceUtils.closeLogStream(AmbianceUtils.getAccountId(ambiance), logKey, true, true);
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

  private DelegateTaskRequest getDelegateCleanupTaskRequest(
      Ambiance ambiance, CICleanupTaskParams ciCleanupTaskParams, String accountId) throws InterruptedException {
    List<TaskSelector> taskSelectors = stageCleanupUtility.fetchDelegateSelector(ambiance);

    Map<String, String> abstractions = buildAbstractions(ambiance, Scope.PROJECT);
    String taskType = "CI_CLEANUP";
    SerializationFormat serializationFormat = SerializationFormat.KRYO;
    boolean executeOnHarnessHostedDelegates = false;
    String stageId = ambiance.getStageExecutionId();
    List<String> eligibleToExecuteDelegateIds = new ArrayList<>();

    CICleanupTaskParams.Type type = ciCleanupTaskParams.getType();
    if (type == CICleanupTaskParams.Type.DLITE_VM) {
      taskType = TaskType.DLITE_CI_VM_CLEANUP_TASK.getDisplayName();
      executeOnHarnessHostedDelegates = true;
      serializationFormat = SerializationFormat.JSON;
      String delegateId = fetchDelegateId(ambiance);
      if (Strings.isNotBlank(delegateId)) {
        eligibleToExecuteDelegateIds.add(delegateId);
        ciTaskDetailsRepository.deleteFirstByStageExecutionId(stageId);
      } else {
        log.warn(
            "Unable to locate delegate ID for stage ID: {}. Cleanup task may be routed to the wrong delegate", stageId);
      }
    }
    // Since we use a same class to handle both VM and DOCKER cases due to they share a lot of similarities in
    // processing logic, and we use a CICleanupTaskParams type name `VM` to represent them. Only docker scenario
    // needs additional step to add matching docker delegate id into the eligible to execute delegate id list.
    else if (type == CICleanupTaskParams.Type.VM) {
      if (((CIVmCleanupTaskParams) ciCleanupTaskParams).getInfraInfo() == CIInitializeTaskParams.Type.DOCKER) {
        // TODO: Start using fetchDelegateId once we start emitting & processing the event for Docker as well
        OptionalOutcome optionalOutput = outcomeService.resolveOptional(
            ambiance, RefObjectUtils.getOutcomeRefObject(VmDetailsOutcome.VM_DETAILS_OUTCOME));
        VmDetailsOutcome vmDetailsOutcome = (VmDetailsOutcome) optionalOutput.getOutcome();
        if (vmDetailsOutcome != null && Strings.isNotBlank(vmDetailsOutcome.getDelegateId())) {
          eligibleToExecuteDelegateIds.add(vmDetailsOutcome.getDelegateId());
        }
      }
    }

    return DelegateTaskRequest.builder()
        .accountId(accountId)
        .executeOnHarnessHostedDelegates(executeOnHarnessHostedDelegates)
        .stageId(stageId)
        .eligibleToExecuteDelegateIds(eligibleToExecuteDelegateIds)
        .taskSelectors(taskSelectors.stream().map(TaskSelector::getSelector).collect(Collectors.toList()))
        .selectors(taskSelectors)
        .taskSetupAbstractions(abstractions)
        .executionTimeout(java.time.Duration.ofSeconds(900))
        .taskType(taskType)
        .serializationFormat(serializationFormat)
        .taskParameters(ciCleanupTaskParams)
        .taskDescription("CI cleanup pod task")
        .build();
  }

  private String fetchDelegateId(Ambiance ambiance) throws InterruptedException {
    OptionalOutcome optionalOutput = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(VmDetailsOutcome.VM_DETAILS_OUTCOME));
    VmDetailsOutcome vmDetailsOutcome = (VmDetailsOutcome) optionalOutput.getOutcome();

    if (vmDetailsOutcome != null && Strings.isNotBlank(vmDetailsOutcome.getDelegateId())) {
      return vmDetailsOutcome.getDelegateId();
    } else {
      String stageId = ambiance.getStageExecutionId();
      log.info("Could not process the delegate ID for stage ID: {} from the init response. Trying to look in the DB",
          stageId);

      long currentTime = System.currentTimeMillis();
      long waitTill = currentTime + WAIT_TIME_IN_SECOND * 1000;

      while (System.currentTimeMillis() < waitTill) {
        Optional<CITaskDetails> taskDetailsOptional = ciTaskDetailsRepository.findFirstByStageExecutionId(stageId);

        if (taskDetailsOptional.isPresent()) {
          CITaskDetails taskDetails = taskDetailsOptional.get();
          if (Strings.isNotBlank(taskDetails.getDelegateId())) {
            log.info("Successfully found delegate ID: {} corresponding to stage ID: {}", taskDetails.getDelegateId(),
                stageId);
            return taskDetails.getDelegateId();
          }
          break;
        } else {
          Thread.sleep(1000);
        }
      }
    }
    return null;
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
