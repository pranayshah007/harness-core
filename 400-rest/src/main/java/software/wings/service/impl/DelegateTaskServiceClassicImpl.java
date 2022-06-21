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
import static io.harness.beans.FeatureName.GIT_HOST_CONNECTIVITY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.SizeFunction.size;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.DelegateTaskAbortEvent.Builder.aDelegateTaskAbortEvent;
import static io.harness.delegate.beans.DelegateTaskEvent.DelegateTaskEventBuilder.aDelegateTaskEvent;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.executioncapability.ExecutionCapability.EvaluationMode;
import static io.harness.delegate.task.TaskFailureReason.EXPIRED;
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
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.app.ManagerCacheRegistrar.SECRET_TOKEN_CACHE;
import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.NO_ELIGIBLE_DELEGATES;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyList;
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
import io.harness.capability.CapabilityRequirement;
import io.harness.capability.CapabilityTaskSelectionDetails;
import io.harness.capability.service.CapabilityService;
import io.harness.delegate.NoEligibleDelegatesInAccountException;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.beans.DelegateInstanceStatus;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateProgressData;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskAbortEvent;
import io.harness.delegate.beans.DelegateTaskEvent;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskPackage.DelegateTaskPackageBuilder;
import io.harness.delegate.beans.DelegateTaskRank;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.DelegateTaskResponse.ResponseCode;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.NoAvailableDelegatesException;
import io.harness.delegate.beans.NoInstalledDelegatesException;
import io.harness.delegate.beans.SecretDetail;
import io.harness.delegate.beans.TaskGroup;
import io.harness.delegate.beans.TaskSelectorMap;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskLogContext;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.request.CfCommandTaskParameters;
import io.harness.delegate.task.pcf.request.CfCommandTaskParameters.CfCommandTaskParametersBuilder;
import io.harness.delegate.task.pcf.request.CfRunPluginCommandRequest;
import io.harness.environment.SystemEnvironment;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.exception.CriticalExpressionEvaluationException;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ExpressionReflectionUtils;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.PersistentLocker;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.DelegateDriverLogContext;
import io.harness.logstreaming.LogStreamingServiceRestClient;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.mongo.DelayLogContext;
import io.harness.network.SafeHttpCall;
import io.harness.observer.Subject;
import io.harness.persistence.HPersistence;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.selection.log.DelegateSelectionLogTaskMetadata;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateCallbackRegistry;
import io.harness.service.intfc.DelegateCallbackService;
import io.harness.service.intfc.DelegateInsightsService;
import io.harness.service.intfc.DelegateSetupService;
import io.harness.service.intfc.DelegateSyncService;
import io.harness.service.intfc.DelegateTaskResultsProvider;
import io.harness.service.intfc.DelegateTaskSelectorMapService;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.version.VersionInfoManager;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.app.MainConfiguration;
import software.wings.beans.ExecutionCredential;
import software.wings.beans.GitConfig;
import software.wings.beans.GitValidationParameters;
import software.wings.beans.HostValidationTaskParameters;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.common.AuditHelper;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.delegatetasks.cv.RateLimitExceededException;
import software.wings.delegatetasks.delegatecapability.CapabilityHelper;
import software.wings.delegatetasks.validation.DelegateConnectionResult;
import software.wings.expression.ManagerPreExecutionExpressionEvaluator;
import software.wings.expression.ManagerPreviewExpressionEvaluator;
import software.wings.expression.NgSecretManagerFunctor;
import software.wings.expression.SecretManagerFunctor;
import software.wings.expression.SecretManagerMode;
import software.wings.expression.SweepingOutputSecretFunctor;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.impl.infra.InfraDownloadService;
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.cache.Cache;
import javax.validation.executable.ValidateOnExecution;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.atmosphere.cpr.BroadcasterFactory;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

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
public class DelegateTaskServiceClassicImpl implements DelegateTaskServiceClassic {
  private static final String ASYNC = "async";
  private static final String SYNC = "sync";
  private static final String STREAM_DELEGATE = "/stream/delegate/";
  public static final String TASK_SELECTORS = "Task Selectors";
  public static final String TASK_CATEGORY_MAP = "Task Category Map";
  private static final int SECRET_CACHE_TTL_MINUTES = 3;
  private static final int SECRET_CACHE_MAXIMUM_SIZE = 1000;
  private static final long CAPABILITIES_CHECK_TASK_TIMEOUT_IN_MINUTES = 1L;

  private static final long VALIDATION_TIMEOUT = TimeUnit.MINUTES.toMillis(2);

  @Inject private HPersistence persistence;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private MainConfiguration mainConfiguration;
  @Inject private EventEmitter eventEmitter;
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
  @Inject private DelegateConnectionDao delegateConnectionDao;
  @Inject private SystemEnvironment sysenv;
  @Inject private DelegateSyncService delegateSyncService;
  @Inject private DelegateTaskService delegateTaskService;
  @Inject private KryoSerializer kryoSerializer;
  @Inject private DelegateCallbackRegistry delegateCallbackRegistry;
  @Inject private DelegateTaskSelectorMapService taskSelectorMapService;
  @Inject private SettingsService settingsService;
  @Inject private LogStreamingServiceRestClient logStreamingServiceRestClient;
  @Inject @Named("PRIVILEGED") private SecretManagerClientService ngSecretService;
  @Inject private DelegateCache delegateCache;
  @Inject private CapabilityService capabilityService;
  @Inject private DelegateInsightsService delegateInsightsService;
  @Inject private DelegateSetupService delegateSetupService;
  @Inject private AuditHelper auditHelper;
  @Inject private DelegateMetricsService delegateMetricsService;
  @Inject @Named(SECRET_TOKEN_CACHE) Cache<String, EncryptedRecordData> secretsCache;
  @Inject @Getter private Subject<DelegateObserver> subject = new Subject<>();

  private static final SecureRandom random = new SecureRandom();
  private HarnessCacheManager harnessCacheManager;

  private Supplier<Long> taskCountCache = Suppliers.memoizeWithExpiration(this::fetchTaskCount, 1, TimeUnit.MINUTES);
  @Inject @Getter private Subject<DelegateTaskStatusObserver> delegateTaskStatusObserverSubject;

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

  @VisibleForTesting
  protected void checkTaskRankRateLimit(DelegateTaskRank rank) {
    if (rank == null) {
      rank = DelegateTaskRank.CRITICAL;
    }

    if (rankLimitReached(rank)) {
      throw new RateLimitExceededException("Rate limit reached for tasks with rank " + rank.name());
    }
  }

  private boolean rankLimitReached(DelegateTaskRank rank) {
    Long totalTaskCount = taskCountCache.get();
    return totalTaskCount >= obtainRankLimit(rank);
  }

  private long obtainRankLimit(DelegateTaskRank rank) {
    switch (rank) {
      case OPTIONAL:
        return mainConfiguration.getPortal().getOptionalDelegateTaskRejectAtLimit();
      case IMPORTANT:
        return mainConfiguration.getPortal().getImportantDelegateTaskRejectAtLimit();
      case CRITICAL:
        return mainConfiguration.getPortal().getCriticalDelegateTaskRejectAtLimit();
      default:
        throw new InvalidArgumentsException("Unsupported delegate task rank level " + rank);
    }
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

  @Override
  public String queueTask(DelegateTask task) {
    task.getData().setAsync(true);
    if (task.getUuid() == null) {
      task.setUuid(generateUuid());
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
  public void scheduleSyncTask(DelegateTask task) {
    task.getData().setAsync(false);
    if (task.getUuid() == null) {
      task.setUuid(generateUuid());
    }

    try (AutoLogContext ignore1 = new TaskLogContext(task.getUuid(), task.getData().getTaskType(),
             TaskType.valueOf(task.getData().getTaskType()).getTaskGroup().name(), task.getRank(), OVERRIDE_NESTS);
         AutoLogContext ignore2 = new AccountLogContext(task.getAccountId(), OVERRIDE_ERROR)) {
      log.info("Processing sync task {} of type {}", task.getUuid(), task.getData().getTaskType());
      processDelegateTask(task, QUEUED);
      broadcastHelper.rebroadcastDelegateTask(task);
    }
  }

  @Override
  public <T extends DelegateResponseData> T executeTask(DelegateTask task) {
    scheduleSyncTask(task);
    return delegateSyncService.waitForTask(task.getUuid(), task.calcDescription(),
        Duration.ofMillis(task.getData().getTimeout()), task.getExecutionCapabilities());
  }

  @VisibleForTesting
  @Override
  public void processDelegateTask(DelegateTask task, DelegateTask.Status taskStatus) {
    String taskInfo =
        String.format("Processing task id: %s of task type %s", task.getUuid(), task.getData().getTaskType());
    addToTaskActivityLog(task, taskInfo);
    task.setStatus(taskStatus);
    task.setVersion(getVersion());
    task.setLastBroadcastAt(clock.millis());

    // For forward compatibility set the wait id to the uuid
    if (task.getUuid() == null) {
      task.setUuid(generateUuid());
    }

    if (task.getWaitId() == null) {
      task.setWaitId(task.getUuid());
    }

    // For backward compatibility we base the queue task expiry on the execution timeout
    if (task.getExpiry() == 0) {
      task.setExpiry(currentTimeMillis() + task.getData().getTimeout());
    }
    try (AutoLogContext ignore = new TaskLogContext(task.getUuid(), task.getData().getTaskType(),
             TaskType.valueOf(task.getData().getTaskType()).getTaskGroup().name(), OVERRIDE_ERROR)) {
      try {
        // capabilities created,then appended to task.executionCapabilities to get eligible delegates
        generateCapabilitiesForTask(task);
        convertToExecutionCapability(task);

        List<String> eligibleListOfDelegates = assignDelegateService.getEligibleDelegatesToExecuteTask(task);
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
        checkTaskRankRateLimit(task.getRank());

        // Added temporarily to help to identifying tasks whose task setup abstractions need to be fixed
        verifyTaskSetupAbstractions(task);
        if (task.isSelectionLogsTrackingEnabled()) {
          persistence.save(DelegateSelectionLogTaskMetadata.builder()
                               .taskId(task.getUuid())
                               .accountId(task.getAccountId())
                               .setupAbstractions(task.getSetupAbstractions())
                               .build());
        }

        // Ensure that broadcast happens at least 5 seconds from current time for async tasks
        if (task.getData().isAsync()) {
          task.setNextBroadcast(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5));
        }
        persistence.save(task);
        delegateSelectionLogsService.logBroadcastToDelegate(Sets.newHashSet(task.getBroadcastToDelegateIds()), task);
        delegateMetricsService.recordDelegateTaskMetrics(task, DELEGATE_TASK_CREATION);
        log.info("Task {} marked as {} with first attempt broadcast to {}", task.getUuid(), taskStatus,
            task.getBroadcastToDelegateIds());
        addToTaskActivityLog(task, "Task processing completed");
      } catch (Exception exception) {
        log.info("Task id {} failed with error {}", task.getUuid(), exception);
        printErrorMessageOnTaskFailure(task);
        handleTaskFailureResponse(task, ExceptionUtils.getMessage(exception));
        if (!task.getData().isAsync()) {
          throw exception;
        }
      }
    }
  }

  private String getDelegateIdForFirstBroadcast(DelegateTask delegateTask, List<String> eligibleListOfDelegates) {
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

  private void handleTaskFailureResponse(DelegateTask task, String errorMessage) {
    Query<DelegateTask> taskQuery = persistence.createQuery(DelegateTask.class)
                                        .filter(DelegateTaskKeys.accountId, task.getAccountId())
                                        .filter(DelegateTaskKeys.uuid, task.getUuid());
    DelegateTaskResponse response = DelegateTaskResponse.builder()
                                        .response(ErrorNotifyResponseData.builder().errorMessage(errorMessage).build())
                                        .responseCode(ResponseCode.FAILED)
                                        .accountId(task.getAccountId())
                                        .build();
    delegateTaskService.handleResponse(task, taskQuery, response);
  }

  @VisibleForTesting
  public List<CapabilityRequirement> createCapabilityRequirementInstances(
      String accountId, List<ExecutionCapability> agentCapabilities) {
    List<CapabilityRequirement> capabilityRequirements = new ArrayList<>();
    for (ExecutionCapability agentCapability : agentCapabilities) {
      CapabilityRequirement capabilityRequirement =
          capabilityService.buildCapabilityRequirement(accountId, agentCapability);

      if (capabilityRequirement != null) {
        capabilityRequirements.add(capabilityRequirement);
      }
    }

    return capabilityRequirements;
  }

  /**
   * This method is intended to be used whenever we need to extract delegate selection related data from delegate task.
   * It assumes all data related to scoping and selectors
   */
  @VisibleForTesting
  public CapabilityTaskSelectionDetails createCapabilityTaskSelectionDetailsInstance(
      DelegateTask task, CapabilityRequirement capabilityRequirement, List<String> assignableDelegateIds) {
    // Get all selector capabilities(this already contains all task tags)
    List<SelectorCapability> selectorCapabilities = null;
    if (task.getExecutionCapabilities() != null) {
      selectorCapabilities = task.getExecutionCapabilities()
                                 .stream()
                                 .filter(c -> c instanceof SelectorCapability)
                                 .map(c -> (SelectorCapability) c)
                                 .collect(toList());
    }

    // TaskGroup is also required for scoping check
    TaskGroup taskGroup = task.getData() != null && isNotBlank(task.getData().getTaskType())
        ? TaskType.valueOf(task.getData().getTaskType()).getTaskGroup()
        : null;

    return capabilityService.buildCapabilityTaskSelectionDetails(
        capabilityRequirement, taskGroup, task.getSetupAbstractions(), selectorCapabilities, assignableDelegateIds);
  }

  private void verifyTaskSetupAbstractions(DelegateTask task) {
    if (isNotBlank(task.getUuid()) && task.getData() != null && task.getData().getTaskType() != null) {
      try (AutoLogContext ignore1 = new TaskLogContext(task.getUuid(), task.getData().getTaskType(),
               TaskType.valueOf(task.getData().getTaskType()).getTaskGroup().name(), OVERRIDE_NESTS);) {
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

  private Long fetchTaskCount() {
    return persistence.createQuery(DelegateTask.class, excludeAuthority).count();
  }

  @Override
  public String queueParkedTask(String accountId, String taskId) {
    DelegateTask task = persistence.createQuery(DelegateTask.class)
                            .filter(DelegateTaskKeys.accountId, accountId)
                            .filter(DelegateTaskKeys.uuid, taskId)
                            .get();

    task.getData().setAsync(true);

    try (AutoLogContext ignore1 = new TaskLogContext(task.getUuid(), task.getData().getTaskType(),
             TaskType.valueOf(task.getData().getTaskType()).getTaskGroup().name(), OVERRIDE_NESTS);
         AutoLogContext ignore2 = new AccountLogContext(task.getAccountId(), OVERRIDE_ERROR)) {
      processDelegateTask(task, QUEUED);
      log.debug("Queueing parked task");
      broadcastHelper.broadcastNewDelegateTaskAsync(task);
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
          hostValidationTaskParameters = HostValidationTaskParameters.builder()
                                             .hostNames((List<String>) params[2])
                                             .connectionSetting((SettingAttribute) params[3])
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

  @Override
  public DelegateTaskPackage reportConnectionResults(String accountId, String delegateId, String taskId,
      String delegateInstanceId, List<DelegateConnectionResult> results) {
    assignDelegateService.saveConnectionResults(results);
    DelegateTask delegateTask = getUnassignedDelegateTask(accountId, taskId, delegateInstanceId);
    if (delegateTask == null) {
      return null;
    }

    try (AutoLogContext ignore = new TaskLogContext(taskId, delegateTask.getData().getTaskType(),
             TaskType.valueOf(delegateTask.getData().getTaskType()).getTaskGroup().name(), OVERRIDE_ERROR)) {
      log.info("Delegate completed validating {} task", delegateTask.getData().isAsync() ? ASYNC : SYNC);

      UpdateOperations<DelegateTask> updateOperations =
          persistence.createUpdateOperations(DelegateTask.class)
              .addToSet(DelegateTaskKeys.validationCompleteDelegateIds, delegateId);
      Query<DelegateTask> updateQuery = persistence.createQuery(DelegateTask.class)
                                            .filter(DelegateTaskKeys.accountId, delegateTask.getAccountId())
                                            .filter(DelegateTaskKeys.uuid, delegateTask.getUuid())
                                            .filter(DelegateTaskKeys.status, QUEUED)
                                            .field(DelegateTaskKeys.delegateId)
                                            .doesNotExist();
      persistence.update(updateQuery, updateOperations);

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
  public DelegateTaskPackage acquireDelegateTask(
      String accountId, String delegateId, String taskId, String delegateInstanceId) {
    try {
      Delegate delegate = delegateCache.get(accountId, delegateId, false);
      if (delegate == null || DelegateInstanceStatus.ENABLED != delegate.getStatus()) {
        log.warn("Delegate rejected to acquire task, because it was not found to be in {} status.",
            DelegateInstanceStatus.ENABLED);
        return null;
      }

      log.debug("Acquiring delegate task");
      DelegateTask delegateTask = getUnassignedDelegateTask(accountId, taskId, delegateInstanceId);
      if (delegateTask == null) {
        return null;
      }

      try (AutoLogContext ignore = new TaskLogContext(taskId, delegateTask.getData().getTaskType(),
               TaskType.valueOf(delegateTask.getData().getTaskType()).getTaskGroup().name(), OVERRIDE_ERROR)) {
        if (assignDelegateService.shouldValidate(delegateTask, delegateId)) {
          setValidationStarted(delegateId, delegateTask);
          return resolvePreAssignmentExpressions(delegateTask, SecretManagerMode.APPLY);
        } else if (assignDelegateService.isWhitelisted(delegateTask, delegateId)) {
          return assignTask(delegateId, taskId, delegateTask, delegateInstanceId);
        }
        log.info("Delegate {} is blacklisted for task {}", delegateId, taskId);
        return null;
      }
    } finally {
      if (log.isDebugEnabled()) {
        log.debug("Done with acquire delegate task{} ", taskId);
      }
    }
  }

  @VisibleForTesting
  void setValidationStarted(String delegateId, DelegateTask delegateTask) {
    delegateMetricsService.recordDelegateTaskMetrics(delegateTask, DELEGATE_TASK_VALIDATION);
    log.info("Delegate to validate {} task", delegateTask.getData().isAsync() ? ASYNC : SYNC);
    UpdateOperations<DelegateTask> updateOperations = persistence.createUpdateOperations(DelegateTask.class)
                                                          .addToSet(DelegateTaskKeys.validatingDelegateIds, delegateId);
    Query<DelegateTask> updateQuery = persistence.createQuery(DelegateTask.class)
                                          .filter(DelegateTaskKeys.accountId, delegateTask.getAccountId())
                                          .filter(DelegateTaskKeys.uuid, delegateTask.getUuid())
                                          .filter(DelegateTaskKeys.status, QUEUED)
                                          .field(DelegateTaskKeys.delegateId)
                                          .doesNotExist();
    persistence.update(updateQuery, updateOperations);

    persistence.update(updateQuery.field(DelegateTaskKeys.validationStartedAt).doesNotExist(),
        persistence.createUpdateOperations(DelegateTask.class)
            .set(DelegateTaskKeys.validationStartedAt, clock.millis()));
  }

  private void clearFromValidationCache(DelegateTask delegateTask) {
    UpdateOperations<DelegateTask> updateOperations = persistence.createUpdateOperations(DelegateTask.class)
                                                          .unset(DelegateTaskKeys.validatingDelegateIds)
                                                          .unset(DelegateTaskKeys.validationCompleteDelegateIds);
    Query<DelegateTask> updateQuery = persistence.createQuery(DelegateTask.class)
                                          .filter(DelegateTaskKeys.accountId, delegateTask.getAccountId())
                                          .filter(DelegateTaskKeys.uuid, delegateTask.getUuid())
                                          .filter(DelegateTaskKeys.status, QUEUED)
                                          .field(DelegateTaskKeys.delegateId)
                                          .doesNotExist();
    persistence.update(updateQuery, updateOperations);
  }

  @VisibleForTesting
  DelegateTask getUnassignedDelegateTask(String accountId, String taskId, String delegateInstanceId) {
    DelegateTask delegateTask = persistence.createQuery(DelegateTask.class)
                                    .filter(DelegateTaskKeys.accountId, accountId)
                                    .filter(DelegateTaskKeys.uuid, taskId)
                                    .get();
    if (delegateTask != null) {
      try (AutoLogContext ignore = new TaskLogContext(taskId, delegateTask.getData().getTaskType(),
               TaskType.valueOf(delegateTask.getData().getTaskType()).getTaskGroup().name(), OVERRIDE_ERROR)) {
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
      log.info("Task no longer exists", taskId);
    }
    return null;
  }

  private DelegateTaskPackage resolvePreAssignmentExpressions(DelegateTask delegateTask, SecretManagerMode mode) {
    try {
      ManagerPreExecutionExpressionEvaluator managerPreExecutionExpressionEvaluator =
          new ManagerPreExecutionExpressionEvaluator(mode, serviceTemplateService, configService,
              artifactCollectionUtils, featureFlagService, managerDecryptionService, secretManager,
              delegateTask.getAccountId(), delegateTask.getWorkflowExecutionId(),
              delegateTask.getData().getExpressionFunctorToken(), ngSecretService, delegateTask.getSetupAbstractions(),
              secretsCache);

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
          log.warn("Unable to retrieve the log streaming service account token, while preparing delegate task package");
          throw new InvalidRequestException(e.getMessage() + "\nPlease ensure log service is running.", e);
        }

        delegateTaskPackageBuilder.logStreamingAbstractions(delegateTask.getLogStreamingAbstractions());
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
        return null;
      }

      addSecretManagerFunctorConfigs(
          delegateTaskPackageBuilder, secretManagerFunctor, ngSecretManagerFunctor, sweepingOutputSecretFunctor);

      return delegateTaskPackageBuilder.build();
    } catch (CriticalExpressionEvaluationException exception) {
      log.error("Exception in ManagerPreExecutionExpressionEvaluator ", exception);
      Query<DelegateTask> taskQuery = persistence.createQuery(DelegateTask.class)
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

  private void addSecretManagerFunctorConfigs(DelegateTaskPackageBuilder delegateTaskPackageBuilder,
      SecretManagerFunctor secretManagerFunctor, NgSecretManagerFunctor ngSecretManagerFunctor,
      SweepingOutputSecretFunctor sweepingOutputSecretFunctor) {
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

  @Override
  public void publishTaskProgressResponse(
      String accountId, String driverId, String delegateTaskId, DelegateProgressData responseData) {
    DelegateCallbackService delegateCallbackService = delegateCallbackRegistry.obtainDelegateCallbackService(driverId);
    if (delegateCallbackService == null) {
      return;
    }
    delegateCallbackService.publishTaskProgressResponse(
        delegateTaskId, generateUuid(), kryoSerializer.asDeflatedBytes(responseData));
  }

  @VisibleForTesting
  DelegateTaskPackage assignTask(
      String delegateId, String taskId, DelegateTask delegateTask, String delegateInstanceId) {
    // Clear pending validations. No longer need to track since we're assigning.
    clearFromValidationCache(delegateTask);
    log.info("Assigning {} task to delegate", delegateTask.getData().isAsync() ? ASYNC : SYNC);
    Query<DelegateTask> query = persistence.createQuery(DelegateTask.class)
                                    .filter(DelegateTaskKeys.accountId, delegateTask.getAccountId())
                                    .filter(DelegateTaskKeys.uuid, taskId)
                                    .filter(DelegateTaskKeys.status, QUEUED)
                                    .field(DelegateTaskKeys.delegateId)
                                    .doesNotExist()
                                    .field(DelegateTaskKeys.delegateInstanceId)
                                    .doesNotExist()
                                    .project(DelegateTaskKeys.data_parameters, false);
    UpdateOperations<DelegateTask> updateOperations =
        persistence.createUpdateOperations(DelegateTask.class)
            .set(DelegateTaskKeys.delegateId, delegateId)
            .set(DelegateTaskKeys.delegateInstanceId, delegateInstanceId)
            .set(DelegateTaskKeys.status, STARTED)
            .set(DelegateTaskKeys.expiry, currentTimeMillis() + delegateTask.getData().getTimeout());
    DelegateTask task = persistence.findAndModifySystemData(query, updateOperations, HPersistence.returnNewOptions);
    // If the task wasn't updated because delegateId already exists then query for the task with the delegateId in
    // case client is retrying the request
    if (task != null) {
      try (
          DelayLogContext ignore = new DelayLogContext(task.getLastUpdatedAt() - task.getCreatedAt(), OVERRIDE_ERROR)) {
        log.info("Task with id {} assigned to delegate", delegateTask.getUuid());
      }
      task.getData().setParameters(delegateTask.getData().getParameters());
      delegateSelectionLogsService.logTaskAssigned(delegateId, task);
      delegateTaskStatusObserverSubject.fireInform(DelegateTaskStatusObserver::onTaskAssigned,
          delegateTask.getAccountId(), taskId, delegateId, task.getData().getTimeout());
      delegateMetricsService.recordDelegateTaskMetrics(delegateTask, DELEGATE_TASK_ACQUIRE);

      return resolvePreAssignmentExpressions(task, SecretManagerMode.APPLY);
    }
    task = persistence.createQuery(DelegateTask.class)
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
    return persistence.createQuery(DelegateTask.class)
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
        try (AutoLogContext ignore3 = new TaskLogContext(delegateTaskId, delegateTask.getData().getTaskType(),
                 TaskType.valueOf(delegateTask.getData().getTaskType()).getTaskGroup().name(), OVERRIDE_ERROR)) {
          errorMessage =
              "Task expired. " + assignDelegateService.getActiveDelegateAssignmentErrorMessage(EXPIRED, delegateTask);
          delegateMetricsService.recordDelegateTaskMetrics(delegateTask, DELEGATE_TASK_EXPIRED);
          log.info("Marking task as expired: {}", errorMessage);

          if (isNotBlank(delegateTask.getWaitId())) {
            waitNotifyEngine.doneWith(delegateTask.getWaitId(),
                ErrorNotifyResponseData.builder().errorMessage(errorMessage).expired(true).build());
          }
        }
      }

      endTask(accountId, delegateTaskId, delegateTaskQuery, ERROR);
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

  private DelegateTask endTask(
      String accountId, String delegateTaskId, Query<DelegateTask> delegateTaskQuery, DelegateTask.Status status) {
    UpdateOperations updateOperations =
        persistence.createUpdateOperations(DelegateTask.class).set(DelegateTaskKeys.status, status);

    DelegateTask oldTask =
        persistence.findAndModify(delegateTaskQuery, updateOperations, HPersistence.returnOldOptions);

    broadcasterFactory.lookup(STREAM_DELEGATE + accountId, true)
        .broadcast(aDelegateTaskAbortEvent().withAccountId(accountId).withDelegateTaskId(delegateTaskId).build());

    return oldTask;
  }

  private Query<DelegateTask> getRunningTaskQuery(String accountId, String delegateTaskId) {
    return persistence.createQuery(DelegateTask.class)
        .filter(DelegateTaskKeys.uuid, delegateTaskId)
        .filter(DelegateTaskKeys.accountId, accountId)
        .filter(DelegateTaskKeys.data_async, Boolean.TRUE)
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
    // TODO - add assignment filter here (scopes. selectors, ...)
    Query<DelegateTask> delegateTaskQuery = persistence.createQuery(DelegateTask.class)
                                                .filter(DelegateTaskKeys.accountId, accountId)
                                                .filter(DelegateTaskKeys.status, QUEUED)
                                                .filter(DelegateTaskKeys.data_async, !sync)
                                                .field(DelegateTaskKeys.delegateId)
                                                .doesNotExist()
                                                .field(DelegateTaskKeys.expiry)
                                                .greaterThan(currentTimeMillis());
    List<DelegateTask> delegateTasks =
        delegateTaskQuery.asList()
            .stream()
            .filter(delegateTask -> delegateTask.getEligibleToExecuteDelegateIds().contains(delegateId))
            .collect(toList());
    return delegateTasks.stream()
        .map(delegateTask
            -> aDelegateTaskEvent()
                   .withAccountId(accountId)
                   .withDelegateTaskId(delegateTask.getUuid())
                   .withSync(sync)
                   .build())
        .collect(toList());
  }

  private List<DelegateTaskEvent> getAbortedEvents(String accountId, String delegateId) {
    Query<DelegateTask> abortedQuery = persistence.createQuery(DelegateTask.class)
                                           .filter(DelegateTaskKeys.accountId, accountId)
                                           .filter(DelegateTaskKeys.status, ABORTED)
                                           .filter(DelegateTaskKeys.data_async, Boolean.TRUE)
                                           .filter(DelegateTaskKeys.delegateId, delegateId);

    // Send abort event only once by clearing delegateId
    persistence.update(
        abortedQuery, persistence.createUpdateOperations(DelegateTask.class).unset(DelegateTaskKeys.delegateId));

    return abortedQuery.project(DelegateTaskKeys.accountId, true)
        .asList()
        .stream()
        .map(delegateTask
            -> aDelegateTaskAbortEvent()
                   .withAccountId(delegateTask.getAccountId())
                   .withDelegateTaskId(delegateTask.getUuid())
                   .withSync(false)
                   .build())
        .collect(toList());
  }

  private String getVersion() {
    return versionInfoManager.getVersionInfo().getVersion();
  }

  @Override
  public void deleteByAccountId(String accountId) {
    persistence.delete(persistence.createQuery(Delegate.class).filter(DelegateKeys.accountId, accountId));
  }

  @Override
  public Optional<DelegateTask> fetchDelegateTask(String accountId, String taskId) {
    return Optional.ofNullable(persistence.createQuery(DelegateTask.class)
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
            delegateTask.getUuid(), kryoSerializer.asDeflatedBytes(response.getResponse()));
      } else {
        log.debug("Publishing sync task response...");
        delegateCallbackService.publishSyncTaskResponse(
            delegateTask.getUuid(), kryoSerializer.asDeflatedBytes(response.getResponse()));
      }
    } catch (Exception ex) {
      log.error("Failed publishing task response for task", ex);
    }
  }

  @Override
  public boolean checkDelegateConnected(String accountId, String delegateId) {
    return delegateConnectionDao.checkDelegateConnected(
        accountId, delegateId, versionInfoManager.getVersionInfo().getVersion());
  }

  @Override
  public void markAllTasksFailedForDelegate(String accountId, String delegateId) {
    final List<DelegateTask> delegateTasks = persistence.createQuery(DelegateTask.class)
                                                 .filter(DelegateTaskKeys.accountId, accountId)
                                                 .filter(DelegateTaskKeys.delegateId, delegateId)
                                                 .filter(DelegateTaskKeys.status, STARTED)
                                                 .asList();
    if (isEmpty(delegateTasks)) {
      return;
    }
    log.info("Marking delegate tasks {} failed since delegate went down before completion.",
        delegateTasks.stream().map(DelegateTask::getUuid).collect(Collectors.toList()));
    final String errorMessage = "Delegate disconnected while executing the task";
    final DelegateTaskResponse delegateTaskResponse =
        DelegateTaskResponse.builder()
            .responseCode(ResponseCode.FAILED)
            .accountId(accountId)
            .response(ErrorNotifyResponseData.builder()
                          .errorMessage(errorMessage)
                          .exception(new DelegateNotAvailableException(errorMessage))
                          .delegateMetaInfo(DelegateMetaInfo.builder().id(delegateId).build())
                          .build())
            .build();
    delegateTasks.forEach(delegateTask -> {
      delegateTaskService.processDelegateResponse(accountId, delegateId, delegateTask.getUuid(), delegateTaskResponse);
    });
  }

  public void addToTaskActivityLog(DelegateTask task, String message) {
    if (task.getTaskActivityLogs() == null) {
      task.setTaskActivityLogs(Lists.newArrayList());
    }
    task.getTaskActivityLogs().add(message);
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
}
