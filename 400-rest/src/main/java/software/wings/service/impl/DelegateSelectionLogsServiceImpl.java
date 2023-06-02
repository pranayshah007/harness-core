/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.FeatureName;
import io.harness.beans.SearchFilter;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateSelectionLogParams;
import io.harness.delegate.beans.DelegateSelectionLogResponse;
import io.harness.delegate.beans.DelegateType;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.selection.log.DelegateSelectionLog;
import io.harness.selection.log.DelegateSelectionLog.DelegateSelectionLogKeys;
import io.harness.service.intfc.DelegateCache;

import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.DelegateSelectionLogsService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.utils.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@BreakDependencyOn("io.harness.beans.Cd1SetupFields")
@BreakDependencyOn("software.wings.beans.Application")
@BreakDependencyOn("software.wings.beans.Environment")
@BreakDependencyOn("software.wings.beans.Service")
@OwnedBy(DEL)
public class DelegateSelectionLogsServiceImpl implements DelegateSelectionLogsService {
  @Inject private HPersistence persistence;
  @Inject private DelegateService delegateService;
  @Inject private DelegateTaskServiceClassic delegateTaskServiceClassic;
  @Inject private DelegateCache delegateCache;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private DataStoreService dataStoreService;
  private Cache<String, List<DelegateSelectionLog>> cache =
      Caffeine.newBuilder()
          .executor(Executors.newSingleThreadExecutor(
              new ThreadFactoryBuilder().setNameFormat("delegate-selection-log-write").build()))
          .expireAfterWrite(1000, TimeUnit.MILLISECONDS)
          .removalListener(this::dispatchSelectionLogs)
          .build();

  private static final String SELECTED = "Selected";
  private static final String NON_SELECTED = "Not Selected";
  private static final String ASSIGNED = "Assigned";
  private static final String REJECTED = "Rejected";
  private static final String BROADCAST = "Broadcast";
  private static final String INFO = "Info";

  private static final String TASK_ASSIGNED = "Delegate assigned for task execution";
  public static final String NO_ELIGIBLE_DELEGATES = "No eligible delegate(s) in account to execute task. ";
  public static final String ELIGIBLE_DELEGATES = "Delegate(s) eligible to execute task";
  public static final String PRE_ASSIGNED_ELIGIBLE_DELEGATES = "Pre assigned delegate(s) eligible to execute task";
  public static final String BROADCASTING_DELEGATES = "Broadcasting to delegate(s)";
  public static final String CAN_NOT_ASSIGN_TASK_GROUP = "Delegate(s) not supported for task type";
  public static final String CAN_NOT_ASSIGN_CG_NG_TASK_GROUP =
      "Cannot assign - CG task to CG Delegate only and NG task to NG delegate(s)";
  public static final String CAN_NOT_ASSIGN_DELEGATE_SCOPE_GROUP = "Delegate scope(s) mismatched";
  public static final String CAN_NOT_ASSIGN_PROFILE_SCOPE_GROUP = "Delegate profile scope(s) mismatched ";
  public static final String CAN_NOT_ASSIGN_SELECTOR_TASK_GROUP = "Delegate(s) don't have selectors";
  public static final String CAN_NOT_ASSIGN_OWNER = "There are no delegates with the right ownership to execute task\"";
  public static final String TASK_VALIDATION_FAILED =
      "No eligible delegate was able to confirm that it has the capability to execute ";

  public DelegateSelectionLogsServiceImpl() {
    // caffeine cache doesn't evict solely on time, it does it lazily only when new entries are added.
    // adding this executor to continuously purge so that the records are written
    Executors
        .newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("delegate-selection-log-cleanup").build())
        .scheduleAtFixedRate(
            this::purgeSelectionLogs, 5000, 1000, TimeUnit.MILLISECONDS); // periodic cleanup for expired keys
  }

  @Override
  public synchronized void save(DelegateSelectionLog selectionLog) {
    Optional.ofNullable(cache.get(selectionLog.getAccountId(), log -> new ArrayList<>()))
        .ifPresent(logs -> logs.add(selectionLog));
  }

  private void dispatchSelectionLogs(String accountId, List<DelegateSelectionLog> logs, RemovalCause removalCause) {
    try {
      dataStoreService.save(DelegateSelectionLog.class, logs, true);
      // TODO: remove this once reading from datastore is operational
      if (dataStoreService instanceof GoogleDataStoreServiceImpl) {
        persistence.save(logs);
      }
    } catch (Exception exception) {
      log.error("Error while saving into Database ", exception);
    }
  }

  @Override
  public void logNoEligibleDelegatesToExecuteTask(DelegateTask delegateTask) {
    if (!delegateTask.isSelectionLogsTrackingEnabled()) {
      return;
    }
    save(DelegateSelectionLog.builder()
             .accountId(getAccountId(delegateTask))
             .taskId(delegateTask.getUuid())
             .conclusion(REJECTED)
             .message(NO_ELIGIBLE_DELEGATES)
             .eventTimestamp(System.currentTimeMillis())
             .build());
  }

  @Override
  public void logEligibleDelegatesToExecuteTask(
      Set<String> delegateIds, DelegateTask delegateTask, boolean preAssigned) {
    if (!delegateTask.isSelectionLogsTrackingEnabled()) {
      return;
    }
    if (Objects.isNull(delegateIds)) {
      return;
    }
    String message_prefix = preAssigned ? PRE_ASSIGNED_ELIGIBLE_DELEGATES : ELIGIBLE_DELEGATES;
    String message = String.format("%s : [%s]", message_prefix,
        String.join(", ", getDelegateSelectionLogKeys(delegateTask.getAccountId(), delegateIds)));
    save(DelegateSelectionLog.builder()
             .accountId(getAccountId(delegateTask))
             .taskId(delegateTask.getUuid())
             .delegateIds(delegateIds)
             .conclusion(SELECTED)
             .message(message)
             .eventTimestamp(System.currentTimeMillis())
             .build());
  }

  @Override
  public void logNonSelectedDelegates(DelegateTask delegateTask, Map<String, List<String>> nonAssignableDelegates) {
    if (!delegateTask.isSelectionLogsTrackingEnabled()) {
      return;
    }
    if (Objects.isNull(nonAssignableDelegates)) {
      return;
    }
    List<String> excludeGroups = Lists.newArrayList(CAN_NOT_ASSIGN_OWNER, CAN_NOT_ASSIGN_CG_NG_TASK_GROUP);
    List<String> nonAssignables =
        nonAssignableDelegates.keySet()
            .stream()
            .filter(err -> !excludeGroups.contains(err))
            .map(errorMessage -> errorMessage + " : " + String.join(",", nonAssignableDelegates.get(errorMessage)))
            .collect(Collectors.toList());
    nonAssignables.forEach(msg
        -> save(DelegateSelectionLog.builder()
                    .accountId(getAccountId(delegateTask))
                    .taskId(delegateTask.getUuid())
                    .message(msg)
                    .conclusion(NON_SELECTED)
                    .eventTimestamp(System.currentTimeMillis())
                    .build()));
  }

  @Override
  public void logBroadcastToDelegate(Set<String> delegateIds, DelegateTask delegateTask) {
    if (!delegateTask.isSelectionLogsTrackingEnabled()) {
      return;
    }
    if (Objects.isNull(delegateIds)) {
      return;
    }
    String message = String.format("%s : [%s]", BROADCASTING_DELEGATES,
        String.join(", ", getDelegateSelectionLogKeys(delegateTask.getAccountId(), delegateIds)));
    save(DelegateSelectionLog.builder()
             .accountId(getAccountId(delegateTask))
             .taskId(delegateTask.getUuid())
             .delegateIds(delegateIds)
             .conclusion(BROADCAST)
             .message(message)
             .eventTimestamp(System.currentTimeMillis())
             .build());
  }

  @Override
  public void logTaskAssigned(String delegateId, DelegateTask delegateTask) {
    if (!delegateTask.isSelectionLogsTrackingEnabled()) {
      return;
    }
    Delegate delegate = delegateCache.get(delegateTask.getAccountId(), delegateId, false);
    if (delegate != null) {
      delegateId = delegate.getHostName();
    }
    String message = String.format("%s : [%s]", TASK_ASSIGNED, delegateId);
    save(DelegateSelectionLog.builder()
             .accountId(getAccountId(delegateTask))
             .taskId(delegateTask.getUuid())
             .conclusion(ASSIGNED)
             .message(message)
             .eventTimestamp(System.currentTimeMillis())
             .build());
  }

  @Override
  public void logTaskValidationFailed(DelegateTask delegateTask, String failureMessage) {
    if (!delegateTask.isSelectionLogsTrackingEnabled()) {
      return;
    }
    save(DelegateSelectionLog.builder()
             .accountId(getAccountId(delegateTask))
             .taskId(delegateTask.getUuid())
             .conclusion(REJECTED)
             .message(failureMessage)
             .eventTimestamp(System.currentTimeMillis())
             .build());
  }

  @Override
  public List<DelegateSelectionLogParams> fetchTaskSelectionLogs(String accountId, String taskId) {
    List<DelegateSelectionLog> delegateSelectionLogsList =
        featureFlagService.isEnabled(FeatureName.DEL_SELECTION_LOGS_READ_FROM_GOOGLE_DATA_STORE, accountId)
        ? dataStoreService
              .list(DelegateSelectionLog.class,
                  aPageRequest()
                      .withLimit(UNLIMITED)
                      .addFilter(DelegateSelectionLogKeys.accountId, SearchFilter.Operator.EQ, accountId)
                      .addFilter(DelegateSelectionLogKeys.taskId, SearchFilter.Operator.EQ, taskId)
                      .build(),
                  false)
              .getResponse()
        : persistence.createQuery(DelegateSelectionLog.class)
              .filter(DelegateSelectionLogKeys.accountId, accountId)
              .filter(DelegateSelectionLogKeys.taskId, taskId)
              .asList();

    List<DelegateSelectionLog> logList = delegateSelectionLogsList.stream()
                                             .sorted(Comparator.comparing(DelegateSelectionLog::getEventTimestamp))
                                             .collect(Collectors.toList());

    return logList.stream().map(this::buildSelectionLogParams).collect(Collectors.toList());
  }

  @Override
  public DelegateSelectionLogResponse fetchTaskSelectionLogsData(String accountId, String taskId) {
    List<DelegateSelectionLogParams> delegateSelectionLogParams = fetchTaskSelectionLogs(accountId, taskId);

    return DelegateSelectionLogResponse.builder()
        .delegateSelectionLogs(delegateSelectionLogParams)
        .taskSetupAbstractions(Collections.emptyMap())
        .build();
  }

  @Override
  public Optional<DelegateSelectionLogParams> fetchSelectedDelegateForTask(String accountId, String taskId) {
    DelegateSelectionLog delegateSelectionLog = null;
    if (featureFlagService.isEnabled(FeatureName.DEL_SELECTION_LOGS_READ_FROM_GOOGLE_DATA_STORE, accountId)) {
      List<DelegateSelectionLog> logs =
          dataStoreService
              .list(DelegateSelectionLog.class,
                  aPageRequest()
                      .withLimit(UNLIMITED)
                      .addFilter(DelegateSelectionLogKeys.accountId, SearchFilter.Operator.EQ, accountId)
                      .addFilter(DelegateSelectionLogKeys.taskId, SearchFilter.Operator.EQ, taskId)
                      .addFilter(DelegateSelectionLogKeys.conclusion, SearchFilter.Operator.EQ, ASSIGNED)
                      .build(),
                  false)
              .getResponse();
      if (isNotEmpty(logs)) {
        delegateSelectionLog = logs.stream()
                                   .filter(selectionLog -> ASSIGNED.equals(selectionLog.getConclusion()))
                                   .findFirst()
                                   .orElse(null);
      }
    } else {
      delegateSelectionLog = persistence.createQuery(DelegateSelectionLog.class)
                                 .filter(DelegateSelectionLogKeys.accountId, accountId)
                                 .filter(DelegateSelectionLogKeys.taskId, taskId)
                                 .filter(DelegateSelectionLogKeys.conclusion, ASSIGNED)
                                 .get();
    }
    if (delegateSelectionLog == null) {
      log.warn("Delegate selection log is null, returning empty optional for taskId {}", taskId);
      return Optional.empty();
    }
    return Optional.ofNullable(buildSelectionLogParams(delegateSelectionLog));
  }

  @Override
  public void logDelegateTaskInfo(DelegateTask delegateTask) {
    if (!delegateTask.isSelectionLogsTrackingEnabled()) {
      return;
    }
    List<String> info = new ArrayList<>();
    String delegateSelectorReceived = generateSelectionLogForSelectors(delegateTask.getExecutionCapabilities());
    if (isNotEmpty(delegateSelectorReceived)) {
      info.add(delegateSelectorReceived);
    }
    if (isNotEmpty(delegateTask.getExecutionCapabilities())) {
      delegateTask.getExecutionCapabilities().forEach(capability -> {
        if (isNotEmpty(capability.getCapabilityToString())) {
          info.add(capability.getCapabilityToString());
        }
      });
    }

    if (isEmpty(info)) {
      return;
    }
    save(DelegateSelectionLog.builder()
             .accountId(getAccountId(delegateTask))
             .taskId(delegateTask.getUuid())
             .conclusion(INFO)
             .message(info.toString())
             .eventTimestamp(System.currentTimeMillis())
             .build());
  }

  private DelegateSelectionLogParams buildSelectionLogParams(DelegateSelectionLog selectionLog) {
    return DelegateSelectionLogParams.builder()
        .conclusion(selectionLog.getConclusion())
        .message(selectionLog.getMessage())
        .eventTimestamp(selectionLog.getEventTimestamp())
        .build();
  }

  // for ng docker delegate the key is DELEGATE_NAME + HOSTNAME and for others the key is HOSTNAME
  private Set<String> getDelegateSelectionLogKeys(String accountId, Set<String> delegateIds) {
    return delegateIds.stream()
        .map(delegateId -> getDelegateSelectionLogKey(accountId, delegateId))
        .collect(Collectors.toSet());
  }

  private String getDelegateSelectionLogKey(String accountId, String delegateId) {
    Delegate delegate = delegateCache.get(accountId, delegateId, false);
    if (delegate == null) {
      return delegateId;
    }
    if (delegate.isNg() && DelegateType.DOCKER.equals(delegate.getDelegateType())) {
      return delegate.getDelegateName() + "-" + delegate.getHostName();
    }
    return delegate.getHostName();
  }

  public String generateSelectionLogForSelectors(List<ExecutionCapability> executionCapabilities) {
    if (isEmpty(executionCapabilities)) {
      return EMPTY;
    }
    List<String> taskSelectors = new ArrayList<>();
    List<SelectorCapability> selectorCapabilities =
        delegateTaskServiceClassic.fetchTaskSelectorCapabilities(executionCapabilities);
    if (isEmpty(selectorCapabilities)) {
      return EMPTY;
    }
    selectorCapabilities.forEach(
        capability -> taskSelectors.add(capability.getSelectorOrigin().concat(capability.getSelectors().toString())));
    return String.format("Selector(s) originated from %s ", String.join(", ", taskSelectors));
  }

  private String getAccountId(DelegateTask delegateTask) {
    return delegateTask.isExecuteOnHarnessHostedDelegates() ? delegateTask.getSecondaryAccountId()
                                                            : delegateTask.getAccountId();
  }

  @VisibleForTesting
  void purgeSelectionLogs() {
    this.cache.cleanUp();
  }
}
