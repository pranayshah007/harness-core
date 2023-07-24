package io.harness.service.impl;

import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.beans.DelegateTask.Status.STARTED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.utils.DelegateServiceConstants.PIPELINE;
import static io.harness.delegate.utils.DelegateServiceConstants.STAGE;
import static io.harness.delegate.utils.DelegateServiceConstants.STEP;
import static io.harness.delegate.utils.DelegateServiceConstants.STEP_GROUP;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_TASK_ACQUIRE;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_TASK_ACQUIRE_FAILED;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_TASK_VALIDATION;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskDataV2;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.utils.DelegateLogContextHelper;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.dms.configuration.DelegateServiceConfiguration;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.AutoLogContext;
import io.harness.logging.DelayLogContext;
import io.harness.logstreaming.LogStreamingServiceRestClient;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.network.SafeHttpCall;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DMSAssignDelegateService;
import io.harness.service.intfc.DMSTaskService;
import io.harness.service.intfc.DelegateCache;

import software.wings.TaskTypeToRequestResponseMapper;
import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;
import software.wings.service.intfc.DelegateSelectionLogsService;
import software.wings.utils.Utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

@Singleton
@ValidateOnExecution
@Slf4j
public class DMSTaskServiceImpl implements DMSTaskService {
  @Inject private DelegateCache delegateCache;

  @Inject private HPersistence persistence;

  @Inject private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @Inject private DelegateMetricsService delegateMetricsService;
  @Inject private LogStreamingServiceRestClient logStreamingServiceRestClient;

  @Inject ObjectMapper objectMapper;
  @Inject private DMSAssignDelegateService assignDelegateService;
  @Inject private Clock clock;
  @Inject private DelegateServiceConfiguration mainConfiguration;
  @Inject private DelegateSelectionLogsService delegateSelectionLogsService;
  //  @Inject private DmsObserverEventProducer dmsObserverEventProducer;

  private static final String ASYNC = "async";
  private static final String SYNC = "sync";

  private LoadingCache<String, String> logStreamingAccountTokenCache =
      CacheBuilder.newBuilder()
          .maximumSize(1000)
          .expireAfterWrite(24, TimeUnit.HOURS)
          .build(new CacheLoader<String, String>() {
            @Override
            public String load(String accountId) throws IOException {
              return retrieveLogStreamingAccountToken(accountId);
            }
          });

  @Override
  public DelegateTaskPackage acquireDelegateTask(
      String accountId, String delegateId, String taskId, String delegateInstanceId) {
    try {
      Delegate delegate = delegateCache.get(accountId, delegateId, false);
      if (delegate == null || DelegateInstanceStatus.ENABLED != delegate.getStatus()) {
        log.warn("Delegate rejected to acquire task, because it was not found to be in {} status.",
            DelegateInstanceStatus.ENABLED);
        return DelegateTaskPackage.builder().build();
      }

      log.debug("Acquiring delegate task");
      DelegateTask delegateTask = getUnassignedDelegateTask(accountId, taskId, delegateInstanceId);
      if (delegateTask == null) {
        return DelegateTaskPackage.builder().build();
      }

      try (AutoLogContext ignore = DelegateLogContextHelper.getLogContext(delegateTask)) {
        if (assignDelegateService.shouldValidate(delegateTask, delegateId)) {
          setValidationStarted(delegateId, delegateTask);
          return buildDelegateTaskPackage(delegateTask);
        } else if (assignDelegateService.isWhitelisted(delegateTask, delegateId)) {
          return assignTask(delegateId, taskId, delegateTask, delegateInstanceId);
        }
        log.info("Delegate {} is blacklisted for task {}", delegateId, taskId);
        return DelegateTaskPackage.builder().build();
      }
    } finally {
      log.debug("Done with acquire delegate task{} ", taskId);
    }
  }

  DelegateTask getUnassignedDelegateTask(String accountId, String taskId, String delegateInstanceId) {
    DelegateTask delegateTask =
        persistence.createQuery(DelegateTask.class, delegateTaskMigrationHelper.isMigrationEnabledForTask(taskId))
            .filter(DelegateTask.DelegateTaskKeys.accountId, accountId)
            .filter(DelegateTask.DelegateTaskKeys.uuid, taskId)
            .get();

    if (delegateTask != null) {
      copyTaskDataV2ToTaskData(delegateTask);

      if (delegateTask.getData() != null
          && SerializationFormat.JSON.equals(delegateTask.getData().getSerializationFormat())) {
        // CI's task data is in a json binary format in TaskData.data. But delegate hornors TaskData.parameters. This
        // blob of code converts the json data to java classes, and put into TaskData.parameters This is for DLITE only
        TaskType type = TaskType.valueOf(delegateTask.getData().getTaskType());
        TaskParameters taskParameters;
        try {
          taskParameters = objectMapper.readValue(
              delegateTask.getData().getData(), TaskTypeToRequestResponseMapper.getTaskRequestClass(type).orElse(null));
        } catch (IOException e) {
          throw new InvalidRequestException("could not parse bytes from delegate task data", e);
        }
        TaskData taskData = delegateTask.getData();
        taskData.setParameters(new Object[] {taskParameters});
        delegateTask.setData(taskData);
      }

      try (AutoLogContext ignore = DelegateLogContextHelper.getLogContext(delegateTask)) {
        if (delegateTask.getDelegateId() == null && delegateTask.getStatus() == QUEUED) {
          log.debug("Found unassigned delegate task");
          return delegateTask;
        } else if (delegateInstanceId.equals(delegateTask.getDelegateInstanceId())) {
          log.debug("Returning already assigned task to delegate from getUnassigned");
          return delegateTask;
        }
        log.debug("Task not available for delegate - it was assigned to {} instance id {} and has status {}",
            delegateTask.getDelegateId(), delegateTask.getDelegateInstanceId(), delegateTask.getStatus());
      }
    } else {
      log.info("Task no longer exists");
    }
    return null;
  }

  void setValidationStarted(String delegateId, DelegateTask delegateTask) {
    delegateMetricsService.recordDelegateTaskMetrics(delegateTask, DELEGATE_TASK_VALIDATION);
    boolean async =
        delegateTask.getData() != null ? delegateTask.getData().isAsync() : delegateTask.getTaskDataV2().isAsync();
    log.debug("Delegate to validate {} task", async ? ASYNC : SYNC);
    boolean migrationEnabledForDelegateTask =
        delegateTaskMigrationHelper.isMigrationEnabledForTask(delegateTask.getUuid());

    UpdateOperations<DelegateTask> updateOperations =
        persistence.createUpdateOperations(DelegateTask.class, migrationEnabledForDelegateTask)
            .addToSet(DelegateTask.DelegateTaskKeys.validatingDelegateIds, delegateId);
    Query<DelegateTask> updateQuery = persistence.createQuery(DelegateTask.class, migrationEnabledForDelegateTask)
                                          .filter(DelegateTask.DelegateTaskKeys.accountId, delegateTask.getAccountId())
                                          .filter(DelegateTask.DelegateTaskKeys.uuid, delegateTask.getUuid())
                                          .filter(DelegateTask.DelegateTaskKeys.status, QUEUED)
                                          .field(DelegateTask.DelegateTaskKeys.delegateId)
                                          .doesNotExist();
    persistence.update(updateQuery, updateOperations, migrationEnabledForDelegateTask);

    persistence.update(updateQuery.field(DelegateTask.DelegateTaskKeys.validationStartedAt).doesNotExist(),
        persistence.createUpdateOperations(DelegateTask.class, migrationEnabledForDelegateTask)
            .set(DelegateTask.DelegateTaskKeys.validationStartedAt, clock.millis()),
        migrationEnabledForDelegateTask);
  }

  private DelegateTaskPackage buildDelegateTaskPackage(DelegateTask delegateTask) {
    List<ExecutionCapability> executionCapabilityList = emptyList();
    if (isNotEmpty(delegateTask.getExecutionCapabilities())) {
      executionCapabilityList = delegateTask.getExecutionCapabilities()
                                    .stream()
                                    .filter(x -> x.evaluationMode() == ExecutionCapability.EvaluationMode.AGENT)
                                    .collect(toList());
    }

    DelegateTaskPackage.DelegateTaskPackageBuilder delegateTaskPackageBuilder =
        DelegateTaskPackage.builder()
            .accountId(delegateTask.getAccountId())
            .delegateId(delegateTask.getDelegateId())
            .delegateInstanceId(delegateTask.getDelegateInstanceId())
            .delegateTaskId(delegateTask.getUuid())
            .data(delegateTask.getData())
            .executionCapabilities(executionCapabilityList)
            .delegateCallbackToken(delegateTask.getDriverId())
            .serializationFormat(io.harness.beans.SerializationFormat.valueOf(SerializationFormat.KRYO.name()));

    boolean isTaskNg = !isEmpty(delegateTask.getSetupAbstractions())
        && Boolean.parseBoolean(delegateTask.getSetupAbstractions().get(NG));

    if (isTaskNg) {
      try {
        String logStreamingAccountToken = logStreamingAccountTokenCache.get(delegateTask.getAccountId());

        if (isNotBlank(logStreamingAccountToken)) {
          delegateTaskPackageBuilder.logStreamingToken(logStreamingAccountToken);
        }
      } catch (ExecutionException e) {
        delegateMetricsService.recordDelegateTaskMetrics(delegateTask, DELEGATE_TASK_ACQUIRE_FAILED);
        log.error(
            "Unable to retrieve the log streaming service account token, while preparing delegate task package", e);
        throw new InvalidRequestException("Please ensure log service is running.");
      }

      delegateTaskPackageBuilder.logStreamingAbstractions(delegateTask.getLogStreamingAbstractions());
      delegateTaskPackageBuilder.baseLogKey(Utils.emptyIfNull(delegateTask.getBaseLogKey()));
      delegateTaskPackageBuilder.shouldSkipOpenStream(delegateTask.isShouldSkipOpenStream());
    }

    if (delegateTask.getData().getParameters() == null || delegateTask.getData().getParameters().length != 1
        || !(delegateTask.getData().getParameters()[0] instanceof TaskParameters)) {
      return delegateTaskPackageBuilder.build();
    }

    delegateTaskPackageBuilder.encryptionConfigs(delegateTask.getDelegateTaskPackage().getEncryptionConfigs());
    delegateTaskPackageBuilder.secretDetails(delegateTask.getDelegateTaskPackage().getSecretDetails());
    delegateTaskPackageBuilder.secrets(delegateTask.getDelegateTaskPackage().getSecrets());

    return delegateTaskPackageBuilder.build();
  }

  DelegateTaskPackage assignTask(
      String delegateId, String taskId, DelegateTask delegateTask, String delegateInstanceId) {
    // Clear pending validations. No longer need to track since we're assigning.
    clearFromValidationCache(delegateTask);
    // QUESTION? Do we need a metric for this
    log.debug("Assigning {} task to delegate", delegateTask.getData().isAsync() ? ASYNC : SYNC);
    boolean migrationEnabledForDelegateTask = delegateTaskMigrationHelper.isMigrationEnabledForTask(taskId);

    Query<DelegateTask> query = persistence.createQuery(DelegateTask.class, migrationEnabledForDelegateTask)
                                    .filter(DelegateTask.DelegateTaskKeys.accountId, delegateTask.getAccountId())
                                    .filter(DelegateTask.DelegateTaskKeys.uuid, taskId)
                                    .filter(DelegateTask.DelegateTaskKeys.status, QUEUED)
                                    .field(DelegateTask.DelegateTaskKeys.delegateId)
                                    .doesNotExist()
                                    .field(DelegateTask.DelegateTaskKeys.delegateInstanceId)
                                    .doesNotExist()
                                    .project(DelegateTask.DelegateTaskKeys.data_parameters, false);
    UpdateOperations<DelegateTask> updateOperations =
        persistence.createUpdateOperations(DelegateTask.class, migrationEnabledForDelegateTask)
            .set(DelegateTask.DelegateTaskKeys.delegateId, delegateId)
            .set(DelegateTask.DelegateTaskKeys.delegateInstanceId, delegateInstanceId)
            .set(DelegateTask.DelegateTaskKeys.status, STARTED)
            .set(DelegateTask.DelegateTaskKeys.expiry, currentTimeMillis() + delegateTask.getData().getTimeout());
    DelegateTask task = persistence.findAndModifySystemData(
        query, updateOperations, HPersistence.returnNewOptions, migrationEnabledForDelegateTask);
    // If the task wasn't updated because delegateId already exists then query for the task with the delegateId in
    // case client is retrying the request
    copyTaskDataV2ToTaskData(task);
    if (task != null) {
      try (
          DelayLogContext ignore = new DelayLogContext(task.getLastUpdatedAt() - task.getCreatedAt(), OVERRIDE_ERROR)) {
        log.info("Task assigned to delegate");
      }
      task.getData().setParameters(delegateTask.getData().getParameters());
      delegateSelectionLogsService.logTaskAssigned(delegateId, task);

      if (delegateTask.isEmitEvent()) {
        Map<String, String> eventData = new HashMap<>();
        String taskType = task.getData().getTaskType();

        //        dmsObserverEventProducer.sendEvent(
        //            ReflectionUtils.getMethod(CIDelegateTaskObserver.class, "onTaskAssigned", String.class,
        //            String.class,
        //                String.class, String.class, String.class),
        //            DMSTaskServiceClassicImpl.class, delegateTask.getAccountId(), taskId, delegateId,
        //            delegateTask.getStageId(), taskType);
      }

      delegateMetricsService.recordDelegateTaskMetrics(delegateTask, DELEGATE_TASK_ACQUIRE);

      return buildDelegateTaskPackage(task);
    }
    task = persistence.createQuery(DelegateTask.class, migrationEnabledForDelegateTask)
               .filter(DelegateTask.DelegateTaskKeys.accountId, delegateTask.getAccountId())
               .filter(DelegateTask.DelegateTaskKeys.uuid, taskId)
               .filter(DelegateTask.DelegateTaskKeys.status, STARTED)
               .filter(DelegateTask.DelegateTaskKeys.delegateId, delegateId)
               .filter(DelegateTask.DelegateTaskKeys.delegateInstanceId, delegateInstanceId)
               .project(DelegateTask.DelegateTaskKeys.data_parameters, false)
               .get();
    if (task == null) {
      log.debug("Task no longer available for delegate");
      return null;
    }

    task.getData().setParameters(delegateTask.getData().getParameters());
    log.info("Returning previously assigned task to delegate");
    return buildDelegateTaskPackage(task);
  }

  private void clearFromValidationCache(DelegateTask delegateTask) {
    boolean migrationEnabledForDelegateTask =
        delegateTaskMigrationHelper.isMigrationEnabledForTask(delegateTask.getUuid());

    UpdateOperations<DelegateTask> updateOperations =
        persistence.createUpdateOperations(DelegateTask.class, migrationEnabledForDelegateTask)
            .unset(DelegateTask.DelegateTaskKeys.validatingDelegateIds)
            .unset(DelegateTask.DelegateTaskKeys.validationCompleteDelegateIds);
    Query<DelegateTask> updateQuery = persistence.createQuery(DelegateTask.class, migrationEnabledForDelegateTask)
                                          .filter(DelegateTask.DelegateTaskKeys.accountId, delegateTask.getAccountId())
                                          .filter(DelegateTask.DelegateTaskKeys.uuid, delegateTask.getUuid())
                                          .filter(DelegateTask.DelegateTaskKeys.status, QUEUED)
                                          .field(DelegateTask.DelegateTaskKeys.delegateId)
                                          .doesNotExist();
    persistence.update(updateQuery, updateOperations, migrationEnabledForDelegateTask);
  }

  private DelegateTask copyTaskDataV2ToTaskData(DelegateTask delegateTask) {
    if (delegateTask != null && delegateTask.getTaskDataV2() != null) {
      TaskDataV2 taskDataV2 = delegateTask.getTaskDataV2();
      if (taskDataV2 != null) {
        TaskData taskData =
            TaskData.builder()
                .data(taskDataV2.getData())
                .taskType(taskDataV2.getTaskType())
                .async(taskDataV2.isAsync())
                .parked(taskDataV2.isParked())
                .parameters(taskDataV2.getParameters())
                .timeout(taskDataV2.getTimeout())
                .expressionFunctorToken(taskDataV2.getExpressionFunctorToken())
                .expressions(taskDataV2.getExpressions())
                .serializationFormat(SerializationFormat.valueOf(taskDataV2.getSerializationFormat().name()))
                .build();
        delegateTask.setData(taskData);
      }
    }
    return delegateTask;
  }

  protected String retrieveLogStreamingAccountToken(String accountId) throws IOException {
    return SafeHttpCall.executeWithExceptions(logStreamingServiceRestClient.retrieveAccountToken(
        mainConfiguration.getLogStreamingServiceConfig().getServiceToken(), accountId));
  }

  @Override
  public List<SelectorCapability> fetchTaskSelectorCapabilities(List<ExecutionCapability> executionCapabilities) {
    List<SelectorCapability> selectorCapabilities = executionCapabilities.stream()
                                                        .filter(c -> c instanceof SelectorCapability)
                                                        .map(c -> (SelectorCapability) c)
                                                        .collect(Collectors.toList());
    if (isEmpty(selectorCapabilities)) {
      return selectorCapabilities;
    }
    List<SelectorCapability> selectors =
        selectorCapabilities.stream()
            .filter(sel -> Objects.nonNull(sel.getSelectorOrigin()))
            .filter(c
                -> c.getSelectorOrigin().equals(STEP) || c.getSelectorOrigin().equals(STEP_GROUP)
                    || c.getSelectorOrigin().equals(STAGE) || c.getSelectorOrigin().equals(PIPELINE))
            .collect(toList());
    if (!isEmpty(selectors)) {
      return selectors;
    }
    return selectorCapabilities;
  }
}
