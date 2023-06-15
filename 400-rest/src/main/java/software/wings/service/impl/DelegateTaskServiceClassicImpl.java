/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.beans.DelegateTask.Status.ABORTED;
import static io.harness.beans.DelegateTask.Status.ERROR;
import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.beans.DelegateTask.Status.STARTED;
import static io.harness.beans.DelegateTask.Status.runningStatuses;
import static io.harness.beans.FeatureName.DELEGATE_TASK_LOAD_DISTRIBUTION;
import static io.harness.beans.FeatureName.GIT_HOST_CONNECTIVITY;
import static io.harness.beans.FeatureName.QUEUE_DELEGATE_TASK;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.SizeFunction.size;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.DelegateTaskAbortEvent.Builder.aDelegateTaskAbortEvent;
import static io.harness.delegate.beans.DelegateTaskEvent.DelegateTaskEventBuilder.aDelegateTaskEvent;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.executioncapability.ExecutionCapability.EvaluationMode;
import static io.harness.delegate.task.TaskFailureReason.EXPIRED;
import static io.harness.exception.FailureType.DELEGATE_RESTART;
import static io.harness.govern.Switch.noop;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_TASK_ACQUIRE;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_TASK_ACQUIRE_FAILED;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_TASK_CREATION;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_TASK_EXPIRED;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_TASK_NO_ELIGIBLE_DELEGATES;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_TASK_NO_FIRST_WHITELISTED;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_TASK_VALIDATION;

import static software.wings.expression.SecretManagerModule.EXPRESSION_EVALUATOR_EXECUTOR;
import static software.wings.service.impl.AssignDelegateServiceImpl.PIPELINE;
import static software.wings.service.impl.AssignDelegateServiceImpl.STAGE;
import static software.wings.service.impl.AssignDelegateServiceImpl.STEP;
import static software.wings.service.impl.AssignDelegateServiceImpl.STEP_GROUP;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.NO_ELIGIBLE_DELEGATES;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingInt;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.beans.FeatureName;
import io.harness.cache.HarnessCacheManager;
import io.harness.delegate.DelegateGlobalAccountController;
import io.harness.delegate.NoEligibleDelegatesInAccountException;
import io.harness.delegate.NoGlobalDelegateAccountException;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateProgressData;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskAbortEvent;
import io.harness.delegate.beans.DelegateTaskEvent;
import io.harness.delegate.beans.DelegateTaskExpiredException;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskPackage.DelegateTaskPackageBuilder;
import io.harness.delegate.beans.DelegateTaskRank;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.DelegateTaskResponse.ResponseCode;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.NoAvailableDelegatesException;
import io.harness.delegate.beans.NoInstalledDelegatesException;
import io.harness.delegate.beans.SecretDetail;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskDataV2;
import io.harness.delegate.beans.TaskGroup;
import io.harness.delegate.beans.TaskSelectorMap;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.core.beans.AcquireTasksResponse;
import io.harness.delegate.core.beans.InputData;
import io.harness.delegate.core.beans.TaskPayload;
import io.harness.delegate.queueservice.DelegateTaskQueueService;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.request.CfCommandTaskParameters;
import io.harness.delegate.task.pcf.request.CfCommandTaskParameters.CfCommandTaskParametersBuilder;
import io.harness.delegate.task.pcf.request.CfRunPluginCommandRequest;
import io.harness.delegate.task.tasklogging.TaskLogContext;
import io.harness.delegate.utils.DelegateLogContextHelper;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.environment.SystemEnvironment;
import io.harness.eraro.ErrorCode;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.eventframework.manager.ManagerObserverEventProducer;
import io.harness.exception.CriticalExpressionEvaluationException;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ff.FeatureFlagService;
import io.harness.iterator.FailDelegateTaskIteratorHelper;
import io.harness.lock.PersistentLocker;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.DelayLogContext;
import io.harness.logging.DelegateDriverLogContext;
import io.harness.logstreaming.LogStreamingServiceRestClient;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.network.SafeHttpCall;
import io.harness.observer.RemoteObserverInformer;
import io.harness.observer.Subject;
import io.harness.persistence.HPersistence;
import io.harness.reflection.ExpressionReflectionUtils;
import io.harness.reflection.ReflectionUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateCallbackRegistry;
import io.harness.service.intfc.DelegateCallbackService;
import io.harness.service.intfc.DelegateSetupService;
import io.harness.service.intfc.DelegateSyncService;
import io.harness.service.intfc.DelegateTaskResultsProvider;
import io.harness.service.intfc.DelegateTaskSelectorMapService;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.version.VersionInfoManager;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.TaskTypeToRequestResponseMapper;
import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.ExecutionCredential;
import software.wings.beans.GitConfig;
import software.wings.beans.GitValidationParameters;
import software.wings.beans.HostValidationTaskParameters;
import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;
import software.wings.beans.dto.SettingAttribute;
import software.wings.common.AuditHelper;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.delegatetasks.cv.RateLimitExceededException;
import software.wings.delegatetasks.delegatecapability.CapabilityHelper;
import software.wings.delegatetasks.validation.core.DelegateConnectionResult;
import software.wings.expression.EncryptedDataDetails;
import software.wings.expression.ManagerPreExecutionExpressionEvaluator;
import software.wings.expression.ManagerPreviewExpressionEvaluator;
import software.wings.expression.NgSecretManagerFunctor;
import software.wings.expression.SecretManagerFunctor;
import software.wings.expression.SecretManagerMode;
import software.wings.expression.SweepingOutputSecretFunctor;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.impl.infra.InfraDownloadService;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AssignDelegateService;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.DelegateSelectionLogsService;
import software.wings.service.intfc.DelegateTaskServiceClassic;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.utils.Utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.atmosphere.cpr.BroadcasterFactory;

@Singleton
@ValidateOnExecution
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@BreakDependencyOn("software.wings.app.MainConfiguration")
@BreakDependencyOn("software.wings.app.PortalConfig")
@BreakDependencyOn("software.wings.beans.Application")
@BreakDependencyOn("io.harness.event.handler.impl.EventPublishHelper")
@BreakDependencyOn("software.wings.expression.NgSecretManagerFunctor")
@OwnedBy(DEL)
public class DelegateTaskServiceClassicImpl implements DelegateTaskServiceClassic, DelegateObserver {
  private static final String ASYNC = "async";
  private static final String SYNC = "sync";
  private static final String STREAM_DELEGATE = "/stream/delegate/";
  public static final String TASK_SELECTORS = "Task Selectors";
  public static final String TASK_CATEGORY_MAP = "Task Category Map";

  @Inject private HPersistence persistence;
  @Inject ObjectMapper objectMapper;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private MainConfiguration mainConfiguration;
  @Inject private EventEmitter eventEmitter;

  @Inject private AccountService accountService;
  @Inject private BroadcasterFactory broadcasterFactory;
  @Inject private AssignDelegateService assignDelegateService;
  @Inject private AlertService alertService;
  @Inject private Clock clock;
  @Inject private VersionInfoManager versionInfoManager;
  @Inject private Injector injector;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private InfraDownloadService infraDownloadService;
  @Inject private ManagerDecryptionService managerDecryptionService;
  @Inject private SecretManager secretManager;
  @Inject private ExpressionEvaluator evaluator;
  @Inject private FileService fileService;
  @Inject private EventPublishHelper eventPublishHelper;
  @Inject private ConfigService configService;
  @Inject private ServiceTemplateService serviceTemplateService;
  @Inject private ArtifactCollectionUtils artifactCollectionUtils;
  @Inject private PersistentLocker persistentLocker;
  @Inject private DelegateTaskBroadcastHelper broadcastHelper;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Inject private ConfigurationController configurationController;
  @Inject private DelegateSelectionLogsService delegateSelectionLogsService;
  @Inject private DelegateDao delegateDao;
  @Inject private SystemEnvironment sysenv;
  @Inject private DelegateSyncService delegateSyncService;
  @Inject private DelegateTaskService delegateTaskService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private DelegateCallbackRegistry delegateCallbackRegistry;
  @Inject private DelegateTaskSelectorMapService taskSelectorMapService;
  @Inject private SettingsService settingsService;
  @Inject private LogStreamingServiceRestClient logStreamingServiceRestClient;
  @Inject @Named("PRIVILEGED") private SecretManagerClientService ngSecretService;
  @Inject private DelegateCache delegateCache;
  @Inject private DelegateSetupService delegateSetupService;
  @Inject private AuditHelper auditHelper;
  @Inject private DelegateMetricsService delegateMetricsService;
  @Inject private DelegateGlobalAccountController delegateGlobalAccountController;
  @Inject @Named(EXPRESSION_EVALUATOR_EXECUTOR) ExecutorService expressionEvaluatorExecutor;
  @Inject @Getter private Subject<DelegateObserver> subject = new Subject<>();
  @Inject private DelegateTaskQueueService delegateTaskQueueService;
  @Inject private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @Inject private FailDelegateTaskIteratorHelper failDelegateTaskIteratorHelper;

  private static final SecureRandom random = new SecureRandom();
  private HarnessCacheManager harnessCacheManager;

  @Inject private RemoteObserverInformer remoteObserverInformer;
  @Inject private ManagerObserverEventProducer managerObserverEventProducer;

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

  private final Cache<String, EncryptedDataDetails> secretsCache =
      Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(5, TimeUnit.MINUTES).build();

  public static void embedCapabilitiesInDelegateTask(
      DelegateTask task, Collection<EncryptionConfig> encryptionConfigs, ExpressionEvaluator maskingEvaluator) {
    if (isEmpty(task.getData().getParameters()) || isNotEmpty(task.getExecutionCapabilities())) {
      return;
    }

    task.setExecutionCapabilities(new ArrayList<>());
    task.getExecutionCapabilities().addAll(
        Arrays.stream(task.getData().getParameters())
            .filter(param -> param instanceof ExecutionCapabilityDemander)
            .flatMap(param
                -> ((ExecutionCapabilityDemander) param).fetchRequiredExecutionCapabilities(maskingEvaluator).stream())
            .collect(toList()));

    if (isNotEmpty(encryptionConfigs)) {
      task.getExecutionCapabilities().addAll(
          EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForSecretManagers(
              encryptionConfigs, maskingEvaluator));
    }
  }

  public static void embedCapabilitiesInDelegateTaskV2(
      DelegateTask task, Collection<EncryptionConfig> encryptionConfigs, ExpressionEvaluator maskingEvaluator) {
    if (isEmpty(task.getTaskDataV2().getParameters()) || isNotEmpty(task.getExecutionCapabilities())) {
      return;
    }

    task.setExecutionCapabilities(new ArrayList<>());
    task.getExecutionCapabilities().addAll(
        Arrays.stream(task.getTaskDataV2().getParameters())
            .filter(param -> param instanceof ExecutionCapabilityDemander)
            .flatMap(param
                -> ((ExecutionCapabilityDemander) param).fetchRequiredExecutionCapabilities(maskingEvaluator).stream())
            .collect(toList()));

    if (isNotEmpty(encryptionConfigs)) {
      task.getExecutionCapabilities().addAll(
          EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForSecretManagers(
              encryptionConfigs, maskingEvaluator));
    }
  }

  private void checkTaskRankRateLimit(DelegateTask task) {
    if (task.getRank() == null) {
      task.setRank(DelegateTaskRank.IMPORTANT);
    }
    String accountId = task.isExecuteOnHarnessHostedDelegates() ? task.getSecondaryAccountId() : task.getAccountId();

    long currentTaskCount;
    long maxTaskCount;
    switch (task.getRank()) {
      case OPTIONAL:
        currentTaskCount = delegateCache.getTasksCount(accountId, DelegateTaskRank.OPTIONAL);
        maxTaskCount = getTaskLimit(accountId, DelegateTaskRank.OPTIONAL);

        if (currentTaskCount >= maxTaskCount) {
          throw new RateLimitExceededException(
              format("Rate limit reached for tasks with rank OPTIONAL. Current task count %s and max limit %s ",
                  currentTaskCount, maxTaskCount));
        }
        break;
      case IMPORTANT:
        currentTaskCount = delegateCache.getTasksCount(accountId, DelegateTaskRank.IMPORTANT);
        maxTaskCount = getTaskLimit(accountId, DelegateTaskRank.IMPORTANT);
        if (currentTaskCount >= maxTaskCount) {
          throw new RateLimitExceededException(
              format("Rate limit reached for tasks with rank IMPORTANT. Current task count %s and max limit %s ",
                  currentTaskCount, maxTaskCount));
        }
        break;
      default:
        throw new InvalidArgumentsException("Unsupported delegate task rank " + task.getRank());
    }
  }

  private long getTaskLimit(String accountId, DelegateTaskRank rank) {
    Account account = accountService.getFromCacheWithFallback(accountId);
    if (rank == DelegateTaskRank.OPTIONAL) {
      return account.getOptionalDelegateTaskLimit() != null
          ? account.getOptionalDelegateTaskLimit()
          : mainConfiguration.getPortal().getOptionalDelegateTaskRejectAtLimit();
    }
    return account.getImportantDelegateTaskLimit() != null
        ? account.getImportantDelegateTaskLimit()
        : mainConfiguration.getPortal().getImportantDelegateTaskRejectAtLimit();
  }

  @VisibleForTesting
  @Override
  public void convertToExecutionCapability(DelegateTask task) {
    Set<ExecutionCapability> executionCapabilities = new HashSet<>();

    if (isNotEmpty(task.getTags())) {
      SelectorCapability selectorCapability =
          SelectorCapability.builder().selectors(new HashSet<>(task.getTags())).selectorOrigin(TASK_SELECTORS).build();
      executionCapabilities.add(selectorCapability);
    }

    boolean isTaskNg =
        !isEmpty(task.getSetupAbstractions()) && Boolean.parseBoolean(task.getSetupAbstractions().get(NG));

    // DelegateRequest will always be ng
    if (!isTaskNg && task.getData() != null && task.getData().getTaskType() != null) {
      TaskGroup taskGroup = TaskType.valueOf(task.getData().getTaskType()).getTaskGroup();
      TaskSelectorMap mapFromTaskType = taskSelectorMapService.get(task.getAccountId(), taskGroup);
      if (mapFromTaskType != null && isNotEmpty(mapFromTaskType.getSelectors())) {
        SelectorCapability selectorCapability = SelectorCapability.builder()
                                                    .selectors(mapFromTaskType.getSelectors())
                                                    .selectorOrigin(TASK_CATEGORY_MAP)
                                                    .build();
        executionCapabilities.add(selectorCapability);
      }
    }

    if (task.getExecutionCapabilities() == null) {
      task.setExecutionCapabilities(new ArrayList<>(executionCapabilities));
      if (task.getData() != null && isNotEmpty(executionCapabilities)) {
        addToTaskActivityLog(task,
            CapabilityHelper.generateLogStringWithSelectionCapabilitiesGenerated(
                task.getData().getTaskType(), task.getExecutionCapabilities()));
      }
    } else {
      task.getExecutionCapabilities().addAll(executionCapabilities);
    }
  }

  @VisibleForTesting
  @Override
  public void convertToExecutionCapabilityV2(DelegateTask task) {
    Set<ExecutionCapability> executionCapabilities = new HashSet<>();

    if (isNotEmpty(task.getTags())) {
      SelectorCapability selectorCapability =
          SelectorCapability.builder().selectors(new HashSet<>(task.getTags())).selectorOrigin(TASK_SELECTORS).build();
      executionCapabilities.add(selectorCapability);
    }

    boolean isTaskNg =
        !isEmpty(task.getSetupAbstractions()) && Boolean.parseBoolean(task.getSetupAbstractions().get(NG));

    if (!isTaskNg && task.getTaskDataV2() != null && task.getTaskDataV2().getTaskType() != null) {
      TaskGroup taskGroup = TaskType.valueOf(task.getTaskDataV2().getTaskType()).getTaskGroup();
      TaskSelectorMap mapFromTaskType = taskSelectorMapService.get(task.getAccountId(), taskGroup);
      if (mapFromTaskType != null && isNotEmpty(mapFromTaskType.getSelectors())) {
        SelectorCapability selectorCapability = SelectorCapability.builder()
                                                    .selectors(mapFromTaskType.getSelectors())
                                                    .selectorOrigin(TASK_CATEGORY_MAP)
                                                    .build();
        executionCapabilities.add(selectorCapability);
      }
    }

    if (task.getExecutionCapabilities() == null) {
      task.setExecutionCapabilities(new ArrayList<>(executionCapabilities));
      if (task.getTaskDataV2() != null && isNotEmpty(executionCapabilities)) {
        addToTaskActivityLog(task,
            CapabilityHelper.generateLogStringWithSelectionCapabilitiesGenerated(
                task.getTaskDataV2().getTaskType(), task.getExecutionCapabilities()));
      }
    } else {
      task.getExecutionCapabilities().addAll(executionCapabilities);
    }
  }

  @Override
  public String queueTask(DelegateTask task) {
    task.getData().setAsync(true);
    if (task.getUuid() == null) {
      task.setUuid(delegateTaskMigrationHelper.generateDelegateTaskUUID());
    }

    try (AutoLogContext ignore1 = new TaskLogContext(task.getUuid(), task.getData().getTaskType(),
             TaskType.valueOf(task.getData().getTaskType()).getTaskGroup().name(), task.getRank(), OVERRIDE_NESTS);
         AutoLogContext ignore2 = new AccountLogContext(task.getAccountId(), OVERRIDE_ERROR)) {
      log.info("Queueing async task {} of type {} ", task.getUuid(), task.getData().getTaskType());
      processDelegateTask(task, QUEUED);
      broadcastHelper.broadcastNewDelegateTaskAsync(task);
    }
    return task.getUuid();
  }

  @Override
  public String queueTaskV2(DelegateTask task) {
    task.getTaskDataV2().setAsync(true);
    if (task.getUuid() == null) {
      task.setUuid(delegateTaskMigrationHelper.generateDelegateTaskUUID());
    }

    try (
        AutoLogContext ignore1 = new TaskLogContext(task.getUuid(), task.getTaskDataV2().getTaskType(),
            TaskType.valueOf(task.getTaskDataV2().getTaskType()).getTaskGroup().name(), task.getRank(), OVERRIDE_NESTS);
        AutoLogContext ignore2 = new AccountLogContext(task.getAccountId(), OVERRIDE_ERROR)) {
      processDelegateTaskV2(task, QUEUED);
      broadcastHelper.broadcastNewDelegateTaskAsyncV2(task);
    }
    return task.getUuid();
  }

  @Override
  public void scheduleSyncTask(DelegateTask task) {
    task.getData().setAsync(false);
    if (task.getUuid() == null) {
      task.setUuid(delegateTaskMigrationHelper.generateDelegateTaskUUID());
    }

    try (AutoLogContext ignore1 = new TaskLogContext(task.getUuid(), task.getData().getTaskType(),
             TaskType.valueOf(task.getData().getTaskType()).getTaskGroup().name(), task.getRank(), OVERRIDE_NESTS);
         AutoLogContext ignore2 = new AccountLogContext(task.getAccountId(), OVERRIDE_ERROR)) {
      processDelegateTask(task, QUEUED);
      broadcastHelper.rebroadcastDelegateTask(task);
    }
  }

  @Override
  public void scheduleSyncTaskV2(DelegateTask task) {
    task.getTaskDataV2().setAsync(false);
    if (task.getUuid() == null) {
      task.setUuid(delegateTaskMigrationHelper.generateDelegateTaskUUID());
    }

    try (
        AutoLogContext ignore1 = new TaskLogContext(task.getUuid(), task.getTaskDataV2().getTaskType(),
            TaskType.valueOf(task.getTaskDataV2().getTaskType()).getTaskGroup().name(), task.getRank(), OVERRIDE_NESTS);
        AutoLogContext ignore2 = new AccountLogContext(task.getAccountId(), OVERRIDE_ERROR)) {
      processDelegateTaskV2(task, QUEUED);
      broadcastHelper.rebroadcastDelegateTaskV2(task);
    }
  }

  @Override
  public <T extends DelegateResponseData> T executeTask(DelegateTask task) {
    scheduleSyncTask(task);
    return delegateSyncService.waitForTask(task.getUuid(), task.calcDescription(),
        Duration.ofMillis(task.getData().getTimeout()), task.getExecutionCapabilities());
  }

  @Override
  public <T extends DelegateResponseData> T executeTaskV2(DelegateTask task) {
    scheduleSyncTaskV2(task);
    return delegateSyncService.waitForTask(task.getUuid(), task.calcDescriptionV2(),
        Duration.ofMillis(task.getTaskDataV2().getTimeout()), task.getExecutionCapabilities());
  }

  public void setAdditionalTaskFields(DelegateTask task, DelegateTask.Status taskStatus) {
    String taskInfo = format("Processing task id: %s of %s", task.getUuid(), task.getTaskSummary());
    addToTaskActivityLog(task, taskInfo);
    task.setStatus(taskStatus);
    task.setVersion(getVersion());
    task.setLastBroadcastAt(clock.millis());
    if (task.isExecuteOnHarnessHostedDelegates()) {
      task.setSecondaryAccountId(task.getAccountId());
      if (delegateGlobalAccountController.getGlobalAccount().isPresent()) {
        String globalDelegateAccount = delegateGlobalAccountController.getGlobalAccount().get().getUuid();
        task.setAccountId(globalDelegateAccount);
      } else {
        throw new NoGlobalDelegateAccountException(
            "No Global Delegate Account Found", ErrorCode.NO_GLOBAL_DELEGATE_ACCOUNT);
      }
    }

    // For forward compatibility set the wait id to the uuid
    if (task.getUuid() == null) {
      task.setUuid(delegateTaskMigrationHelper.generateDelegateTaskUUID());
    }

    if (task.getWaitId() == null) {
      task.setWaitId(task.getUuid());
    }

    // For backward compatibility we base the queue task expiry on the execution timeout
    if (task.getExpiry() == 0) {
      long executionTimeout = 0L;
      if (Objects.nonNull(task.getData())) {
        executionTimeout = task.getData().getTimeout();
      } else if (Objects.nonNull(task.getTaskDataV2())) {
        executionTimeout = task.getTaskDataV2().getTimeout();
      } else {
        executionTimeout = task.getExecutionTimeout();
      }
      task.setExpiry(currentTimeMillis() + executionTimeout);
    }
  }

  @Override
  public void processScheduleTaskRequest(DelegateTask task, DelegateTask.Status taskStatus) {
    setAdditionalTaskFields(task, taskStatus);
    try (AutoLogContext ignore = DelegateLogContextHelper.getLogContext(task)) {
      try {
        // convert tags to selector capabilities
        if (isNotEmpty(task.getTags())) {
          SelectorCapability selectorCapability = SelectorCapability.builder()
                                                      .selectors(new HashSet<>(task.getTags()))
                                                      .selectorOrigin(TASK_SELECTORS)
                                                      .build();
          task.getExecutionCapabilities().add(selectorCapability);
        }
        List<String> eligibleListOfDelegates = assignDelegateService.getEligibleDelegatesToTask(task);
        delegateSelectionLogsService.logDelegateTaskInfo(task);
        if (eligibleListOfDelegates.isEmpty()) {
          addToTaskActivityLog(task, NO_ELIGIBLE_DELEGATES);
          delegateSelectionLogsService.logNoEligibleDelegatesToExecuteTask(task);
          // TODO: add websocketAPI metrics
          // delegateMetricsService.recordDelegateTaskMetrics(task, DELEGATE_TASK_NO_ELIGIBLE_DELEGATES);
          StringBuilder errorMessage = new StringBuilder(NO_ELIGIBLE_DELEGATES);
          if (task.getNonAssignableDelegates() != null) {
            errorMessage.append(String.join(" , ", task.getNonAssignableDelegates().keySet()));
          }
          throw new NoEligibleDelegatesInAccountException(errorMessage.toString());
        }
        // shuffle the eligible delegates to evenly distribute the load
        Collections.shuffle(eligibleListOfDelegates);
        task.setBroadcastToDelegateIds(
            Lists.newArrayList(getDelegateIdForFirstBroadcast(task, eligibleListOfDelegates)));
        if (isNotEmpty(task.getEligibleToExecuteDelegateIds())) {
          // case when caller send eligibleDelegateIds where we skip assignment process, different selection log message
          delegateSelectionLogsService.logEligibleDelegatesToExecuteTask(
              Sets.newHashSet(eligibleListOfDelegates), task, true);
        } else {
          delegateSelectionLogsService.logEligibleDelegatesToExecuteTask(
              Sets.newHashSet(eligibleListOfDelegates), task, false);
        }
        // save eligible delegate ids as part of task (will be used for rebroadcasting)
        task.setEligibleToExecuteDelegateIds(new LinkedList<>(eligibleListOfDelegates));
        log.info("Assignable/eligible delegates to execute task {} are {}.", task.getUuid(),
            task.getEligibleToExecuteDelegateIds());

        // filter only connected ones from list
        List<String> connectedEligibleDelegates =
            assignDelegateService.getConnectedDelegateList(eligibleListOfDelegates, task);

        if (!task.isAsync() && connectedEligibleDelegates.isEmpty()) {
          addToTaskActivityLog(task, "No Connected eligible delegates to execute sync task");
          if (assignDelegateService.noInstalledDelegates(task.getAccountId())) {
            throw new NoInstalledDelegatesException();
          } else {
            throw new NoAvailableDelegatesException();
          }
        }
        checkTaskRankRateLimit(task);

        // Added temporarily to help to identifying tasks whose task setup abstractions need to be fixed
        verifyTaskSetupAbstractions(task);
        task.setNextBroadcast(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5));
        saveDelegateTask(task, task.getAccountId());
        delegateSelectionLogsService.logBroadcastToDelegate(Sets.newHashSet(task.getBroadcastToDelegateIds()), task);
        // delegateMetricsService.recordDelegateTaskMetrics(task, DELEGATE_TASK_CREATION);
        log.info("Task {} marked as {} with first attempt broadcast to {}", task.getUuid(), taskStatus,
            task.getBroadcastToDelegateIds());
        addToTaskActivityLog(task, "Task processing completed");
      } catch (Exception exception) {
        log.warn("Task id {} failed with error {}", task.getUuid(), exception);
        printErrorMessageOnTaskFailure(task);
        handleTaskFailureResponse(task, exception);
        if (!task.isAsync()) {
          throw exception;
        }
      }
    }
  }

  @VisibleForTesting
  @Override
  public void processDelegateTask(DelegateTask task, DelegateTask.Status taskStatus) {
    setAdditionalTaskFields(task, taskStatus);

    try (AutoLogContext ignore = DelegateLogContextHelper.getLogContext(task)) {
      try {
        // capabilities created,then appended to task.executionCapabilities to get eligible delegates
        generateCapabilitiesForTask(task);
        convertToExecutionCapability(task);

        List<String> eligibleListOfDelegates = assignDelegateService.getEligibleDelegatesToExecuteTask(task);
        delegateSelectionLogsService.logDelegateTaskInfo(task);
        if (eligibleListOfDelegates.isEmpty()) {
          addToTaskActivityLog(task, NO_ELIGIBLE_DELEGATES);
          delegateSelectionLogsService.logNoEligibleDelegatesToExecuteTask(task);
          delegateSelectionLogsService.logNonSelectedDelegates(task, task.getNonAssignableDelegates());
          delegateMetricsService.recordDelegateTaskMetrics(task, DELEGATE_TASK_NO_ELIGIBLE_DELEGATES);
          StringBuilder errorMessage = new StringBuilder(NO_ELIGIBLE_DELEGATES);
          if (task.getNonAssignableDelegates() != null) {
            errorMessage.append(String.join(" , ", task.getNonAssignableDelegates().keySet()));
          }
          throw new NoEligibleDelegatesInAccountException(errorMessage.toString());
        }
        // shuffle the eligible delegates to evenly distribute the load
        Collections.shuffle(eligibleListOfDelegates);
        task.setBroadcastToDelegateIds(
            Lists.newArrayList(getDelegateIdForFirstBroadcast(task, eligibleListOfDelegates)));
        if (isNotEmpty(task.getEligibleToExecuteDelegateIds())) {
          // case when caller send eligibleDelegateIds where we skip assignment process, different selection log message
          delegateSelectionLogsService.logEligibleDelegatesToExecuteTask(
              Sets.newHashSet(eligibleListOfDelegates), task, true);
        } else {
          delegateSelectionLogsService.logEligibleDelegatesToExecuteTask(
              Sets.newHashSet(eligibleListOfDelegates), task, false);
        }
        // save eligible delegate ids as part of task (will be used for rebroadcasting)
        task.setEligibleToExecuteDelegateIds(new LinkedList<>(eligibleListOfDelegates));
        log.info("Assignable/eligible delegates to execute task {} are {}.", task.getUuid(),
            task.getEligibleToExecuteDelegateIds() + "\n\n"
                + CapabilityHelper.generateLogStringWithSelectionCapabilitiesGenerated(
                    task.getData().getTaskType(), task.getExecutionCapabilities()));

        // filter only connected ones from list
        List<String> connectedEligibleDelegates =
            assignDelegateService.getConnectedDelegateList(eligibleListOfDelegates, task);

        if (!task.getData().isAsync() && connectedEligibleDelegates.isEmpty()) {
          addToTaskActivityLog(task, "No Connected eligible delegates to execute sync task");
          if (assignDelegateService.noInstalledDelegates(task.getAccountId())) {
            throw new NoInstalledDelegatesException();
          } else {
            throw new NoAvailableDelegatesException();
          }
        }
        checkTaskRankRateLimit(task);

        // Added temporarily to help to identifying tasks whose task setup abstractions need to be fixed
        verifyTaskSetupAbstractions(task);
        task.setNextBroadcast(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5));
        saveDelegateTask(task, task.getAccountId());
        delegateSelectionLogsService.logBroadcastToDelegate(Sets.newHashSet(task.getBroadcastToDelegateIds()), task);
        delegateMetricsService.recordDelegateTaskMetrics(task, DELEGATE_TASK_CREATION);
        log.info("Task {} marked as {} with first attempt broadcast to {}", task.getUuid(), taskStatus,
            task.getBroadcastToDelegateIds());
        addToTaskActivityLog(task, "Task processing completed");
      } catch (Exception exception) {
        log.warn("Task id {} failed with error {}", task.getUuid(), exception);
        printErrorMessageOnTaskFailure(task);
        handleTaskFailureResponse(task, exception);
        if (!task.getData().isAsync()) {
          throw exception;
        }
      }
    }
  }

  @VisibleForTesting
  @Override
  public void processDelegateTaskV2(DelegateTask task, DelegateTask.Status taskStatus) {
    setAdditionalTaskFields(task, taskStatus);

    try (AutoLogContext ignore = DelegateLogContextHelper.getLogContext(task)) {
      try {
        // capabilities created,then appended to task.executionCapabilities to get eligible delegates
        generateCapabilitiesForTaskV2(task);
        convertToExecutionCapabilityV2(task);

        List<String> eligibleListOfDelegates = assignDelegateService.getEligibleDelegatesToExecuteTaskV2(task);
        delegateSelectionLogsService.logDelegateTaskInfo(task);
        if (eligibleListOfDelegates.isEmpty()) {
          addToTaskActivityLog(task, NO_ELIGIBLE_DELEGATES);
          delegateSelectionLogsService.logNoEligibleDelegatesToExecuteTask(task);
          delegateMetricsService.recordDelegateTaskMetrics(task, DELEGATE_TASK_NO_ELIGIBLE_DELEGATES);
          StringBuilder errorMessage = new StringBuilder(NO_ELIGIBLE_DELEGATES);
          if (task.getNonAssignableDelegates() != null) {
            errorMessage.append(String.join(" , ", task.getNonAssignableDelegates().keySet()));
          }
          throw new NoEligibleDelegatesInAccountException(errorMessage.toString());
        }
        // shuffle the eligible delegates to evenly distribute the load
        Collections.shuffle(eligibleListOfDelegates);
        task.setBroadcastToDelegateIds(
            Lists.newArrayList(getDelegateIdForFirstBroadcast(task, eligibleListOfDelegates)));
        if (isNotEmpty(task.getEligibleToExecuteDelegateIds())) {
          // case when caller send eligibleDelegateIds where we skip assignment process, different selection log message
          delegateSelectionLogsService.logEligibleDelegatesToExecuteTask(
              Sets.newHashSet(eligibleListOfDelegates), task, true);
        } else {
          delegateSelectionLogsService.logEligibleDelegatesToExecuteTask(
              Sets.newHashSet(eligibleListOfDelegates), task, false);
        }
        // save eligible delegate ids as part of task (will be used for rebroadcasting)
        task.setEligibleToExecuteDelegateIds(new LinkedList<>(eligibleListOfDelegates));
        log.debug("Assignable/eligible delegates to execute task {} are {}.", task.getUuid(),
            task.getEligibleToExecuteDelegateIds() + "\n\n"
                + CapabilityHelper.generateLogStringWithSelectionCapabilitiesGenerated(
                    task.getTaskDataV2().getTaskType(), task.getExecutionCapabilities()));

        // filter only connected ones from list
        List<String> connectedEligibleDelegates =
            assignDelegateService.getConnectedDelegateList(eligibleListOfDelegates, task);

        if (!task.getTaskDataV2().isAsync() && connectedEligibleDelegates.isEmpty()) {
          addToTaskActivityLog(task, "No Connected eligible delegates to execute sync task");
          if (assignDelegateService.noInstalledDelegates(task.getAccountId())) {
            throw new NoInstalledDelegatesException();
          } else {
            throw new NoAvailableDelegatesException();
          }
        }
        checkTaskRankRateLimit(task);

        // Added temporarily to help to identifying tasks whose task setup abstractions need to be fixed
        verifyTaskSetupAbstractions(task);

        task.setNextBroadcast(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5));
        saveDelegateTask(task, task.getAccountId());
        delegateSelectionLogsService.logBroadcastToDelegate(Sets.newHashSet(task.getBroadcastToDelegateIds()), task);
        delegateMetricsService.recordDelegateTaskMetrics(task, DELEGATE_TASK_CREATION);
        log.info("Task {} marked as {} with first attempt broadcast to {}", task.getUuid(), taskStatus,
            task.getBroadcastToDelegateIds());
        addToTaskActivityLog(task, "Task processing completed");
      } catch (Exception exception) {
        log.warn("Task id {} failed with error {}", task.getUuid(), exception);
        printErrorMessageOnTaskFailure(task);
        handleTaskFailureResponseV2(task, exception);
        if (!task.getTaskDataV2().isAsync()) {
          throw exception;
        }
      }
    }
  }

  private String getDelegateIdForFirstBroadcast(DelegateTask delegateTask, List<String> eligibleListOfDelegates) {
    if (delegateTask.isNGTask(delegateTask.getSetupAbstractions())
        && featureFlagService.isEnabled(DELEGATE_TASK_LOAD_DISTRIBUTION, delegateTask.getAccountId())) {
      List<String> eligibleDelegatesSorted =
          getEligibleDelegateListOrderedNumberByTaskAssigned(delegateTask, eligibleListOfDelegates);
      delegateTask.setEligibleToExecuteDelegateIds(new LinkedList<>(eligibleDelegatesSorted));
      log.info("Eligible delegate sorted list: {}", eligibleDelegatesSorted);
      delegateMetricsService.recordDelegateTaskMetrics(delegateTask, DELEGATE_TASK_NO_FIRST_WHITELISTED);
      return eligibleDelegatesSorted.get(0);
    } else {
      for (String delegateId : eligibleListOfDelegates) {
        if (assignDelegateService.isDelegateGroupWhitelisted(delegateTask, delegateId)
            || assignDelegateService.isWhitelisted(delegateTask, delegateId)) {
          return delegateId;
        }
      }
      delegateMetricsService.recordDelegateTaskMetrics(delegateTask, DELEGATE_TASK_NO_FIRST_WHITELISTED);
      printCriteriaNoMatch(delegateTask);
      return eligibleListOfDelegates.get(random.nextInt(eligibleListOfDelegates.size()));
    }
  }

  @VisibleForTesting
  protected List<String> getEligibleDelegateListOrderedNumberByTaskAssigned(
      DelegateTask delegateTask, List<String> eligibleListOfDelegates) {
    List<DelegateTask> delegateTasks = persistence.createQuery(DelegateTask.class)
                                           .filter(DelegateTaskKeys.accountId, delegateTask.getAccountId())
                                           .filter(DelegateTaskKeys.status, STARTED)
                                           .field(DelegateTaskKeys.delegateId)
                                           .in(eligibleListOfDelegates)
                                           .project(DelegateTaskKeys.uuid, true)
                                           .project(DelegateTaskKeys.delegateId, true)
                                           .asList();
    Map<String, Integer> tasksCount =
        delegateTasks.stream().collect(groupingBy(DelegateTask::getDelegateId, summingInt(delegateTaskCount -> 1)));
    eligibleListOfDelegates.forEach(delegate -> tasksCount.computeIfAbsent(delegate, key -> 0));
    TreeMap<Integer, List<String>> taskCountToDelegates = tasksCount.entrySet().stream().collect(Collectors.groupingBy(
        Map.Entry::getValue, TreeMap::new, Collectors.mapping(Map.Entry::getKey, Collectors.toList())));
    // In order to improve randomness on delegate ids with same task counts, we apply the following
    // 1: Add first map entry value(list of delegateIds) to an output list,
    // 2: Do swap/shuffle if list has more than one delegateIds. Note we only concerned about shuffling delegateIds with
    // min count
    // 3: Add rest of map entry values to output list. 4:
    List<String> delegateIds = new ArrayList<>(taskCountToDelegates.firstEntry().getValue());
    if (delegateIds.size() > 1) {
      Collections.swap(delegateIds, 0, random.nextInt(delegateIds.size()));
      Collections.shuffle(delegateIds);
    }
    delegateIds.addAll(taskCountToDelegates.entrySet()
                           .stream()
                           .skip(1) // Skip the first entry
                           .flatMap(entry -> entry.getValue().stream())
                           .collect(Collectors.toList()));
    return delegateIds;
  }

  private void handleTaskFailureResponse(DelegateTask task, Exception exception) {
    Query<DelegateTask> taskQuery =
        persistence
            .createQuery(DelegateTask.class, delegateTaskMigrationHelper.isMigrationEnabledForTask(task.getUuid()))
            .filter(DelegateTaskKeys.accountId, task.getAccountId())
            .filter(DelegateTaskKeys.uuid, task.getUuid());
    WingsException ex = null;
    if (exception instanceof WingsException) {
      ex = (WingsException) exception;
    } else {
      log.error("Encountered unknown exception and failing task", exception);
    }
    DelegateTaskResponse response = DelegateTaskResponse.builder()
                                        .response(ErrorNotifyResponseData.builder()
                                                      .errorMessage(ExceptionUtils.getMessage(exception))
                                                      .exception(ex)
                                                      .build())
                                        .responseCode(ResponseCode.FAILED)
                                        .accountId(task.getAccountId())
                                        .build();
    delegateTaskService.handleResponse(task, taskQuery, response);
  }

  private void handleTaskFailureResponseV2(DelegateTask task, Exception exception) {
    Query<DelegateTask> taskQuery =
        persistence
            .createQuery(DelegateTask.class, delegateTaskMigrationHelper.isMigrationEnabledForTask(task.getUuid()))
            .filter(DelegateTaskKeys.accountId, task.getAccountId())
            .filter(DelegateTaskKeys.uuid, task.getUuid());
    WingsException ex = null;
    if (exception instanceof WingsException) {
      ex = (WingsException) exception;
    } else {
      log.error("Encountered unknown exception and failing task", exception);
    }
    DelegateTaskResponse response = DelegateTaskResponse.builder()
                                        .response(ErrorNotifyResponseData.builder()
                                                      .errorMessage(ExceptionUtils.getMessage(exception))
                                                      .exception(ex)
                                                      .build())
                                        .responseCode(ResponseCode.FAILED)
                                        .accountId(task.getAccountId())
                                        .build();
    delegateTaskService.handleResponseV2(task, taskQuery, response);
  }

  private void verifyTaskSetupAbstractions(DelegateTask task) {
    if (isNotBlank(task.getUuid()) && task.getData() != null && task.getData().getTaskType() != null) {
      try (AutoLogContext ignore1 = DelegateLogContextHelper.getLogContext(task)) {
        // Verify presence of Environment type, if EnvironmentId is present
        if (isNotEmpty(task.getSetupAbstractions())
            && task.getSetupAbstractions().get(Cd1SetupFields.ENV_ID_FIELD) != null
            && task.getSetupAbstractions().get(Cd1SetupFields.ENV_TYPE_FIELD) == null) {
          log.error("Missing envType setup abstraction", new RuntimeException());
        }

        // Verify presence of ServiceId, if Infrastructure Mapping is present
        if (isNotEmpty(task.getSetupAbstractions())
            && task.getSetupAbstractions().get(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD) != null
            && task.getSetupAbstractions().get(Cd1SetupFields.SERVICE_ID_FIELD) == null) {
          log.error("Missing serviceId setup abstraction", new RuntimeException());
        }
      }
    }
  }

  private void verifyTaskSetupAbstractionsV2(DelegateTask task) {
    if (isNotBlank(task.getUuid()) && task.getTaskDataV2() != null && task.getTaskDataV2().getTaskType() != null) {
      try (AutoLogContext ignore1 = DelegateLogContextHelper.getLogContext(task)) {
        // Verify presence of Environment type, if EnvironmentId is present
        if (isNotEmpty(task.getSetupAbstractions())
            && task.getSetupAbstractions().get(Cd1SetupFields.ENV_ID_FIELD) != null
            && task.getSetupAbstractions().get(Cd1SetupFields.ENV_TYPE_FIELD) == null) {
          log.error("Missing envType setup abstraction", new RuntimeException());
        }

        // Verify presence of ServiceId, if Infrastructure Mapping is present
        if (isNotEmpty(task.getSetupAbstractions())
            && task.getSetupAbstractions().get(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD) != null
            && task.getSetupAbstractions().get(Cd1SetupFields.SERVICE_ID_FIELD) == null) {
          log.error("Missing serviceId setup abstraction", new RuntimeException());
        }
      }
    }
  }

  @Override
  public String queueParkedTask(String accountId, String taskId) {
    DelegateTask task =
        persistence.createQuery(DelegateTask.class, delegateTaskMigrationHelper.isMigrationEnabledForTask(taskId))
            .filter(DelegateTaskKeys.accountId, accountId)
            .filter(DelegateTaskKeys.uuid, taskId)
            .get();

    task.getData().setAsync(true);

    try (AutoLogContext ignore1 = DelegateLogContextHelper.getLogContext(task);
         AutoLogContext ignore2 = new AccountLogContext(task.getAccountId(), OVERRIDE_ERROR)) {
      processDelegateTask(task, QUEUED);
      log.debug("Queueing parked task");
      broadcastHelper.broadcastNewDelegateTaskAsync(task);
    }
    return task.getUuid();
  }

  @Override
  public String queueParkedTaskV2(String accountId, String taskId) {
    DelegateTask task =
        persistence.createQuery(DelegateTask.class, delegateTaskMigrationHelper.isMigrationEnabledForTask(taskId))
            .filter(DelegateTaskKeys.accountId, accountId)
            .filter(DelegateTaskKeys.uuid, taskId)
            .get();

    task.getTaskDataV2().setAsync(true);

    try (AutoLogContext ignore1 = DelegateLogContextHelper.getLogContext(task);
         AutoLogContext ignore2 = new AccountLogContext(task.getAccountId(), OVERRIDE_ERROR)) {
      processDelegateTaskV2(task, QUEUED);
      broadcastHelper.broadcastNewDelegateTaskAsyncV2(task);
    }
    return task.getUuid();
  }

  @Override
  public byte[] getParkedTaskResults(String accountId, String taskId, String driverId) {
    DelegateTaskResultsProvider delegateTaskResultsProvider =
        delegateCallbackRegistry.obtainDelegateTaskResultsProvider(driverId);
    if (delegateTaskResultsProvider == null) {
      return new byte[0];
    }
    return delegateTaskResultsProvider.getDelegateTaskResults(taskId);
  }

  private void generateCapabilitiesForTask(DelegateTask task) {
    addMergedParamsForCapabilityCheck(task);

    DelegateTaskPackage delegateTaskPackage = getDelegatePackageWithEncryptionConfig(task);
    embedCapabilitiesInDelegateTask(task,
        delegateTaskPackage == null || isEmpty(delegateTaskPackage.getEncryptionConfigs())
            ? emptyList()
            : delegateTaskPackage.getEncryptionConfigs().values(),
        new ManagerPreviewExpressionEvaluator());

    if (isNotEmpty(task.getExecutionCapabilities())) {
      log.debug(CapabilityHelper.generateLogStringWithCapabilitiesGenerated(
          task.getData().getTaskType(), task.getExecutionCapabilities()));
      addToTaskActivityLog(task,
          CapabilityHelper.generateLogStringWithCapabilitiesGenerated(
              task.getData().getTaskType(), task.getExecutionCapabilities()));
    }
  }

  private void generateCapabilitiesForTaskV2(DelegateTask task) {
    addMergedParamsForCapabilityCheckV2(task);

    DelegateTaskPackage delegateTaskPackage = getDelegatePackageWithEncryptionConfigV2(task);
    embedCapabilitiesInDelegateTaskV2(task,
        delegateTaskPackage == null || isEmpty(delegateTaskPackage.getEncryptionConfigs())
            ? emptyList()
            : delegateTaskPackage.getEncryptionConfigs().values(),
        new ManagerPreviewExpressionEvaluator());

    if (isNotEmpty(task.getExecutionCapabilities())) {
      log.debug(CapabilityHelper.generateLogStringWithCapabilitiesGenerated(
          task.getTaskDataV2().getTaskType(), task.getExecutionCapabilities()));
      addToTaskActivityLog(task,
          CapabilityHelper.generateLogStringWithCapabilitiesGenerated(
              task.getTaskDataV2().getTaskType(), task.getExecutionCapabilities()));
    }
  }

  // For some of the tasks, the necessary factors to do capability check is split across multiple
  // params. So none of the params can provide the execution capability by itself. To work around this,
  // we're adding extra params that combines these split params.
  private void addMergedParamsForCapabilityCheck(DelegateTask task) {
    List<Object> newParams;
    TaskType type = TaskType.valueOf(task.getData().getTaskType());
    Object[] params = task.getData().getParameters();
    switch (type) {
      case HOST_VALIDATION:
        HostValidationTaskParameters hostValidationTaskParameters;
        if (params.length == 1 && params[0] instanceof HostValidationTaskParameters) {
          hostValidationTaskParameters = (HostValidationTaskParameters) params[0];
        } else {
          SettingAttribute settingAttribute = (SettingAttribute) params[3];
          hostValidationTaskParameters = HostValidationTaskParameters.builder()
                                             .hostNames((List<String>) params[2])
                                             .connectionSetting(settingAttribute)
                                             .encryptionDetails((List<EncryptedDataDetail>) params[4])
                                             .executionCredential((ExecutionCredential) params[5])
                                             .build();
        }

        newParams = new ArrayList<>(Arrays.asList(hostValidationTaskParameters));
        task.getData().setParameters(newParams.toArray());
        return;
      case PCF_COMMAND_TASK:
        CfCommandRequest commandRequest = (CfCommandRequest) params[0];
        if (!(commandRequest instanceof CfRunPluginCommandRequest)) {
          CfCommandTaskParametersBuilder parametersBuilder =
              CfCommandTaskParameters.builder().pcfCommandRequest(commandRequest);
          if (params.length > 1) {
            parametersBuilder.encryptedDataDetails((List<EncryptedDataDetail>) params[1]);
          }
          newParams = new ArrayList<>(Collections.singletonList(parametersBuilder.build()));
          task.getData().setParameters(newParams.toArray());
        }
        return;
      case GIT_COMMAND:
        GitConfig config = (GitConfig) params[1];
        List<EncryptedDataDetail> encryptedDataDetails = (List<EncryptedDataDetail>) params[2];
        Object[] newParamsArr = Arrays.copyOf(params, params.length + 1);
        newParamsArr[newParamsArr.length - 1] =
            GitValidationParameters.builder()
                .gitConfig(config)
                .encryptedDataDetails(encryptedDataDetails)
                .isGitHostConnectivityCheck(featureFlagService.isEnabled(GIT_HOST_CONNECTIVITY, task.getAccountId()))
                .build();
        task.getData().setParameters(newParamsArr);
        return;
      default:
        noop();
    }
  }

  private void addMergedParamsForCapabilityCheckV2(DelegateTask task) {
    List<Object> newParams;
    TaskType type = TaskType.valueOf(task.getTaskDataV2().getTaskType());
    Object[] params = task.getTaskDataV2().getParameters();
    switch (type) {
      case HOST_VALIDATION:
        HostValidationTaskParameters hostValidationTaskParameters;
        if (params.length == 1 && params[0] instanceof HostValidationTaskParameters) {
          hostValidationTaskParameters = (HostValidationTaskParameters) params[0];
        } else {
          SettingAttribute settingAttribute = (SettingAttribute) params[3];
          hostValidationTaskParameters = HostValidationTaskParameters.builder()
                                             .hostNames((List<String>) params[2])
                                             .connectionSetting(settingAttribute)
                                             .encryptionDetails((List<EncryptedDataDetail>) params[4])
                                             .executionCredential((ExecutionCredential) params[5])
                                             .build();
        }

        newParams = new ArrayList<>(Arrays.asList(hostValidationTaskParameters));
        task.getTaskDataV2().setParameters(newParams.toArray());
        return;
      case PCF_COMMAND_TASK:
        CfCommandRequest commandRequest = (CfCommandRequest) params[0];
        if (!(commandRequest instanceof CfRunPluginCommandRequest)) {
          CfCommandTaskParametersBuilder parametersBuilder =
              CfCommandTaskParameters.builder().pcfCommandRequest(commandRequest);
          if (params.length > 1) {
            parametersBuilder.encryptedDataDetails((List<EncryptedDataDetail>) params[1]);
          }
          newParams = new ArrayList<>(Collections.singletonList(parametersBuilder.build()));
          task.getTaskDataV2().setParameters(newParams.toArray());
        }
        return;
      case GIT_COMMAND:
        GitConfig config = (GitConfig) params[1];
        List<EncryptedDataDetail> encryptedDataDetails = (List<EncryptedDataDetail>) params[2];
        Object[] newParamsArr = Arrays.copyOf(params, params.length + 1);
        newParamsArr[newParamsArr.length - 1] =
            GitValidationParameters.builder()
                .gitConfig(config)
                .encryptedDataDetails(encryptedDataDetails)
                .isGitHostConnectivityCheck(featureFlagService.isEnabled(GIT_HOST_CONNECTIVITY, task.getAccountId()))
                .build();
        task.getTaskDataV2().setParameters(newParamsArr);
        return;
      default:
        noop();
    }
  }

  @Override
  public DelegateTaskPackage reportConnectionResults(String accountId, String delegateId, String taskId,
      String delegateInstanceId, List<DelegateConnectionResult> results) {
    assignDelegateService.saveConnectionResults(results);
    DelegateTask delegateTask = getUnassignedDelegateTask(accountId, taskId, delegateInstanceId);
    if (delegateTask == null) {
      return null;
    }

    String taskType = delegateTask.getData() != null ? delegateTask.getData().getTaskType()
                                                     : delegateTask.getTaskDataV2().getTaskType();
    boolean async =
        delegateTask.getData() != null ? delegateTask.getData().isAsync() : delegateTask.getTaskDataV2().isAsync();
    try (AutoLogContext ignore = DelegateLogContextHelper.getLogContext(delegateTask)) {
      log.info("Delegate completed validating {} task", async ? ASYNC : SYNC);
      boolean migrationEnabledForDelegateTask =
          delegateTaskMigrationHelper.isMigrationEnabledForTask(delegateTask.getUuid());

      UpdateOperations<DelegateTask> updateOperations =
          persistence.createUpdateOperations(DelegateTask.class, migrationEnabledForDelegateTask)
              .addToSet(DelegateTaskKeys.validationCompleteDelegateIds, delegateId);
      Query<DelegateTask> updateQuery = persistence.createQuery(DelegateTask.class, migrationEnabledForDelegateTask)
                                            .filter(DelegateTaskKeys.accountId, delegateTask.getAccountId())
                                            .filter(DelegateTaskKeys.uuid, delegateTask.getUuid())
                                            .filter(DelegateTaskKeys.status, QUEUED)
                                            .field(DelegateTaskKeys.delegateId)
                                            .doesNotExist();
      persistence.update(updateQuery, updateOperations, migrationEnabledForDelegateTask);

      long requiredDelegateCapabilities = 0;
      if (delegateTask.getExecutionCapabilities() != null) {
        requiredDelegateCapabilities = delegateTask.getExecutionCapabilities()
                                           .stream()
                                           .filter(e -> e.evaluationMode() == EvaluationMode.AGENT)
                                           .count();
      }

      // If all delegate task capabilities were evaluated and they were ok, we can assign the task
      if (requiredDelegateCapabilities == size(results)
          && results.stream().allMatch(DelegateConnectionResult::isValidated)) {
        return assignTask(delegateId, taskId, delegateTask, delegateInstanceId);
      }
    }

    return null;
  }

  @Override
  public Optional<AcquireTasksResponse> acquireTask(
      String accountId, String delegateId, String taskId, String delegateInstanceId) {
    try {
      Delegate delegate = delegateCache.get(accountId, delegateId, false);
      if (delegate == null || DelegateInstanceStatus.ENABLED != delegate.getStatus()) {
        log.warn("Delegate rejected to acquire task, because it was not found to be in {} status.",
            DelegateInstanceStatus.ENABLED);
        return Optional.empty();
      }

      // Mark task as assigned
      log.debug("Acquiring delegate task");
      DelegateTask delegateTask = getUnassignedDelegateTask(accountId, taskId, delegateInstanceId);
      if (delegateTask == null) {
        return Optional.empty();
      }
      try (AutoLogContext ignore = DelegateLogContextHelper.getLogContext(delegateTask)) {
        markTaskAssigned(accountId, delegateId, taskId, delegateInstanceId, delegateTask.getExecutionTimeout());

        // Metrics and logs
        delegateSelectionLogsService.logTaskAssigned(delegateId, delegateTask);

        // TODO: add metrics for new APIs
        // delegateMetricsService.recordDelegateTaskMetrics(delegateTask, DELEGATE_TASK_ACQUIRE);
        return Optional.of(
            AcquireTasksResponse.newBuilder()
                .addTask(TaskPayload.newBuilder()
                             .setId(taskId)
                             .setInfraData(InputData.newBuilder()
                                               .setBinaryData(ByteString.copyFrom(delegateTask.getRunnerData()))
                                               .build())
                             .setTaskData(InputData.newBuilder()
                                              .setBinaryData(ByteString.copyFrom(delegateTask.getTaskData()))
                                              .build())
                             .setResourceUri(delegateTask.getRequestUri())
                             .setResourceMethod(delegateTask.getRequestMethod())
                             .build())
                .build());
      }
    } finally {
      log.debug("Done with acquire delegate task{} ", taskId);
    }
  }

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

      String taskType = delegateTask.getData() != null ? delegateTask.getData().getTaskType()
                                                       : delegateTask.getTaskDataV2().getTaskType();
      try (AutoLogContext ignore = DelegateLogContextHelper.getLogContext(delegateTask)) {
        if (assignDelegateService.shouldValidate(delegateTask, delegateId)) {
          setValidationStarted(delegateId, delegateTask);
          return resolvePreAssignmentExpressions(delegateTask, SecretManagerMode.APPLY);
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

  @VisibleForTesting
  void setValidationStarted(String delegateId, DelegateTask delegateTask) {
    delegateMetricsService.recordDelegateTaskMetrics(delegateTask, DELEGATE_TASK_VALIDATION);
    boolean async =
        delegateTask.getData() != null ? delegateTask.getData().isAsync() : delegateTask.getTaskDataV2().isAsync();
    log.debug("Delegate to validate {} task", async ? ASYNC : SYNC);
    boolean migrationEnabledForDelegateTask =
        delegateTaskMigrationHelper.isMigrationEnabledForTask(delegateTask.getUuid());

    UpdateOperations<DelegateTask> updateOperations =
        persistence.createUpdateOperations(DelegateTask.class, migrationEnabledForDelegateTask)
            .addToSet(DelegateTaskKeys.validatingDelegateIds, delegateId);
    Query<DelegateTask> updateQuery = persistence.createQuery(DelegateTask.class, migrationEnabledForDelegateTask)
                                          .filter(DelegateTaskKeys.accountId, delegateTask.getAccountId())
                                          .filter(DelegateTaskKeys.uuid, delegateTask.getUuid())
                                          .filter(DelegateTaskKeys.status, QUEUED)
                                          .field(DelegateTaskKeys.delegateId)
                                          .doesNotExist();
    persistence.update(updateQuery, updateOperations, migrationEnabledForDelegateTask);

    persistence.update(updateQuery.field(DelegateTaskKeys.validationStartedAt).doesNotExist(),
        persistence.createUpdateOperations(DelegateTask.class, migrationEnabledForDelegateTask)
            .set(DelegateTaskKeys.validationStartedAt, clock.millis()),
        migrationEnabledForDelegateTask);
  }

  private void clearFromValidationCache(DelegateTask delegateTask) {
    boolean migrationEnabledForDelegateTask =
        delegateTaskMigrationHelper.isMigrationEnabledForTask(delegateTask.getUuid());

    UpdateOperations<DelegateTask> updateOperations =
        persistence.createUpdateOperations(DelegateTask.class, migrationEnabledForDelegateTask)
            .unset(DelegateTaskKeys.validatingDelegateIds)
            .unset(DelegateTaskKeys.validationCompleteDelegateIds);
    Query<DelegateTask> updateQuery = persistence.createQuery(DelegateTask.class, migrationEnabledForDelegateTask)
                                          .filter(DelegateTaskKeys.accountId, delegateTask.getAccountId())
                                          .filter(DelegateTaskKeys.uuid, delegateTask.getUuid())
                                          .filter(DelegateTaskKeys.status, QUEUED)
                                          .field(DelegateTaskKeys.delegateId)
                                          .doesNotExist();
    persistence.update(updateQuery, updateOperations, migrationEnabledForDelegateTask);
  }

  @VisibleForTesting
  DelegateTask getUnassignedDelegateTask(String accountId, String taskId, String delegateInstanceId) {
    DelegateTask delegateTask =
        persistence.createQuery(DelegateTask.class, delegateTaskMigrationHelper.isMigrationEnabledForTask(taskId))
            .filter(DelegateTaskKeys.accountId, accountId)
            .filter(DelegateTaskKeys.uuid, taskId)
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

  private DelegateTaskPackage resolvePreAssignmentExpressions(DelegateTask delegateTask, SecretManagerMode mode) {
    try {
      ManagerPreExecutionExpressionEvaluator managerPreExecutionExpressionEvaluator =
          new ManagerPreExecutionExpressionEvaluator(mode, serviceTemplateService, configService,
              artifactCollectionUtils, featureFlagService, managerDecryptionService, secretManager,
              delegateTask.getAccountId(), delegateTask.getWorkflowExecutionId(),
              delegateTask.getData().getExpressionFunctorToken(), ngSecretService, delegateTask.getSetupAbstractions(),
              secretsCache, delegateMetricsService, expressionEvaluatorExecutor);

      List<ExecutionCapability> executionCapabilityList = emptyList();
      if (isNotEmpty(delegateTask.getExecutionCapabilities())) {
        executionCapabilityList = delegateTask.getExecutionCapabilities()
                                      .stream()
                                      .filter(x -> x.evaluationMode() == EvaluationMode.AGENT)
                                      .collect(toList());
      }

      DelegateTaskPackageBuilder delegateTaskPackageBuilder =
          DelegateTaskPackage.builder()
              .accountId(delegateTask.getAccountId())
              .delegateId(delegateTask.getDelegateId())
              .delegateInstanceId(delegateTask.getDelegateInstanceId())
              .delegateTaskId(delegateTask.getUuid())
              .data(delegateTask.getData())
              .executionCapabilities(executionCapabilityList)
              .delegateCallbackToken(delegateTask.getDriverId());

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

      NgSecretManagerFunctor ngSecretManagerFunctor =
          (NgSecretManagerFunctor) managerPreExecutionExpressionEvaluator.getNgSecretManagerFunctor();

      SecretManagerFunctor secretManagerFunctor =
          (SecretManagerFunctor) managerPreExecutionExpressionEvaluator.getSecretManagerFunctor();

      SweepingOutputSecretFunctor sweepingOutputSecretFunctor =
          managerPreExecutionExpressionEvaluator.getSweepingOutputSecretFunctor();

      ExpressionReflectionUtils.applyExpression(delegateTask.getData().getParameters()[0], (secretMode, value) -> {
        if (value == null) {
          log.error("Unable to assign task {} due to error on ManagerPreExecutionExpressionEvaluator , value is null",
              delegateTask.getUuid());
          return null;
        }
        return managerPreExecutionExpressionEvaluator.substitute(value, new HashMap<>());
        // TODO: this code is causing the second issue in DEL-1167
        //        if (secretManagerFunctor != null && secretMode == DISALLOW_SECRETS
        //            && secretManagerFunctor.getEvaluatedSecrets().size() > 0) {
        //          throw new InvalidRequestException(format("Expression %s is not allowed to have secrets.",
        //          substituted));
        //        }
        //        return mode == CHECK_FOR_SECRETS ? value : substituted;
      });

      if (secretManagerFunctor == null && ngSecretManagerFunctor == null) {
        log.error(
            "Unable to assign task {} due to Error on ManagerPreExecutionExpressionEvaluator", delegateTask.getUuid());
        return null;
      }

      addSecretManagerFunctorConfigs(delegateTaskPackageBuilder, secretManagerFunctor, ngSecretManagerFunctor,
          sweepingOutputSecretFunctor, delegateTask.getAccountId());

      return delegateTaskPackageBuilder.build();
    } catch (CriticalExpressionEvaluationException exception) {
      log.error("Exception in ManagerPreExecutionExpressionEvaluator ", exception);
      Query<DelegateTask> taskQuery =
          persistence
              .createQuery(
                  DelegateTask.class, delegateTaskMigrationHelper.isMigrationEnabledForTask(delegateTask.getUuid()))
              .filter(DelegateTaskKeys.accountId, delegateTask.getAccountId())
              .filter(DelegateTaskKeys.uuid, delegateTask.getUuid());
      DelegateTaskResponse response =
          DelegateTaskResponse.builder()
              .response(ErrorNotifyResponseData.builder().errorMessage(ExceptionUtils.getMessage(exception)).build())
              .responseCode(ResponseCode.FAILED)
              .accountId(delegateTask.getAccountId())
              .build();
      delegateTaskService.handleResponse(delegateTask, taskQuery, response);
      if (featureFlagService.isEnabled(
              FeatureName.FAIL_WORKFLOW_IF_SECRET_DECRYPTION_FAILS, delegateTask.getAccountId())) {
        throw exception;
      }
      return null;
    }
  }

  private DelegateTaskPackage resolvePreAssignmentExpressionsV2(DelegateTask delegateTask, SecretManagerMode mode) {
    try {
      ManagerPreExecutionExpressionEvaluator managerPreExecutionExpressionEvaluator =
          new ManagerPreExecutionExpressionEvaluator(mode, serviceTemplateService, configService,
              artifactCollectionUtils, featureFlagService, managerDecryptionService, secretManager,
              delegateTask.getAccountId(), delegateTask.getWorkflowExecutionId(),
              delegateTask.getTaskDataV2().getExpressionFunctorToken(), ngSecretService,
              delegateTask.getSetupAbstractions(), secretsCache, delegateMetricsService, expressionEvaluatorExecutor);

      List<ExecutionCapability> executionCapabilityList = emptyList();
      if (isNotEmpty(delegateTask.getExecutionCapabilities())) {
        executionCapabilityList = delegateTask.getExecutionCapabilities()
                                      .stream()
                                      .filter(x -> x.evaluationMode() == EvaluationMode.AGENT)
                                      .collect(toList());
      }

      //  copyTaskDataV2ToTaskData(delegateTask);

      DelegateTaskPackageBuilder delegateTaskPackageBuilder =
          DelegateTaskPackage.builder()
              .accountId(delegateTask.getAccountId())
              .delegateId(delegateTask.getDelegateId())
              .delegateInstanceId(delegateTask.getDelegateInstanceId())
              .delegateTaskId(delegateTask.getUuid())
              .taskDataV2(delegateTask.getTaskDataV2())
              .executionCapabilities(executionCapabilityList)
              .delegateCallbackToken(delegateTask.getDriverId());

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
          log.warn(
              "Unable to retrieve the log streaming service account token, while preparing delegate task package", e);
          throw new InvalidRequestException("Please ensure log service is running.");
        }

        delegateTaskPackageBuilder.logStreamingAbstractions(delegateTask.getLogStreamingAbstractions());
      }

      if (delegateTask.getTaskDataV2().getParameters() == null
          || delegateTask.getTaskDataV2().getParameters().length != 1
          || !(delegateTask.getTaskDataV2().getParameters()[0] instanceof TaskParameters)) {
        return delegateTaskPackageBuilder.build();
      }

      NgSecretManagerFunctor ngSecretManagerFunctor =
          (NgSecretManagerFunctor) managerPreExecutionExpressionEvaluator.getNgSecretManagerFunctor();

      SecretManagerFunctor secretManagerFunctor =
          (SecretManagerFunctor) managerPreExecutionExpressionEvaluator.getSecretManagerFunctor();

      SweepingOutputSecretFunctor sweepingOutputSecretFunctor =
          managerPreExecutionExpressionEvaluator.getSweepingOutputSecretFunctor();

      ExpressionReflectionUtils.applyExpression(
          delegateTask.getTaskDataV2().getParameters()[0], (secretMode, value) -> {
            if (value == null) {
              log.error(
                  "Unable to assign task {} due to error on ManagerPreExecutionExpressionEvaluator , value is null",
                  delegateTask.getUuid());
              return null;
            }
            return managerPreExecutionExpressionEvaluator.substitute(value, new HashMap<>());
            // TODO: this code is causing the second issue in DEL-1167
            //        if (secretManagerFunctor != null && secretMode == DISALLOW_SECRETS
            //            && secretManagerFunctor.getEvaluatedSecrets().size() > 0) {
            //          throw new InvalidRequestException(format("Expression %s is not allowed to have secrets.",
            //          substituted));
            //        }
            //        return mode == CHECK_FOR_SECRETS ? value : substituted;
          });

      if (secretManagerFunctor == null && ngSecretManagerFunctor == null) {
        log.error(
            "Unable to assign task {} due to Error on ManagerPreExecutionExpressionEvaluator", delegateTask.getUuid());
        return null;
      }

      addSecretManagerFunctorConfigs(delegateTaskPackageBuilder, secretManagerFunctor, ngSecretManagerFunctor,
          sweepingOutputSecretFunctor, delegateTask.getAccountId());

      return delegateTaskPackageBuilder.build();
    } catch (CriticalExpressionEvaluationException exception) {
      log.error("Exception in ManagerPreExecutionExpressionEvaluator ", exception);
      Query<DelegateTask> taskQuery =
          persistence
              .createQuery(
                  DelegateTask.class, delegateTaskMigrationHelper.isMigrationEnabledForTask(delegateTask.getUuid()))
              .filter(DelegateTaskKeys.accountId, delegateTask.getAccountId())
              .filter(DelegateTaskKeys.uuid, delegateTask.getUuid());
      DelegateTaskResponse response =
          DelegateTaskResponse.builder()
              .response(ErrorNotifyResponseData.builder().errorMessage(ExceptionUtils.getMessage(exception)).build())
              .responseCode(ResponseCode.FAILED)
              .accountId(delegateTask.getAccountId())
              .build();
      delegateTaskService.handleResponseV2(delegateTask, taskQuery, response);
      if (featureFlagService.isEnabled(
              FeatureName.FAIL_WORKFLOW_IF_SECRET_DECRYPTION_FAILS, delegateTask.getAccountId())) {
        throw exception;
      }
      return null;
    }
  }

  private void addSecretManagerFunctorConfigs(DelegateTaskPackageBuilder delegateTaskPackageBuilder,
      SecretManagerFunctor secretManagerFunctor, NgSecretManagerFunctor ngSecretManagerFunctor,
      SweepingOutputSecretFunctor sweepingOutputSecretFunctor, String accountID) {
    Map<String, EncryptionConfig> encryptionConfigs = new HashMap<>();
    Map<String, SecretDetail> secretDetails = new HashMap<>();
    Set<String> secrets = new HashSet<>();

    if (secretManagerFunctor != null) {
      encryptionConfigs.putAll(secretManagerFunctor.getEncryptionConfigs());
      secretDetails.putAll(secretManagerFunctor.getSecretDetails());
      if (isNotEmpty(secretManagerFunctor.getEvaluatedSecrets())) {
        secrets.addAll(secretManagerFunctor.getEvaluatedSecrets().values());
      }
    }

    if (ngSecretManagerFunctor != null) {
      encryptionConfigs.putAll(ngSecretManagerFunctor.getEncryptionConfigs());
      secretDetails.putAll(ngSecretManagerFunctor.getSecretDetails());
      if (isNotEmpty(ngSecretManagerFunctor.getEvaluatedSecrets())) {
        secrets.addAll(ngSecretManagerFunctor.getEvaluatedSecrets().values());
      }
    }

    if (sweepingOutputSecretFunctor != null) {
      if (isNotEmpty(sweepingOutputSecretFunctor.getEvaluatedSecrets())) {
        secrets.addAll(sweepingOutputSecretFunctor.getEvaluatedSecrets());
      }
    }

    delegateTaskPackageBuilder.encryptionConfigs(encryptionConfigs);
    delegateTaskPackageBuilder.secretDetails(secretDetails);
    delegateTaskPackageBuilder.secrets(secrets);
  }

  private DelegateTaskPackage getDelegatePackageWithEncryptionConfig(DelegateTask delegateTask) {
    if (CapabilityHelper.isTaskParameterType(delegateTask.getData())) {
      return resolvePreAssignmentExpressions(delegateTask, SecretManagerMode.DRY_RUN);
    } else {
      // TODO: Ideally we should not land here, as we should always be passing TaskParameter only for
      // TODO: delegate task. But for now, this is needed. (e.g. Tasks containing Jenkinsonfig, BambooConfig etc.)
      Map<String, EncryptionConfig> encryptionConfigMap =
          CapabilityHelper.fetchEncryptionDetailsListFromParameters(delegateTask.getData());

      return DelegateTaskPackage.builder()
          .accountId(delegateTask.getAccountId())
          .delegateId(delegateTask.getDelegateId())
          .delegateTaskId(delegateTask.getUuid())
          .data(delegateTask.getData())
          .encryptionConfigs(encryptionConfigMap)
          .build();
    }
  }

  private DelegateTaskPackage getDelegatePackageWithEncryptionConfigV2(DelegateTask delegateTask) {
    if (CapabilityHelper.isTaskParameterTypeV2(delegateTask.getTaskDataV2())) {
      return resolvePreAssignmentExpressionsV2(delegateTask, SecretManagerMode.DRY_RUN);
    } else {
      // TODO: Ideally we should not land here, as we should always be passing TaskParameter only for
      // TODO: delegate task. But for now, this is needed. (e.g. Tasks containing Jenkinsonfig, BambooConfig etc.)
      Map<String, EncryptionConfig> encryptionConfigMap =
          CapabilityHelper.fetchEncryptionDetailsListFromParametersV2(delegateTask.getTaskDataV2());
      copyTaskDataV2ToTaskData(delegateTask);
      return DelegateTaskPackage.builder()
          .accountId(delegateTask.getAccountId())
          .delegateId(delegateTask.getDelegateId())
          .delegateTaskId(delegateTask.getUuid())
          .data(delegateTask.getData())
          .encryptionConfigs(encryptionConfigMap)
          .build();
    }
  }

  @Override
  public void publishTaskProgressResponse(
      String accountId, String driverId, String delegateTaskId, DelegateProgressData responseData) {
    DelegateCallbackService delegateCallbackService = delegateCallbackRegistry.obtainDelegateCallbackService(driverId);
    if (delegateCallbackService == null) {
      return;
    }
    delegateCallbackService.publishTaskProgressResponse(
        delegateTaskId, generateUuid(), referenceFalseKryoSerializer.asDeflatedBytes(responseData));
  }

  /**
   * Mark task denoted by accountId, delegateId, taskId as assigned. This means change status to STARTED and insert
   * delegateInstanceId.
   * @param accountId
   * @param delegateId
   * @param taskId
   * @param delegateInstanceId
   * @param executionTimeout
   */
  private void markTaskAssigned(
      String accountId, String delegateId, String taskId, String delegateInstanceId, long executionTimeout) {
    log.debug("Assigning task {} to delegate {}", taskId, delegateId);
    boolean migrationEnabledForDelegateTask = delegateTaskMigrationHelper.isMigrationEnabledForTask(taskId);

    Query<DelegateTask> query = persistence.createQuery(DelegateTask.class, migrationEnabledForDelegateTask)
                                    .filter(DelegateTaskKeys.accountId, accountId)
                                    .filter(DelegateTaskKeys.uuid, taskId)
                                    .filter(DelegateTaskKeys.status, QUEUED)
                                    .field(DelegateTaskKeys.delegateId)
                                    .doesNotExist()
                                    .field(DelegateTaskKeys.delegateInstanceId)
                                    .doesNotExist()
                                    .project(DelegateTaskKeys.taskData, false)
                                    .project(DelegateTaskKeys.runnerData, false);

    UpdateOperations<DelegateTask> updateOperations =
        persistence.createUpdateOperations(DelegateTask.class, migrationEnabledForDelegateTask)
            .set(DelegateTaskKeys.delegateId, delegateId)
            .set(DelegateTaskKeys.delegateInstanceId, delegateInstanceId)
            .set(DelegateTaskKeys.status, STARTED)
            .set(DelegateTaskKeys.expiry, currentTimeMillis() + executionTimeout);
    DelegateTask task = persistence.findAndModifySystemData(
        query, updateOperations, HPersistence.returnNewOptions, migrationEnabledForDelegateTask);
    if (task != null) {
      try (
          DelayLogContext ignore = new DelayLogContext(task.getLastUpdatedAt() - task.getCreatedAt(), OVERRIDE_ERROR)) {
        log.info("Task assigned to delegate");
      }
      return;
    }
    task = persistence.createQuery(DelegateTask.class, migrationEnabledForDelegateTask)
               .filter(DelegateTaskKeys.accountId, accountId)
               .filter(DelegateTaskKeys.uuid, taskId)
               .filter(DelegateTaskKeys.status, STARTED)
               .filter(DelegateTaskKeys.delegateId, delegateId)
               .filter(DelegateTaskKeys.delegateInstanceId, delegateInstanceId)
               .project(DelegateTaskKeys.data_parameters, false)
               .get();
    if (task == null) {
      log.debug("Task no longer available for delegate");
    }
  }

  @VisibleForTesting
  DelegateTaskPackage assignTask(
      String delegateId, String taskId, DelegateTask delegateTask, String delegateInstanceId) {
    // Clear pending validations. No longer need to track since we're assigning.
    clearFromValidationCache(delegateTask);
    // QUESTION? Do we need a metric for this
    log.debug("Assigning {} task to delegate", delegateTask.getData().isAsync() ? ASYNC : SYNC);
    boolean migrationEnabledForDelegateTask = delegateTaskMigrationHelper.isMigrationEnabledForTask(taskId);

    Query<DelegateTask> query = persistence.createQuery(DelegateTask.class, migrationEnabledForDelegateTask)
                                    .filter(DelegateTaskKeys.accountId, delegateTask.getAccountId())
                                    .filter(DelegateTaskKeys.uuid, taskId)
                                    .filter(DelegateTaskKeys.status, QUEUED)
                                    .field(DelegateTaskKeys.delegateId)
                                    .doesNotExist()
                                    .field(DelegateTaskKeys.delegateInstanceId)
                                    .doesNotExist()
                                    .project(DelegateTaskKeys.data_parameters, false);
    UpdateOperations<DelegateTask> updateOperations =
        persistence.createUpdateOperations(DelegateTask.class, migrationEnabledForDelegateTask)
            .set(DelegateTaskKeys.delegateId, delegateId)
            .set(DelegateTaskKeys.delegateInstanceId, delegateInstanceId)
            .set(DelegateTaskKeys.status, STARTED)
            .set(DelegateTaskKeys.expiry, currentTimeMillis() + delegateTask.getData().getTimeout());
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

        managerObserverEventProducer.sendEvent(
            ReflectionUtils.getMethod(CIDelegateTaskObserver.class, "onTaskAssigned", String.class, String.class,
                String.class, String.class, String.class),
            DelegateTaskServiceClassicImpl.class, delegateTask.getAccountId(), taskId, delegateId,
            delegateTask.getStageId(), taskType);
      }

      delegateMetricsService.recordDelegateTaskMetrics(delegateTask, DELEGATE_TASK_ACQUIRE);

      return resolvePreAssignmentExpressions(task, SecretManagerMode.APPLY);
    }
    task = persistence.createQuery(DelegateTask.class, migrationEnabledForDelegateTask)
               .filter(DelegateTaskKeys.accountId, delegateTask.getAccountId())
               .filter(DelegateTaskKeys.uuid, taskId)
               .filter(DelegateTaskKeys.status, STARTED)
               .filter(DelegateTaskKeys.delegateId, delegateId)
               .filter(DelegateTaskKeys.delegateInstanceId, delegateInstanceId)
               .project(DelegateTaskKeys.data_parameters, false)
               .get();
    if (task == null) {
      log.debug("Task no longer available for delegate");
      return null;
    }

    task.getData().setParameters(delegateTask.getData().getParameters());
    log.info("Returning previously assigned task to delegate");
    return resolvePreAssignmentExpressions(task, SecretManagerMode.APPLY);
  }

  @Override
  public boolean filter(String delegateId, DelegateTaskAbortEvent taskAbortEvent) {
    return persistence
               .createQuery(DelegateTask.class,
                   delegateTaskMigrationHelper.isMigrationEnabledForTask(taskAbortEvent.getDelegateTaskId()))
               .filter(DelegateTaskKeys.accountId, taskAbortEvent.getAccountId())
               .filter(DelegateTaskKeys.uuid, taskAbortEvent.getDelegateTaskId())
               .filter(DelegateTaskKeys.delegateId, delegateId)
               .getKey()
        != null;
  }

  @Override
  public String expireTask(String accountId, String delegateTaskId) {
    String errorMessage = null;
    try (AutoLogContext ignore1 = new TaskLogContext(delegateTaskId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      if (delegateTaskId == null) {
        log.warn("Delegate task id was null", new IllegalArgumentException());
        return errorMessage;
      }
      log.info("Expiring delegate task");
      Query<DelegateTask> delegateTaskQuery = getRunningTaskQuery(accountId, delegateTaskId);

      DelegateTask delegateTask = delegateTaskQuery.get();
      if (delegateTask != null) {
        try (AutoLogContext ignore3 = DelegateLogContextHelper.getLogContext(delegateTask)) {
          errorMessage =
              "Task expired. " + assignDelegateService.getActiveDelegateAssignmentErrorMessage(EXPIRED, delegateTask);
          delegateMetricsService.recordDelegateTaskMetrics(delegateTask, DELEGATE_TASK_EXPIRED);
          log.info("Marking task as expired: {}", errorMessage);

          if (isNotBlank(delegateTask.getWaitId())) {
            waitNotifyEngine.doneWith(delegateTask.getWaitId(),
                ErrorNotifyResponseData.builder()
                    .errorMessage(errorMessage)
                    .expired(true)
                    .exception(new DelegateTaskExpiredException(delegateTaskId))
                    .build());
          }
        }
      }

      endTask(accountId, delegateTaskId, delegateTaskQuery, ERROR);
    }
    return errorMessage;
  }

  @Override
  public String expireTaskV2(String accountId, String delegateTaskId) {
    String errorMessage = null;
    try (AutoLogContext ignore1 = new TaskLogContext(delegateTaskId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      if (delegateTaskId == null) {
        log.warn("Delegate task id was null", new IllegalArgumentException());
        return errorMessage;
      }
      log.info("Expiring delegate task");
      Query<DelegateTask> delegateTaskQuery = getRunningTaskQueryV2(accountId, delegateTaskId);

      DelegateTask delegateTask =
          delegateTaskQuery.asList().stream().filter(task -> task.getTaskDataV2().isAsync()).findFirst().orElse(null);
      if (delegateTask != null) {
        try (AutoLogContext ignore3 = DelegateLogContextHelper.getLogContext(delegateTask)) {
          errorMessage =
              "Task expired. " + assignDelegateService.getActiveDelegateAssignmentErrorMessage(EXPIRED, delegateTask);
          delegateMetricsService.recordDelegateTaskMetrics(delegateTask, DELEGATE_TASK_EXPIRED);
          log.info("Marking task as expired: {}", errorMessage);

          if (isNotBlank(delegateTask.getWaitId())) {
            waitNotifyEngine.doneWith(delegateTask.getWaitId(),
                ErrorNotifyResponseData.builder()
                    .errorMessage(errorMessage)
                    .expired(true)
                    .exception(new DelegateTaskExpiredException(delegateTaskId))
                    .build());
          }
        }
      }

      endTaskV2(accountId, delegateTaskId, delegateTaskQuery, ERROR);
    }
    return errorMessage;
  }

  @Override
  public DelegateTask abortTask(String accountId, String delegateTaskId) {
    try (AutoLogContext ignore1 = new TaskLogContext(delegateTaskId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      if (delegateTaskId == null) {
        log.warn("Delegate task id was null", new IllegalArgumentException());
        return null;
      }
      log.info("Aborting delegate task");

      persistence.save(DelegateSyncTaskResponse.builder()
                           .uuid(delegateTaskId)
                           .responseData(kryoSerializer.asDeflatedBytes(
                               ErrorNotifyResponseData.builder().errorMessage("Delegate task was aborted").build()))
                           .build());

      return endTask(accountId, delegateTaskId, getRunningTaskQuery(accountId, delegateTaskId), ABORTED);
    }
  }

  @Override
  public DelegateTask abortTaskV2(String accountId, String delegateTaskId) {
    try (AutoLogContext ignore1 = new TaskLogContext(delegateTaskId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      if (delegateTaskId == null) {
        log.warn("Delegate task id was null", new IllegalArgumentException());
        return null;
      }
      log.info("Aborting delegate task");

      persistence.save(DelegateSyncTaskResponse.builder()
                           .uuid(delegateTaskId)
                           .responseData(kryoSerializer.asDeflatedBytes(
                               ErrorNotifyResponseData.builder().errorMessage("Delegate task was aborted").build()))
                           .build());

      DelegateTask abortedDelegateTask =
          endTaskV2(accountId, delegateTaskId, getRunningTaskQueryV2(accountId, delegateTaskId), ABORTED);
      // if task is not in DB then check if task is in the queue
      if (abortedDelegateTask == null) {
        delegateCache.addToAbortedTaskList(accountId, Sets.newHashSet(delegateTaskId));
      }
      return abortedDelegateTask;
    }
  }

  private DelegateTask endTask(
      String accountId, String delegateTaskId, Query<DelegateTask> delegateTaskQuery, DelegateTask.Status status) {
    boolean migrationEnabledForDelegateTask = delegateTaskMigrationHelper.isMigrationEnabledForTask(delegateTaskId);
    UpdateOperations updateOperations =
        persistence.createUpdateOperations(DelegateTask.class, migrationEnabledForDelegateTask)
            .set(DelegateTaskKeys.status, status);

    DelegateTask oldTask = persistence.findAndModify(
        delegateTaskQuery, updateOperations, HPersistence.returnOldOptions, migrationEnabledForDelegateTask);
    if (oldTask != null) {
      failDelegateTaskIteratorHelper.logValidationFailedErrorsInSelectionLog(oldTask);
    }
    broadcasterFactory.lookup(STREAM_DELEGATE + accountId, true)
        .broadcast(aDelegateTaskAbortEvent().withAccountId(accountId).withDelegateTaskId(delegateTaskId).build());

    return oldTask;
  }

  private DelegateTask endTaskV2(
      String accountId, String delegateTaskId, Query<DelegateTask> delegateTaskQuery, DelegateTask.Status status) {
    boolean migrationEnabledForDelegateTask = delegateTaskMigrationHelper.isMigrationEnabledForTask(delegateTaskId);

    UpdateOperations updateOperations =
        persistence.createUpdateOperations(DelegateTask.class, migrationEnabledForDelegateTask)
            .set(DelegateTaskKeys.status, status);

    DelegateTask oldTask =
        delegateTaskQuery.asList().stream().filter(task -> task.getTaskDataV2().isAsync()).findFirst().orElse(null);
    if (oldTask != null) {
      persistence.update(oldTask, updateOperations, migrationEnabledForDelegateTask);
      failDelegateTaskIteratorHelper.logValidationFailedErrorsInSelectionLog(oldTask);
      broadcasterFactory.lookup(STREAM_DELEGATE + accountId, true)
          .broadcast(aDelegateTaskAbortEvent().withAccountId(accountId).withDelegateTaskId(delegateTaskId).build());
    }
    return oldTask;
  }

  private Query<DelegateTask> getRunningTaskQuery(String accountId, String delegateTaskId) {
    return persistence
        .createQuery(DelegateTask.class, delegateTaskMigrationHelper.isMigrationEnabledForTask(delegateTaskId))
        .filter(DelegateTaskKeys.uuid, delegateTaskId)
        .filter(DelegateTaskKeys.accountId, accountId)
        .filter(DelegateTaskKeys.data_async, Boolean.TRUE)
        .field(DelegateTaskKeys.status)
        .in(runningStatuses());
  }

  private Query<DelegateTask> getRunningTaskQueryV2(String accountId, String delegateTaskId) {
    return persistence
        .createQuery(DelegateTask.class, delegateTaskMigrationHelper.isMigrationEnabledForTask(delegateTaskId))
        .filter(DelegateTaskKeys.uuid, delegateTaskId)
        .filter(DelegateTaskKeys.accountId, accountId)
        .field(DelegateTaskKeys.taskDataV2)
        .exists()
        .field(DelegateTaskKeys.status)
        .in(runningStatuses());
  }

  @Override
  public List<DelegateTaskEvent> getDelegateTaskEvents(String accountId, String delegateId, boolean syncOnly) {
    List<DelegateTaskEvent> delegateTaskEvents = new ArrayList<>(getQueuedEvents(accountId, delegateId, true));
    if (!syncOnly) {
      delegateTaskEvents.addAll(getQueuedEvents(accountId, delegateId, false));
      delegateTaskEvents.addAll(getAbortedEvents(accountId, delegateId));
    }

    log.debug("Dispatched delegateTaskIds: {}",
        StringUtils.join(
            ",", delegateTaskEvents.stream().map(DelegateTaskEvent::getDelegateTaskId).collect(Collectors.toList())));

    return delegateTaskEvents;
  }

  private List<DelegateTaskEvent> getQueuedEvents(String accountId, String delegateId, boolean sync) {
    List<DelegateTask> delegateTasks = getQueuedDelegateTasks(accountId, delegateId, sync, false);

    if (delegateTaskMigrationHelper.isDelegateTaskMigrationEnabled()) {
      delegateTasks.addAll(getQueuedDelegateTasks(accountId, delegateId, sync, true));
    }

    return delegateTasks.stream()
        .map(delegateTask
            -> aDelegateTaskEvent()
                   .withAccountId(accountId)
                   .withDelegateTaskId(delegateTask.getUuid())
                   .withSync(sync)
                   .withTaskType(delegateTask.getData().getTaskType())
                   .build())
        .collect(toList());
  }

  private List<DelegateTask> getQueuedDelegateTasks(
      String accountId, String delegateId, boolean sync, boolean isDelegateTaskMigrationEnabled) {
    // TODO - add assignment filter here (scopes. selectors, ...)
    Query<DelegateTask> delegateTaskQuery = persistence.createQuery(DelegateTask.class, isDelegateTaskMigrationEnabled)
                                                .filter(DelegateTaskKeys.accountId, accountId)
                                                .filter(DelegateTaskKeys.status, QUEUED)
                                                .field(DelegateTaskKeys.delegateId)
                                                .doesNotExist()
                                                .field(DelegateTaskKeys.expiry)
                                                .greaterThan(currentTimeMillis());
    return delegateTaskQuery.asList()
        .stream()
        .map(this::copyTaskDataV2ToTaskData)
        .filter(delegateTask -> !sync == delegateTask.getData().isAsync())
        .filter(delegateTask -> delegateTask.getEligibleToExecuteDelegateIds().contains(delegateId))
        .collect(toList());
  }

  private List<DelegateTaskEvent> getAbortedEvents(String accountId, String delegateId) {
    List<DelegateTask> delegateTasks = getAbortedDelegateTasks(accountId, delegateId, false);

    if (delegateTaskMigrationHelper.isDelegateTaskMigrationEnabled()) {
      delegateTasks.addAll(getAbortedDelegateTasks(accountId, delegateId, true));
    }

    return delegateTasks.stream()
        .map(delegateTask
            -> aDelegateTaskAbortEvent()
                   .withAccountId(delegateTask.getAccountId())
                   .withDelegateTaskId(delegateTask.getUuid())
                   .withSync(false)
                   .build())
        .collect(toList());
  }

  private List<DelegateTask> getAbortedDelegateTasks(
      String accountId, String delegateId, boolean isDelegateTaskMigrationEnabled) {
    Query<DelegateTask> abortedQuery = persistence.createQuery(DelegateTask.class, isDelegateTaskMigrationEnabled)
                                           .filter(DelegateTaskKeys.accountId, accountId)
                                           .filter(DelegateTaskKeys.status, ABORTED)
                                           .filter(DelegateTaskKeys.delegateId, delegateId);

    UpdateOperations<DelegateTask> updateOperations =
        persistence.createUpdateOperations(DelegateTask.class, isDelegateTaskMigrationEnabled)
            .unset(DelegateTaskKeys.delegateId);

    List<DelegateTask> delegateTasks = abortedQuery.asList()
                                           .stream()
                                           .map(this::copyTaskDataV2ToTaskData)
                                           .filter(delegateTask -> delegateTask.getData().isAsync())
                                           .collect(Collectors.toList());

    // Send abort event only once by clearing delegateId
    delegateTasks.stream().forEach(
        delegateTask -> persistence.update(delegateTask, updateOperations, isDelegateTaskMigrationEnabled));

    return delegateTasks;
  }

  private String getVersion() {
    return versionInfoManager.getVersionInfo().getVersion();
  }

  @Override
  public void deleteByAccountId(String accountId) {
    log.info("Account {} deleted. Deleting all delegate tasks owned by account.", accountId);
    persistence.deleteOnServer(
        persistence.createQuery(DelegateTask.class).filter(DelegateTaskKeys.accountId, accountId));

    if (delegateTaskMigrationHelper.isDelegateTaskMigrationEnabled()) {
      persistence.deleteOnServer(
          persistence.createQuery(DelegateTask.class, true).filter(DelegateTaskKeys.accountId, accountId), true);
    }
  }

  @Override
  public Optional<DelegateTask> fetchDelegateTask(String accountId, String taskId) {
    return Optional.ofNullable(
        persistence.createQuery(DelegateTask.class, delegateTaskMigrationHelper.isMigrationEnabledForTask(taskId))
            .filter(DelegateTaskKeys.accountId, accountId)
            .filter(DelegateTaskKeys.uuid, taskId)
            .get());
  }

  @VisibleForTesting
  protected String retrieveLogStreamingAccountToken(String accountId) throws IOException {
    return SafeHttpCall.executeWithExceptions(logStreamingServiceRestClient.retrieveAccountToken(
        mainConfiguration.getLogStreamingServiceConfig().getServiceToken(), accountId));
  }

  @VisibleForTesting
  void handleDriverResponse(DelegateTask delegateTask, DelegateTaskResponse response) {
    if (delegateTask == null || response == null) {
      return;
    }

    DelegateCallbackService delegateCallbackService =
        delegateCallbackRegistry.obtainDelegateCallbackService(delegateTask.getDriverId());
    if (delegateCallbackService == null) {
      return;
    }

    try (DelegateDriverLogContext driverLogContext =
             new DelegateDriverLogContext(delegateTask.getDriverId(), OVERRIDE_ERROR);
         TaskLogContext taskLogContext = new TaskLogContext(delegateTask.getUuid(), OVERRIDE_ERROR)) {
      if (delegateTask.getData().isAsync()) {
        log.debug("Publishing async task response...");
        delegateCallbackService.publishAsyncTaskResponse(
            delegateTask.getUuid(), referenceFalseKryoSerializer.asDeflatedBytes(response.getResponse()));
      } else {
        log.debug("Publishing sync task response...");
        delegateCallbackService.publishSyncTaskResponse(
            delegateTask.getUuid(), referenceFalseKryoSerializer.asDeflatedBytes(response.getResponse()));
      }
    } catch (Exception ex) {
      log.error("Failed publishing task response for task", ex);
    }
  }

  @Override
  public boolean checkDelegateConnected(String accountId, String delegateId) {
    return delegateDao.checkDelegateConnected(accountId, delegateId, versionInfoManager.getVersionInfo().getVersion());
  }

  @Override
  public void markAllTasksFailedForDelegate(String accountId, String delegateId) {
    List<DelegateTask> delegateTasks = getDelegateTasksForFailing(accountId, delegateId, false);

    if (delegateTaskMigrationHelper.isDelegateTaskMigrationEnabled()) {
      delegateTasks.addAll(getDelegateTasksForFailing(accountId, delegateId, true));
    }

    if (isEmpty(delegateTasks)) {
      return;
    }
    log.warn("Marking delegate tasks {} failed since delegate went down before completion.",
        delegateTasks.stream().map(DelegateTask::getUuid).collect(Collectors.toList()));
    Delegate delegate = delegateCache.get(accountId, delegateId, false);
    final String delegateName = isNotEmpty(delegate.getHostName()) ? delegate.getHostName() : delegate.getUuid();
    final String errorMessage = "Delegate [" + delegateName + "] disconnected while executing the task";
    final DelegateTaskResponse delegateTaskResponse =
        DelegateTaskResponse.builder()
            .responseCode(ResponseCode.FAILED)
            .accountId(accountId)
            .response(ErrorNotifyResponseData.builder()
                          .failureTypes(EnumSet.of(DELEGATE_RESTART))
                          .errorMessage(errorMessage)
                          .exception(new DelegateNotAvailableException(errorMessage))
                          .delegateMetaInfo(DelegateMetaInfo.builder().id(delegateId).build())
                          .build())
            .build();
    delegateTasks.forEach(delegateTask -> {
      delegateTaskService.processDelegateResponse(accountId, delegateId, delegateTask.getUuid(), delegateTaskResponse);
    });
  }

  private List<DelegateTask> getDelegateTasksForFailing(
      String accountId, String delegateId, boolean isDelegateTaskMigrationEnabled) {
    return persistence.createQuery(DelegateTask.class, isDelegateTaskMigrationEnabled)
        .filter(DelegateTaskKeys.accountId, accountId)
        .filter(DelegateTaskKeys.delegateId, delegateId)
        .filter(DelegateTaskKeys.status, STARTED)
        .asList();
  }

  public void addToTaskActivityLog(DelegateTask task, String message) {
    if (task.getTaskActivityLogs() == null) {
      task.setTaskActivityLogs(Lists.newArrayList());
    }
    task.getTaskActivityLogs().add(message);
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

  @Override
  public String saveAndBroadcastDelegateTaskV2(DelegateTask delegateTask) {
    if (delegateTask.getUuid() == null) {
      delegateTask.setUuid(delegateTaskMigrationHelper.generateDelegateTaskUUID());
    }
    delegateTask.setBroadcastToDelegateIds(Lists.newArrayList(delegateTask.getEligibleToExecuteDelegateIds().get(0)));
    delegateTask.setNextBroadcast(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5));
    String key =
        persistence.save(delegateTask, delegateTaskMigrationHelper.isMigrationEnabledForTask(delegateTask.getUuid()));
    // no need to broadcast if list is empty, empty for the case when task is queued in queue service
    if (isEmpty(delegateTask.getBroadcastToDelegateIds())) {
      return key;
    }
    if (delegateTask.getTaskDataV2().isAsync()) {
      broadcastHelper.broadcastNewDelegateTaskAsyncV2(delegateTask);
    } else {
      broadcastHelper.rebroadcastDelegateTaskV2(delegateTask);
    }
    return key;
  }

  @Override
  public String saveAndBroadcastDelegateTask(DelegateTask delegateTask) {
    if (delegateTask.getUuid() == null) {
      delegateTask.setUuid(delegateTaskMigrationHelper.generateDelegateTaskUUID());
    }
    delegateTask.setBroadcastToDelegateIds(Lists.newArrayList(delegateTask.getEligibleToExecuteDelegateIds().get(0)));
    delegateTask.setNextBroadcast(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5));
    String taskId =
        persistence.save(delegateTask, delegateTaskMigrationHelper.isMigrationEnabledForTask(delegateTask.getUuid()));
    // no need to broadcast if list is empty, empty for the case when task is queued in queue service
    if (isEmpty(delegateTask.getBroadcastToDelegateIds())) {
      return taskId;
    }
    if (delegateTask.getData().isAsync()) {
      broadcastHelper.broadcastNewDelegateTaskAsync(delegateTask);
    } else {
      broadcastHelper.rebroadcastDelegateTask(delegateTask);
    }
    return taskId;
  }

  private void printErrorMessageOnTaskFailure(DelegateTask task) {
    log.info("Task Activity Log {}", task.getTaskActivityLogs().stream().collect(Collectors.joining("\n")));
  }
  private void printCriteriaNoMatch(DelegateTask task) {
    log.info("Task {} Criteria Mismatch with delegates :  {}", task.getUuid(),
        task.getTaskActivityLogs()
            .stream()
            .filter(message -> message.startsWith("No matching criteria"))
            .collect(Collectors.joining("\n")));
  }
  private void saveDelegateTask(DelegateTask delegateTask, String accountId) {
    if (mainConfiguration.getQueueServiceConfig() != null
        && !mainConfiguration.getQueueServiceConfig().isEnableQueueAndDequeue()) {
      persistence.save(delegateTask, delegateTaskMigrationHelper.isMigrationEnabledForTask(delegateTask.getUuid()));
      return;
    }

    if (featureFlagService.isEnabled(QUEUE_DELEGATE_TASK, accountId)
        && !delegateTaskQueueService.isResourceAvailableToAssignTask(delegateTask)) {
      delegateTaskQueueService.enqueue(delegateTask);
    } else {
      persistence.save(delegateTask, delegateTaskMigrationHelper.isMigrationEnabledForTask(delegateTask.getUuid()));
    }
  }

  @Override
  public void onAdded(Delegate delegate) {
    // do nothing
  }

  @Override
  public void onDisconnected(String accountId, String delegateId) {
    markAllTasksFailedForDelegate(accountId, delegateId);
  }

  @Override
  public void onReconnected(Delegate delegate) {
    // do nothing
  }

  @Override
  public void onDelegateTagsUpdated(String accountId) {
    // do nothing
  }
}
