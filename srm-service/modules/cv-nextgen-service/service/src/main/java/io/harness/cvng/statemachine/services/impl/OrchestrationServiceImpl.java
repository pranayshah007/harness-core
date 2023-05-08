/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.impl;

import static io.harness.cvng.CVConstants.STATE_MACHINE_IGNORE_LIMIT;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eventsframework.EventsFrameworkConstants.SRM_STATEMACHINE_LOCK;
import static io.harness.eventsframework.EventsFrameworkConstants.SRM_STATEMACHINE_LOCK_TIMEOUT;
import static io.harness.eventsframework.EventsFrameworkConstants.SRM_STATEMACHINE_LOCK_WAIT_TIMEOUT;

import io.harness.cvng.analysis.services.api.DeploymentTimeSeriesAnalysisService;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.jobs.StateMachineEventPublisherService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.metrics.CVNGMetricsUtils;
import io.harness.cvng.metrics.services.impl.MetricContextBuilder;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisOrchestratorStatus;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator.AnalysisOrchestratorKeys;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine;
import io.harness.cvng.statemachine.services.api.AnalysisStateMachineService;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.metrics.AutoMetricContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.persistence.HPersistence;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrchestrationServiceImpl implements OrchestrationService {
  @Inject private HPersistence hPersistence;
  @Inject private AnalysisStateMachineService stateMachineService;

  @Inject private VerificationTaskService verificationTaskService;
  @Inject private StateMachineEventPublisherService stateMachineEventPublisherService;
  @Inject private PersistentLocker persistentLocker;
  @Inject private MetricService metricService;
  @Inject private MetricContextBuilder metricContextBuilder;
  @Inject private DeploymentTimeSeriesAnalysisService deploymentTimeSeriesAnalysisService;
  @Inject private Map<VerificationTask.TaskType, AnalysisStateMachineService> taskTypeAnalysisStateMachineServiceMap;

  @Override
  public void queueAnalysis(AnalysisInput analysisInput) {
    String accountId = verificationTaskService.get(analysisInput.getVerificationTaskId()).getAccountId();
    queueAnalysisWithoutEventPublish(accountId, analysisInput);

    stateMachineEventPublisherService.registerTaskComplete(accountId, analysisInput.getVerificationTaskId());
  }

  @Override
  public void queueAnalysisWithoutEventPublish(String accountId, AnalysisInput analysisInput) {
    validateAnalysisInputs(analysisInput);
    log.info("Queuing analysis for verificationTaskId: {}, startTime: {}, endTime: {}",
        analysisInput.getVerificationTaskId(), analysisInput.getStartTime(), analysisInput.getEndTime());
    if (deploymentTimeSeriesAnalysisService.isAnalysisFailFastForLatestTimeRange(
            analysisInput.getVerificationTaskId())) {
      log.info("DeploymentTimeSeriesAnalysis from LE is FailFast, so not queuing analysis for verificationTaskId: {}",
          analysisInput.getVerificationTaskId());
      return;
    }

    VerificationTask verificationTask = verificationTaskService.get(analysisInput.getVerificationTaskId());
    VerificationTask.TaskType verificationTaskType = verificationTask.getTaskInfo().getTaskType();

    AnalysisStateMachine stateMachine =
        taskTypeAnalysisStateMachineServiceMap.get(verificationTaskType).createStateMachine(analysisInput);

    Query<AnalysisOrchestrator> orchestratorQuery =
        hPersistence.createQuery(AnalysisOrchestrator.class)
            .filter(AnalysisOrchestratorKeys.verificationTaskId, analysisInput.getVerificationTaskId());

    UpdateOperations<AnalysisOrchestrator> updateOperations =
        hPersistence.createUpdateOperations(AnalysisOrchestrator.class)
            .setOnInsert(AnalysisOrchestratorKeys.verificationTaskId, analysisInput.getVerificationTaskId())
            .setOnInsert(AnalysisOrchestratorKeys.accountId, accountId)
            .setOnInsert(AnalysisOrchestratorKeys.uuid,
                generateUuid()) // By default mongo generates object id instead of string and our hPersistence does not
            // work well with objectIds.
            .set(AnalysisOrchestratorKeys.status, AnalysisOrchestratorStatus.RUNNING)
            .set(AnalysisOrchestratorKeys.validUntil, Date.from(OffsetDateTime.now().plusDays(30).toInstant()))
            .addToSet(AnalysisOrchestratorKeys.analysisStateMachineQueue, Arrays.asList(stateMachine));

    hPersistence.upsert(orchestratorQuery, updateOperations);
  }

  private void validateAnalysisInputs(AnalysisInput inputs) {
    Preconditions.checkNotNull(inputs.getVerificationTaskId(), "verificationTaskId can not be null");
    Preconditions.checkNotNull(inputs.getStartTime(), "startTime can not be null");
    Preconditions.checkNotNull(inputs.getEndTime(), "endTime can not be null");
  }

  @Override
  public void orchestrate(AnalysisOrchestrator orchestrator) {
    Preconditions.checkNotNull(orchestrator, "orchestrator cannot be null when trying to orchestrate");

    String lockString = SRM_STATEMACHINE_LOCK + orchestrator.getVerificationTaskId();
    try (AcquiredLock acquiredLock =
             persistentLocker.waitToAcquireLock(lockString, Duration.ofSeconds(SRM_STATEMACHINE_LOCK_TIMEOUT),
                 Duration.ofSeconds(SRM_STATEMACHINE_LOCK_WAIT_TIMEOUT))) {
      orchestrateAtRunningState(orchestrator);
    } catch (Exception e) {
      // TODO: these errors needs to go to execution log so that we can connect it with the right context and show them
      // in the UI.
      // TODO: setup alert
      log.error("Failed to orchestrate: {}", orchestrator.getVerificationTaskId(), e);
      throw e;
    }
  }

  @Override
  public void markTerminated(String verificationTaskId) {
    updateStatusOfOrchestrator(verificationTaskId, AnalysisOrchestratorStatus.TERMINATED);
  }

  @Override
  public void markStateMachineTerminated(String verificationTaskId) {
    AnalysisOrchestrator orchestrator = getAnalysisOrchestrator(verificationTaskId);
    List<AnalysisStateMachine> analysisStateMachines = orchestrator.getAnalysisStateMachineQueue();
    analysisStateMachines.forEach(analysisStateMachine -> analysisStateMachine.setStatus(AnalysisStatus.TERMINATED));
    Query<AnalysisOrchestrator> orchestratorQuery =
        hPersistence.createQuery(AnalysisOrchestrator.class)
            .filter(AnalysisOrchestratorKeys.verificationTaskId, verificationTaskId);
    UpdateOperations<AnalysisOrchestrator> updateOperations =
        hPersistence.createUpdateOperations(AnalysisOrchestrator.class)
            .set(AnalysisOrchestratorKeys.status, AnalysisOrchestratorStatus.TERMINATED)
            .set(AnalysisOrchestratorKeys.analysisStateMachineQueue, Collections.EMPTY_LIST);
    hPersistence.save(analysisStateMachines);
    hPersistence.upsert(orchestratorQuery, updateOperations);
  }

  @Override
  public void markCompleted(String verificationTaskId) {
    updateStatusOfOrchestrator(verificationTaskId, AnalysisOrchestratorStatus.COMPLETED);
  }

  @Override
  public void markCompleted(Set<String> verificationTaskIds) {
    Query<AnalysisOrchestrator> orchestratorQuery = hPersistence.createQuery(AnalysisOrchestrator.class)
                                                        .field(AnalysisOrchestratorKeys.verificationTaskId)
                                                        .in(verificationTaskIds);

    UpdateOperations<AnalysisOrchestrator> updateOperations =
        hPersistence.createUpdateOperations(AnalysisOrchestrator.class)
            .set(AnalysisOrchestratorKeys.status, AnalysisOrchestratorStatus.COMPLETED);

    hPersistence.update(orchestratorQuery, updateOperations);
  }

  private void orchestrateAtRunningState(AnalysisOrchestrator orchestrator) {
    if (isNotEmpty(orchestrator.getAnalysisStateMachineQueue())
        && orchestrator.getAnalysisStateMachineQueue().size() > 5) {
      log.info("For verification task ID {}, orchestrator has more than 5 tasks waiting."
              + " Please check if there is a growing backlog.",
          orchestrator.getVerificationTaskId());
      try (AutoMetricContext ignore = metricContextBuilder.getContext(orchestrator, AnalysisOrchestrator.class)) {
        metricService.incCounter(CVNGMetricsUtils.ORCHESTRATOR_STATE_MACHINE_QUEUE_COUNT_ABOVE_FIVE);
      }
    }

    AnalysisStateMachine currentlyExecutingStateMachine =
        stateMachineService.getExecutingStateMachine(orchestrator.getVerificationTaskId());
    if (currentlyExecutingStateMachine == null && isNotEmpty(orchestrator.getAnalysisStateMachineQueue())) {
      currentlyExecutingStateMachine = orchestrator.getAnalysisStateMachineQueue().get(0);
    }
    if (currentlyExecutingStateMachine != null) {
      AnalysisStatus stateMachineStatus = null;

      switch (currentlyExecutingStateMachine.getStatus()) {
        case CREATED:
        case SUCCESS:
        case IGNORED:
          orchestrateNewAnalysisStateMachine(
              orchestrator.getVerificationTaskId(), currentlyExecutingStateMachine.getTotalRetryCountToBePropagated());
          break;
        case RUNNING:
          log.info("For {}, state machine is currently RUNNING. "
                  + "We will call executeStateMachine() to handover execution to state machine.",
              orchestrator.getVerificationTaskId());
          stateMachineStatus = stateMachineService.executeStateMachine(currentlyExecutingStateMachine);
          break;
        case FAILED:
          markCompleted(orchestrator.getVerificationTaskId());
          break;
        case TIMEOUT:
          orchestrateFailedStateMachine(currentlyExecutingStateMachine);
          break;
        case TERMINATED:
          markTerminated(currentlyExecutingStateMachine.getVerificationTaskId());
          break;
        case COMPLETED:
          log.info("Analysis for the entire duration is done. Time to close down");
          markCompleted(orchestrator.getVerificationTaskId());
          break;
        default:
          log.info("Unknown analysis status of the state machine under execution");
      }
      if ((AnalysisStatus.SUCCESS == stateMachineStatus || AnalysisStatus.COMPLETED == stateMachineStatus)
          && !AnalysisOrchestratorStatus.getFinalStates().contains(orchestrator.getStatus())) {
        orchestrateNewAnalysisStateMachine(
            orchestrator.getVerificationTaskId(), currentlyExecutingStateMachine.getTotalRetryCountToBePropagated());
      }
    }
  }

  private void orchestrateFailedStateMachine(AnalysisStateMachine currentStateMachine) {
    stateMachineService.retryStateMachineAfterFailure(currentStateMachine);
  }

  private AnalysisStateMachine getFrontOfStateMachineQueue(String verificationTaskId) {
    Query<AnalysisOrchestrator> orchestratorQuery =
        hPersistence.createQuery(AnalysisOrchestrator.class)
            .filter(AnalysisOrchestratorKeys.verificationTaskId, verificationTaskId);
    UpdateOperations<AnalysisOrchestrator> updateOperations =
        hPersistence.createUpdateOperations(AnalysisOrchestrator.class)
            .removeFirst(AnalysisOrchestratorKeys.analysisStateMachineQueue);
    AnalysisOrchestrator orchestrator =
        hPersistence.findAndModify(orchestratorQuery, updateOperations, new FindAndModifyOptions().returnNew(false));

    return orchestrator.getAnalysisStateMachineQueue() != null && orchestrator.getAnalysisStateMachineQueue().size() > 0
        ? orchestrator.getAnalysisStateMachineQueue().get(0)
        : null;
  }

  private void orchestrateNewAnalysisStateMachine(String verificationTaskId, int totalRetryCount) {
    int ignoredCount = 0;
    List<AnalysisStateMachine> ignoredStatemachines = new ArrayList<>();
    AnalysisStateMachine analysisStateMachine = null;
    while (ignoredCount < STATE_MACHINE_IGNORE_LIMIT) {
      analysisStateMachine = getFrontOfStateMachineQueue(verificationTaskId);

      if (analysisStateMachine == null) {
        log.info("There is currently nothing to analyze for verificationTaskId {}", verificationTaskId);
        break;
      }
      analysisStateMachine.setTotalRetryCount(totalRetryCount);
      Optional<AnalysisStateMachine> ignoredStateMachine =
          stateMachineService.ignoreOldStateMachine(analysisStateMachine);
      if (ignoredStateMachine.isEmpty()) {
        break;
      }
      analysisStateMachine = null;
      ignoredCount++;

      // add to ignored list and remove from queue
      ignoredStatemachines.add(ignoredStateMachine.get());
    }
    if (ignoredCount > 0) {
      // save all the ignored state machines.
      stateMachineService.save(ignoredStatemachines);
    }

    if (analysisStateMachine != null && ignoredCount < STATE_MACHINE_IGNORE_LIMIT) {
      stateMachineService.initiateStateMachine(verificationTaskId, analysisStateMachine);
    }
    if (analysisStateMachine == null) {
      updateStatusOfOrchestrator(verificationTaskId, AnalysisOrchestratorStatus.WAITING);
    } else {
      updateStatusOfOrchestrator(verificationTaskId, AnalysisOrchestratorStatus.RUNNING);
    }
  }

  private void updateStatusOfOrchestrator(String verificationTaskId, AnalysisOrchestratorStatus status) {
    Query<AnalysisOrchestrator> orchestratorQuery =
        hPersistence.createQuery(AnalysisOrchestrator.class)
            .filter(AnalysisOrchestratorKeys.verificationTaskId, verificationTaskId);
    if (status.equals(AnalysisOrchestratorStatus.WAITING)) {
      // handle race condition if we are changing status to WAITING
      orchestratorQuery = orchestratorQuery.field(AnalysisOrchestratorKeys.analysisStateMachineQueue).sizeEq(0);
    }

    UpdateOperations<AnalysisOrchestrator> updateOperations =
        hPersistence.createUpdateOperations(AnalysisOrchestrator.class).set(AnalysisOrchestratorKeys.status, status);

    hPersistence.update(orchestratorQuery, updateOperations);
  }

  @Override
  public AnalysisOrchestrator getAnalysisOrchestrator(String verificationTaskId) {
    return hPersistence.createQuery(AnalysisOrchestrator.class)
        .filter(AnalysisOrchestratorKeys.verificationTaskId, verificationTaskId)
        .get();
  }
}
