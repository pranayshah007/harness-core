/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.workers.background.critical.iterator;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionInterruptType.MARK_EXPIRED;
import static io.harness.beans.ExecutionInterruptType.ROLLBACK_PROVISIONER_AFTER_PHASES;
import static io.harness.beans.ExecutionStatus.ERROR;
import static io.harness.beans.ExecutionStatus.EXPIRED;
import static io.harness.beans.ExecutionStatus.PREPARING;
import static io.harness.beans.ExecutionStatus.flowingStatuses;
import static io.harness.beans.FeatureName.SPG_DISABLE_EXPIRING_TO_MANUAL_INTERVENTION_CANDIDATE;
import static io.harness.beans.RepairActionCode.CONTINUE_WITH_DEFAULTS;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;

import static software.wings.sm.ExecutionInterrupt.ExecutionInterruptBuilder.anExecutionInterrupt;
import static software.wings.sm.StateType.PHASE;
import static software.wings.sm.StateType.PHASE_STEP;
import static software.wings.sm.StateType.SHELL_SCRIPT;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpAndRedisModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;

import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionInterrupt;
import software.wings.sm.ExecutionInterruptManager;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;
import software.wings.sm.StateMachineExecutor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WorkflowExecutionMonitorHandler
    extends IteratorPumpAndRedisModeHandler implements Handler<WorkflowExecution> {
  @Inject private AccountService accountService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ExecutionInterruptManager executionInterruptManager;
  @Inject private StateMachineExecutor stateMachineExecutor;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private MorphiaPersistenceProvider<WorkflowExecution> persistenceProvider;
  @Inject private WorkflowExecutionZombieHandler zombieHandler;

  private static final Duration INACTIVITY_TIMEOUT = Duration.ofMinutes(3);
  private static final Duration EXPIRE_THRESHOLD = Duration.ofMinutes(10);
  private static final Duration SHELL_SCRIPT_EXPIRE_THRESHOLD = Duration.ofSeconds(10);
  private static final Duration ACCEPTABLE_NO_ALERT_DELAY = Duration.ofSeconds(30);

  @Override
  protected void createAndStartIterator(PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<WorkflowExecution, MorphiaFilterExpander<WorkflowExecution>>)
                   persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                       WorkflowExecution.class,
                       MongoPersistenceIterator.<WorkflowExecution, MorphiaFilterExpander<WorkflowExecution>>builder()
                           .clazz(WorkflowExecution.class)
                           .fieldName(WorkflowExecutionKeys.nextIteration)
                           .filterExpander(q -> q.field(WorkflowExecutionKeys.status).in(flowingStatuses()))
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .handler(this)
                           .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
                           .schedulingType(SchedulingType.REGULAR)
                           .persistenceProvider(persistenceProvider)
                           .redistribute(true));
  }

  @Override
  public void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<WorkflowExecution, MorphiaFilterExpander<WorkflowExecution>>)
                   persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                       WorkflowExecution.class,
                       MongoPersistenceIterator.<WorkflowExecution, MorphiaFilterExpander<WorkflowExecution>>builder()
                           .clazz(WorkflowExecution.class)
                           .fieldName(WorkflowExecutionKeys.nextIteration)
                           .filterExpander(q -> q.field(WorkflowExecutionKeys.status).in(flowingStatuses()))
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                           .handler(this)
                           .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
                           .persistenceProvider(persistenceProvider));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "WorkflowExecutionMonitor";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(WorkflowExecution entity) {
    try {
      // logic to expire workflowExecution if its stuck in preparing state for more than 10 mins
      if (entity.getStatus() == PREPARING
          && System.currentTimeMillis() - entity.getStartTs() > EXPIRE_THRESHOLD.toMillis()) {
        updateStartStatusAndUnsetMessage(entity.getAppId(), entity.getUuid(), EXPIRED);
        return;
      }
      boolean disableExpiringManualInterventionFF =
          featureFlagService.isEnabled(SPG_DISABLE_EXPIRING_TO_MANUAL_INTERVENTION_CANDIDATE, entity.getAccountId());

      boolean hasActiveStates = false;
      try (HIterator<StateExecutionInstance> stateExecutionInstances =
               new HIterator<>(wingsPersistence.createQuery(StateExecutionInstance.class)
                                   .filter(StateExecutionInstanceKeys.appId, entity.getAppId())
                                   .filter(StateExecutionInstanceKeys.executionUuid, entity.getUuid())
                                   .field(StateExecutionInstanceKeys.status)
                                   .in(flowingStatuses())
                                   .fetch())) {
        hasActiveStates = stateExecutionInstances.hasNext();
        while (stateExecutionInstances.hasNext()) {
          StateExecutionInstance stateExecutionInstance = stateExecutionInstances.next();
          long now = System.currentTimeMillis();

          if (featureFlagService.isEnabled(FeatureName.ENABLE_CHECK_STATE_EXECUTION_STARTING, entity.getAccountId())) {
            checkIfStateExecutionIsStartingOrRunningWithoutResponse(stateExecutionInstance, now, entity.getEnvId());
          }

          if (shouldAvoidExpiringWithThreshold(stateExecutionInstance, now)) {
            continue;
          }

          if (stateExecutionInstance.getExpiryTs() > now) {
            continue;
          }

          log.info("Expired StateExecutionInstance found: {}", stateExecutionInstance.getUuid());
          ExecutionInterrupt executionInterrupt;
          if (stateExecutionInstance.isWaitingForInputs()
              && CONTINUE_WITH_DEFAULTS == stateExecutionInstance.getActionOnTimeout()) {
            executionInterrupt = anExecutionInterrupt()
                                     .executionInterruptType(ExecutionInterruptType.CONTINUE_WITH_DEFAULTS)
                                     .appId(stateExecutionInstance.getAppId())
                                     .executionUuid(stateExecutionInstance.getExecutionUuid())
                                     .stateExecutionInstanceId(stateExecutionInstance.getUuid())
                                     .build();
          } else if (stateExecutionInstance.isWaitingForManualIntervention()) {
            executionInterrupt =
                anExecutionInterrupt()
                    .executionInterruptType(stateExecutionInstance.getActionAfterManualInterventionTimeout())
                    .appId(stateExecutionInstance.getAppId())
                    .executionUuid(stateExecutionInstance.getExecutionUuid())
                    .stateExecutionInstanceId(stateExecutionInstance.getUuid())
                    .build();
          } else if (stateExecutionInstance.isManualInterventionCandidate() && disableExpiringManualInterventionFF) {
            // should add some threshold here to expire?
            continue;
          } else {
            executionInterrupt = anExecutionInterrupt()
                                     .executionInterruptType(MARK_EXPIRED)
                                     .appId(stateExecutionInstance.getAppId())
                                     .executionUuid(stateExecutionInstance.getExecutionUuid())
                                     .stateExecutionInstanceId(stateExecutionInstance.getUuid())
                                     .build();
          }

          if (featureFlagService.isEnabled(FeatureName.ROLLBACK_PROVISIONER_AFTER_PHASES, entity.getAccountId())) {
            if (executionInterrupt.getExecutionInterruptType() == ROLLBACK_PROVISIONER_AFTER_PHASES) {
              entity.setRollbackProvisionerAfterPhases(true);
              wingsPersistence.save(entity);
            }
          }
          executionInterruptManager.registerExecutionInterrupt(executionInterrupt);
        }
      } catch (WingsException exception) {
        ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
      } catch (Exception e) {
        log.error("Error in cleaning up the workflow execution {}", entity.getUuid(), e);
      }

      if (!hasActiveStates) {
        log.warn("WorkflowExecution {} is in non final state, but there is no active state execution for it.",
            entity.getUuid());

        final StateExecutionInstance stateExecutionInstance =
            wingsPersistence.createQuery(StateExecutionInstance.class)
                .filter(StateExecutionInstanceKeys.appId, entity.getAppId())
                .filter(StateExecutionInstanceKeys.executionUuid, entity.getUuid())
                .field(StateExecutionInstanceKeys.notifyId)
                .doesNotExist()
                .field(StateExecutionInstanceKeys.callback)
                .exists()
                .order(Sort.descending(StateExecutionInstanceKeys.lastUpdatedAt))
                .get();

        if (stateExecutionInstance == null) {
          log.info("Workflow execution stuck, but we cannot find good state to callback from.");
          return;
        }

        if (stateExecutionInstance.getLastUpdatedAt() > System.currentTimeMillis() - INACTIVITY_TIMEOUT.toMillis()) {
          log.warn("WorkflowExecution {} last callbackable state {} is very recent."
                  + "Lets give more time to the system it might be just in the middle of things.",
              entity.getUuid(), stateExecutionInstance.getUuid());
          return;
        }

        final ExecutionContextImpl executionContext =
            stateMachineExecutor.getExecutionContext(stateExecutionInstance.getAppId(),
                stateExecutionInstance.getExecutionUuid(), stateExecutionInstance.getUuid());

        try (AutoLogContext ignore = executionContext.autoLogContext()) {
          boolean expired = entity.getCreatedAt() < System.currentTimeMillis() - WorkflowExecution.EXPIRY.toMillis();
          // We lost the eventual exception, but its better than doing nothing
          ExecutionStatus finalStatus = expired ? EXPIRED : ERROR;
          log.info("[WorkflowStateUpdate] Executing StateCallBack with status: {}. StateExecutionId in context is {}",
              finalStatus, stateExecutionInstance.getUuid());
          stateMachineExecutor.executeCallback(executionContext, stateExecutionInstance, finalStatus, null);
        }
      }

      // APPLY ZOMBIE RULES TO FORCE ABORT A STUCK EXECUTION
      zombieHandler.handle(entity);

    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (Exception exception) {
      log.error("Error in monitoring the workflow execution {}", entity.getUuid());
    }
  }

  private void updateStartStatusAndUnsetMessage(String appId, String workflowExecutionId, ExecutionStatus status) {
    Query<WorkflowExecution> query = wingsPersistence.createQuery(WorkflowExecution.class)
                                         .filter(WorkflowExecutionKeys.appId, appId)
                                         .filter(WorkflowExecutionKeys.uuid, workflowExecutionId)
                                         .filter(WorkflowExecutionKeys.status, PREPARING);

    UpdateOperations<WorkflowExecution> updateOps = wingsPersistence.createUpdateOperations(WorkflowExecution.class)
                                                        .set(WorkflowExecutionKeys.status, status)
                                                        .set(WorkflowExecutionKeys.startTs, System.currentTimeMillis())
                                                        .unset(WorkflowExecutionKeys.message);

    wingsPersistence.findAndModify(query, updateOps, HPersistence.returnNewOptions);
  }

  private void checkIfStateExecutionIsStartingOrRunningWithoutResponse(
      StateExecutionInstance stateExecutionInstance, long currentTimeMillis, String envId) {
    if (stateExecutionInstance.getStateType() != null && !stateExecutionInstance.getStateType().equals(PHASE.getType())
        && !stateExecutionInstance.getStateType().equals(PHASE_STEP.getType())
        && stateExecutionInstance.getStatus().equals(ExecutionStatus.STARTING)
        && (currentTimeMillis - stateExecutionInstance.getStartTs()) > EXPIRE_THRESHOLD.toMillis()) {
      log.info("Restart StateExecutionInstance found: {}", stateExecutionInstance.getUuid());
      ExecutionInterrupt executionInterrupt = anExecutionInterrupt()
                                                  .accountId(stateExecutionInstance.getAccountId())
                                                  .executionInterruptType(ExecutionInterruptType.RETRY)
                                                  .envId(envId)
                                                  .appId(stateExecutionInstance.getAppId())
                                                  .executionUuid(stateExecutionInstance.getExecutionUuid())
                                                  .stateExecutionInstanceId(stateExecutionInstance.getUuid())
                                                  .build();

      executionInterruptManager.registerExecutionInterrupt(executionInterrupt);
    }
  }

  private boolean shouldAvoidExpiringWithThreshold(
      StateExecutionInstance stateExecutionInstance, long currentTimeMillis) {
    // adding a expire threshold just for shell script for now
    if (stateExecutionInstance.getStateType() != null
        && stateExecutionInstance.getStateType().equals(SHELL_SCRIPT.getName())) {
      if (stateExecutionInstance.getExpiryTs() < currentTimeMillis
          && stateExecutionInstance.getExpiryTs() + SHELL_SCRIPT_EXPIRE_THRESHOLD.toMillis() > currentTimeMillis) {
        return true;
      }
    }
    return false;
  }
}
