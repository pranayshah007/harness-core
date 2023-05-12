/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask.internal;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.govern.IgnoreThrowable.ignoredOnPurpose;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;
import static io.harness.perpetualtask.PerpetualTaskType.CONTAINER_INSTANCE_SYNC;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.delegate.Capability;
import io.harness.delegate.DelegateTaskValidationFailedException;
import io.harness.delegate.NoEligibleDelegatesInAccountException;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskInvalidRequestException;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.NoAvailableDelegatesException;
import io.harness.delegate.beans.NoInstalledDelegatesException;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.exception.DelegateTaskExpiredException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpAndRedisModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceRequiredProvider;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskServiceClient;
import io.harness.perpetualtask.PerpetualTaskServiceClientRegistry;
import io.harness.perpetualtask.PerpetualTaskState;
import io.harness.perpetualtask.PerpetualTaskUnassignedReason;
import io.harness.perpetualtask.internal.PerpetualTaskRecord.PerpetualTaskRecordKeys;
import io.harness.serializer.KryoSerializer;
import io.harness.workers.background.CrossEnvironmentAccountStatusBasedEntityProcessController;

import software.wings.beans.TaskType;
import software.wings.service.InstanceSyncConstants;
import software.wings.service.impl.PerpetualTaskCapabilityCheckResponse;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.perpetualtask.PerpetualTaskCrudObserver;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@BreakDependencyOn("software.wings.service.InstanceSyncConstants")
public class PerpetualTaskRecordHandler extends IteratorPumpAndRedisModeHandler implements PerpetualTaskCrudObserver {
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private DelegateService delegateService;
  @Inject private PerpetualTaskService perpetualTaskService;
  @Inject private PerpetualTaskServiceClientRegistry clientRegistry;
  @Inject private MorphiaPersistenceRequiredProvider<PerpetualTaskRecord> persistenceProvider;
  @Inject private AccountService accountService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private PerpetualTaskRecordDao perpetualTaskRecordDao;

  private static final Duration ACCEPTABLE_NO_ALERT_DELAY = ofSeconds(45);
  private static final Duration ACCEPTABLE_EXECUTION_TIME = ofSeconds(30);

  @Override
  protected void createAndStartIterator(PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator =
        (MongoPersistenceIterator<PerpetualTaskRecord, MorphiaFilterExpander<PerpetualTaskRecord>>)
            persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                PerpetualTaskRecordHandler.class,
                MongoPersistenceIterator.<PerpetualTaskRecord, MorphiaFilterExpander<PerpetualTaskRecord>>builder()
                    .clazz(PerpetualTaskRecord.class)
                    .fieldName(PerpetualTaskRecordKeys.assignIteration)
                    .targetInterval(targetInterval)
                    .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                    .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
                    .handler(this::assign)
                    .filterExpander(query
                        -> query.filter(PerpetualTaskRecordKeys.state, PerpetualTaskState.TASK_UNASSIGNED)
                               .field(PerpetualTaskRecordKeys.assignAfterMs)
                               .lessThanOrEq(System.currentTimeMillis()))
                    .entityProcessController(
                        new CrossEnvironmentAccountStatusBasedEntityProcessController<>(accountService))
                    .schedulingType(REGULAR)
                    .persistenceProvider(persistenceProvider)
                    .redistribute(true));
  }

  @Override
  protected void createAndStartRedisBatchIterator(
      PersistenceIteratorFactory.RedisBatchExecutorOptions executorOptions, Duration targetInterval) {
    iterator =
        (MongoPersistenceIterator<PerpetualTaskRecord, MorphiaFilterExpander<PerpetualTaskRecord>>)
            persistenceIteratorFactory.createRedisBatchIteratorWithDedicatedThreadPool(executorOptions,
                PerpetualTaskRecordHandler.class,
                MongoPersistenceIterator.<PerpetualTaskRecord, MorphiaFilterExpander<PerpetualTaskRecord>>builder()
                    .clazz(PerpetualTaskRecord.class)
                    .fieldName(PerpetualTaskRecordKeys.assignIteration)
                    .targetInterval(targetInterval)
                    .acceptableNoAlertDelay(ACCEPTABLE_NO_ALERT_DELAY)
                    .acceptableExecutionTime(ACCEPTABLE_EXECUTION_TIME)
                    .handler(this::assign)
                    .filterExpander(query
                        -> query.filter(PerpetualTaskRecordKeys.state, PerpetualTaskState.TASK_UNASSIGNED)
                               .field(PerpetualTaskRecordKeys.assignAfterMs)
                               .lessThanOrEq(System.currentTimeMillis()))
                    .entityProcessController(
                        new CrossEnvironmentAccountStatusBasedEntityProcessController<>(accountService))
                    .persistenceProvider(persistenceProvider));
  }

  @Override
  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "PerpetualTaskAssignment";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  public void assign(PerpetualTaskRecord taskRecord) {
    try (AutoLogContext ignore0 = new AccountLogContext(taskRecord.getAccountId(), OVERRIDE_ERROR)) {
      String taskId = taskRecord.getUuid();
      if (!isEmpty(taskRecord.getDelegateId())
          && delegateService.checkDelegateConnected(taskRecord.getAccountId(), taskRecord.getDelegateId())) {
        perpetualTaskRecordDao.appointDelegate(taskId, taskRecord.getDelegateId(), System.currentTimeMillis());
        log.info(
            "Assign perpetual task {} to previously appointed delegate id {}.", taskId, taskRecord.getDelegateId());
        return;
      }
      log.info("Start assigning perpetual task id:{} type:{} to delegate.", taskId, taskRecord.getPerpetualTaskType());
      DelegateTask validationTask = getValidationTask(taskRecord);
      if (validationTask == null) {
        log.info("Getting validation task null for perpetual task id {}.", taskId);
        perpetualTaskService.markStateAndNonAssignedReason_OnAssignTryCount(taskRecord,
            PerpetualTaskUnassignedReason.PT_TASK_FAILED, PerpetualTaskState.TASK_INVALID,
            "Unable to get validation task");
        return;
      }
      try {
        DelegateResponseData response = delegateService.executeTaskV2(validationTask);

        if (response instanceof ErrorNotifyResponseData) {
          log.info("Perpetual validation task {} failed, unable to assign delegate.", validationTask.getUuid());
          if (CONTAINER_INSTANCE_SYNC.equals(taskRecord.getPerpetualTaskType())) {
            perpetualTaskService.markStateAndNonAssignedReason_OnAssignTryCount(taskRecord,
                PerpetualTaskUnassignedReason.PT_TASK_FAILED, PerpetualTaskState.TASK_NON_ASSIGNABLE,
                ((ErrorNotifyResponseData) response).getErrorMessage());
          } else {
            perpetualTaskService.markStateAndNonAssignedReason_OnAssignTryCount(taskRecord,
                PerpetualTaskUnassignedReason.PT_TASK_FAILED, PerpetualTaskState.TASK_INVALID,
                ((ErrorNotifyResponseData) response).getErrorMessage());
          }
          return;
        }

        if (response instanceof DelegateTaskNotifyResponseData) {
          if (response instanceof PerpetualTaskCapabilityCheckResponse) {
            boolean isAbleToExecutePerpetualTask =
                ((PerpetualTaskCapabilityCheckResponse) response).isAbleToExecutePerpetualTask();
            if (!isAbleToExecutePerpetualTask) {
              perpetualTaskService.markStateAndNonAssignedReason_OnAssignTryCount(taskRecord,
                  PerpetualTaskUnassignedReason.PT_TASK_FAILED, PerpetualTaskState.TASK_NON_ASSIGNABLE,
                  "Unable to execute task");
              return;
            }
          }
          if (((DelegateTaskNotifyResponseData) response).getDelegateMetaInfo() != null) {
            String delegateId = ((DelegateTaskNotifyResponseData) response).getDelegateMetaInfo().getId();
            log.info("Delegate {} is assigned to the inactive {} perpetual task with id={}.", delegateId,
                taskRecord.getPerpetualTaskType(), taskId);
            perpetualTaskService.appointDelegate(
                taskRecord.getAccountId(), taskId, delegateId, System.currentTimeMillis());
          } else {
            log.info("Perpetual validation task {} unable to assign delegate due to missing DelegateMetaInfo.",
                validationTask.getUuid());
            perpetualTaskService.markStateAndNonAssignedReason_OnAssignTryCount(taskRecord,
                PerpetualTaskUnassignedReason.NO_DELEGATE_AVAILABLE, PerpetualTaskState.TASK_INVALID,
                "Unable to assign to any delegates");
          }
        } else if ((response instanceof RemoteMethodReturnValueData)
            && (((RemoteMethodReturnValueData) response).getException() instanceof InvalidRequestException)) {
          perpetualTaskRecordDao.updateTaskStateNonAssignableReason(taskId,
              PerpetualTaskUnassignedReason.PT_TASK_FAILED, taskRecord.getAssignTryCount(),
              PerpetualTaskState.TASK_NON_ASSIGNABLE);
          log.error("Invalid request exception: ", ((RemoteMethodReturnValueData) response).getException());
        } else {
          log.error(format(
              "Assignment for perpetual task id=%s got unexpected delegate response %s", taskId, response.toString()));
        }
      } catch (NoInstalledDelegatesException exception) {
        ignoredOnPurpose(exception);
        perpetualTaskRecordDao.updateTaskStateNonAssignableReason(taskId,
            PerpetualTaskUnassignedReason.NO_DELEGATE_INSTALLED, taskRecord.getAssignTryCount(),
            PerpetualTaskState.TASK_NON_ASSIGNABLE);
      } catch (NoAvailableDelegatesException exception) {
        ignoredOnPurpose(exception);
        perpetualTaskService.markStateAndNonAssignedReason_OnAssignTryCount(taskRecord,
            PerpetualTaskUnassignedReason.NO_DELEGATE_AVAILABLE, PerpetualTaskState.TASK_NON_ASSIGNABLE,
            exception.toString());
      } catch (NoEligibleDelegatesInAccountException exception) {
        ignoredOnPurpose(exception);
        perpetualTaskService.markStateAndNonAssignedReason_OnAssignTryCount(taskRecord,
            PerpetualTaskUnassignedReason.NO_ELIGIBLE_DELEGATES, PerpetualTaskState.TASK_NON_ASSIGNABLE,
            exception.toString());
      } catch (DelegateTaskExpiredException | InvalidArgumentsException exception) {
        ignoredOnPurpose(exception);
        perpetualTaskService.markStateAndNonAssignedReason_OnAssignTryCount(taskRecord,
            PerpetualTaskUnassignedReason.TASK_EXPIRED, PerpetualTaskState.TASK_NON_ASSIGNABLE, exception.toString());
      } catch (DelegateTaskValidationFailedException exception) {
        ignoredOnPurpose(exception);
        perpetualTaskService.markStateAndNonAssignedReason_OnAssignTryCount(taskRecord,
            PerpetualTaskUnassignedReason.TASK_VALIDATION_FAILED, PerpetualTaskState.TASK_NON_ASSIGNABLE,
            exception.toString());
      } catch (DelegateTaskInvalidRequestException e) {
        log.error("Invalid task found, perpetual task id {}", taskRecord.getUuid(), e);
        perpetualTaskService.markStateAndNonAssignedReason_OnAssignTryCount(
            taskRecord, PerpetualTaskUnassignedReason.PT_TASK_FAILED, PerpetualTaskState.TASK_INVALID, e.toString());
      } catch (Exception e) {
        log.error("Failed to assign to any delegate, perpetual task {} ", taskId, e);
        perpetualTaskService.markStateAndNonAssignedReason_OnAssignTryCount(taskRecord,
            PerpetualTaskUnassignedReason.PT_TASK_FAILED, PerpetualTaskState.TASK_NON_ASSIGNABLE, e.toString());
      }
    } catch (Exception e) {
      log.error("Unexpected error occurred during assigning perpetual task {}", taskRecord.getUuid(), e);
      perpetualTaskService.markStateAndNonAssignedReason_OnAssignTryCount(
          taskRecord, PerpetualTaskUnassignedReason.PT_TASK_FAILED, PerpetualTaskState.TASK_INVALID, e.toString());
    }
  }

  protected DelegateTask getValidationTask(PerpetualTaskRecord taskRecord) {
    if (isNotEmpty(taskRecord.getClientContext().getClientParams())) {
      PerpetualTaskServiceClient client = clientRegistry.getClient(taskRecord.getPerpetualTaskType());
      if (client == null) {
        log.error("Error fetching PerpetualTaskServiceClient");
        return null;
      }
      return client.getValidationTask(taskRecord.getClientContext(), taskRecord.getAccountId());
    }

    PerpetualTaskExecutionBundle perpetualTaskExecutionBundle = null;
    try {
      if (taskRecord.getClientContext().getExecutionBundle() == null) {
        return null;
      }
      perpetualTaskExecutionBundle =
          PerpetualTaskExecutionBundle.parseFrom(taskRecord.getClientContext().getExecutionBundle());

    } catch (InvalidProtocolBufferException e) {
      log.error("Failed to parse perpetual task execution bundle", e);
      return null;
    }

    List<ExecutionCapability> executionCapabilityList = new ArrayList<>();
    List<Capability> capabilityList = perpetualTaskExecutionBundle.getCapabilitiesList();
    if (isNotEmpty(capabilityList)) {
      executionCapabilityList = capabilityList.stream()
                                    .map(capability
                                        -> (ExecutionCapability) kryoSerializer.asInflatedObject(
                                            capability.getKryoCapability().toByteArray()))
                                    .collect(toList());
    }

    return DelegateTask.builder()
        .executionCapabilities(executionCapabilityList)
        .accountId(taskRecord.getAccountId())
        .data(TaskData.builder()
                  .async(false)
                  .taskType(TaskType.CAPABILITY_VALIDATION.name())
                  .parameters(executionCapabilityList.toArray())
                  .timeout(TimeUnit.MINUTES.toMillis(InstanceSyncConstants.VALIDATION_TIMEOUT_MINUTES))
                  .build())
        .setupAbstractions(perpetualTaskExecutionBundle.getSetupAbstractionsMap())
        .build();
  }

  @Override
  public void onPerpetualTaskCreated() {
    if (iterator != null) {
      iterator.wakeup();
    }
  }

  @Override
  public void onRebalanceRequired() {}
}
