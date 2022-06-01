/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateSelectionLogParams;
import io.harness.delegate.beans.DelegateSelectionLogResponse;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.selection.log.DelegateSelectionLog;
import io.harness.selection.log.DelegateSelectionLog.DelegateSelectionLogKeys;
import io.harness.selection.log.DelegateSelectionLogTaskMetadata;
import io.harness.service.intfc.DelegateCache;

import software.wings.delegatetasks.delegatecapability.CapabilityHelper;
import software.wings.service.intfc.DelegateSelectionLogsService;
import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.utils.Lists;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

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
  @Inject private DelegateCache delegateCache;
  @Inject private FeatureFlagService featureFlagService;

  private static final String SELECTED = "Selected";
  private static final String NON_SELECTED = "Non Selected";
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
  public static final String CAN_NOT_ASSIGN_SELECTOR_TASK_GROUP = "No matching selector(s)";
  public static final String CAN_NOT_ASSIGN_OWNER = "There are no delegates with the right ownership to execute task\"";
  public static final String TASK_VALIDATION_FAILED =
      "No eligible delegate was able to confirm that it has the capability to execute ";

  public static final List<String> selectionLogOrder =
      Lists.newArrayList(SELECTED, NON_SELECTED, BROADCAST, ASSIGNED, REJECTED, INFO);

  @Override
  public void save(DelegateSelectionLog selectionLog) {
    if (featureFlagService.isEnabled(FeatureName.DELEGATE_SELECTION_LOGS_DISABLED, selectionLog.getAccountId())) {
      return;
    }
    try {
      persistence.save(selectionLog);
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
             .accountId(delegateTask.getAccountId())
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
    String message = String.format(
        "%s : [%s]", message_prefix, String.join(", ", getDelegateHostNames(delegateTask.getAccountId(), delegateIds)));
    save(DelegateSelectionLog.builder()
             .accountId(delegateTask.getAccountId())
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
                    .accountId(delegateTask.getAccountId())
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
        String.join(", ", getDelegateHostNames(delegateTask.getAccountId(), delegateIds)));
    save(DelegateSelectionLog.builder()
             .accountId(delegateTask.getAccountId())
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
             .accountId(delegateTask.getAccountId())
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
             .accountId(delegateTask.getAccountId())
             .taskId(delegateTask.getUuid())
             .conclusion(REJECTED)
             .message(failureMessage)
             .eventTimestamp(System.currentTimeMillis())
             .build());
  }

  @Override
  public List<DelegateSelectionLogParams> fetchTaskSelectionLogs(String accountId, String taskId) {
    List<DelegateSelectionLog> delegateSelectionLogsList = persistence.createQuery(DelegateSelectionLog.class)
                                                               .filter(DelegateSelectionLogKeys.accountId, accountId)
                                                               .filter(DelegateSelectionLogKeys.taskId, taskId)
                                                               .asList();
    Map<String, List<DelegateSelectionLog>> logs =
        delegateSelectionLogsList.stream().collect(Collectors.groupingBy(DelegateSelectionLog::getConclusion));
    List<DelegateSelectionLog> delegateSelectionLogList = new ArrayList<>();
    for (String assessment : selectionLogOrder) {
      if (logs.containsKey(assessment)) {
        delegateSelectionLogList.addAll(logs.get(assessment));
      }
    }
    return delegateSelectionLogList.stream().map(this::buildSelectionLogParams).collect(Collectors.toList());
  }

  @Override
  public List<Pair<String, List<DelegateSelectionLogParams>>> fetchTaskSelectionLogsGroupByAssessment(
      String accountId, String taskId) {
    List<DelegateSelectionLog> delegateSelectionLogsList = persistence.createQuery(DelegateSelectionLog.class)
                                                               .filter(DelegateSelectionLogKeys.accountId, accountId)
                                                               .filter(DelegateSelectionLogKeys.taskId, taskId)
                                                               .asList();
    Map<String, List<DelegateSelectionLog>> logs =
        delegateSelectionLogsList.stream().collect(Collectors.groupingBy(DelegateSelectionLog::getConclusion));
    List<Pair<String, List<DelegateSelectionLogParams>>> delegateSelectionLogs = new ArrayList<>();
    for (String assessment : selectionLogOrder) {
      if (logs.containsKey(assessment)) {
        List<DelegateSelectionLogParams> delegateSelectionLogParams =
            logs.get(assessment).stream().map(this::buildSelectionLogParams).collect(Collectors.toList());
        delegateSelectionLogs.add(Pair.of(assessment, delegateSelectionLogParams));
      }
    }
    return delegateSelectionLogs;
  }

  @Override
  public DelegateSelectionLogResponse fetchTaskSelectionLogsData(String accountId, String taskId) {
    List<DelegateSelectionLogParams> delegateSelectionLogParams = fetchTaskSelectionLogs(accountId, taskId);
    DelegateSelectionLogTaskMetadata taskMetadata = persistence.createQuery(DelegateSelectionLogTaskMetadata.class)
                                                        .filter(DelegateSelectionLogKeys.accountId, accountId)
                                                        .filter(DelegateSelectionLogKeys.taskId, taskId)
                                                        .get();

    Map<String, String> previewSetupAbstractions = new HashMap<>();
    if (taskMetadata != null && taskMetadata.getSetupAbstractions() != null) {
      previewSetupAbstractions =
          taskMetadata.getSetupAbstractions()
              .entrySet()
              .stream()
              .filter(map
                  -> Cd1SetupFields.APPLICATION.equals(map.getKey()) || Cd1SetupFields.SERVICE.equals(map.getKey())
                      || Cd1SetupFields.ENVIRONMENT.equals(map.getKey())
                      || Cd1SetupFields.ENVIRONMENT_TYPE.equals(map.getKey()))
              .collect(Collectors.toMap(Map.Entry::getKey, map -> String.valueOf(map.getValue())));
    }

    return DelegateSelectionLogResponse.builder()
        .delegateSelectionLogs(delegateSelectionLogParams)
        .taskSetupAbstractions(previewSetupAbstractions)
        .build();
  }

  @Override
  public Optional<DelegateSelectionLogParams> fetchSelectedDelegateForTask(String accountId, String taskId) {
    DelegateSelectionLog delegateSelectionLog = persistence.createQuery(DelegateSelectionLog.class)
                                                    .filter(DelegateSelectionLogKeys.accountId, accountId)
                                                    .filter(DelegateSelectionLogKeys.taskId, taskId)
                                                    .filter(DelegateSelectionLogKeys.conclusion, ASSIGNED)
                                                    .get();
    if (delegateSelectionLog == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(buildSelectionLogParams(delegateSelectionLog));
  }

  @Override
  public void logDelegateTaskInfo(DelegateTask delegateTask) {
    if (!delegateTask.isSelectionLogsTrackingEnabled()) {
      return;
    }
    String delegateSelectorReceived =
        CapabilityHelper.generateSelectionLogForSelectors(delegateTask.getExecutionCapabilities());
    if (isEmpty(delegateSelectorReceived)) {
      return;
    }
    save(DelegateSelectionLog.builder()
             .accountId(delegateTask.getAccountId())
             .taskId(delegateTask.getUuid())
             .conclusion(INFO)
             .message(delegateSelectorReceived)
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

  private Set<String> getDelegateHostNames(String accountId, Set<String> delegateIds) {
    return delegateIds.stream()
        .map(delegateId
            -> Optional.ofNullable(delegateCache.get(accountId, delegateId, false))
                   .map(Delegate::getHostName)
                   .orElse(delegateId))
        .collect(Collectors.toSet());
  }
}
