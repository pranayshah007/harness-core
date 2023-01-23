/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.steps;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.AccountId;
import io.harness.delegate.Capability;
import io.harness.delegate.SubmitTaskRequest;
import io.harness.delegate.TaskDetails;
import io.harness.delegate.TaskLogAbstractions;
import io.harness.delegate.TaskMode;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.TaskSetupAbstractions;
import io.harness.delegate.TaskType;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.MaskingExpressionEvaluator;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.DelegateTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.SerializationFormat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import io.fabric8.utils.Strings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;

/**
 * This util should be used to create delegate task. StepUtils will become deprecated and all tasks need to eventually
 * use this
 */
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class TaskRequestsUtils {
  public static TaskRequest prepareTaskRequest(Ambiance ambiance, TaskData taskData, KryoSerializer kryoSerializer,
      boolean executeOnHarnessHostedDelegates, List<String> eligibleToExecuteDelegateIds, boolean emitEvent,
      String stageId) {
    return prepareTaskRequest(ambiance, taskData, kryoSerializer, TaskCategory.DELEGATE_TASK_V2,
        Collections.emptyList(), true, null, executeOnHarnessHostedDelegates, eligibleToExecuteDelegateIds, emitEvent,
        stageId);
  }

  public static TaskRequest prepareCDTaskRequest(Ambiance ambiance, TaskData taskData, KryoSerializer kryoSerializer,
      List<String> units, String taskName, List<TaskSelector> selectors, EnvironmentType environmentType) {
    return prepareTaskRequest(ambiance, taskData, kryoSerializer, TaskCategory.DELEGATE_TASK_V2, units, true, taskName,
        selectors, Scope.PROJECT, environmentType, false, Collections.emptyList(), false, null);
  }

  public static TaskRequest prepareCDTaskRequest(Ambiance ambiance, TaskData taskData, KryoSerializer kryoSerializer,
      List<String> keys, List<String> units, String taskName, List<TaskSelector> selectors,
      EnvironmentType environmentType) {
    return prepareTaskRequest(ambiance, taskData, kryoSerializer, TaskCategory.DELEGATE_TASK_V2, keys, units, true,
        taskName, selectors, Scope.PROJECT, environmentType, false, Collections.emptyList(), false, null, null);
  }

  public static TaskRequest prepareTaskRequest(
      Ambiance ambiance, TaskData taskData, KryoSerializer kryoSerializer, List<String> units, String taskName) {
    return prepareTaskRequest(ambiance, taskData, kryoSerializer, TaskCategory.DELEGATE_TASK_V2, units, true, taskName,
        false, Collections.emptyList(), false, null);
  }

  public static TaskRequest prepareTaskRequestWithTaskSelector(
      Ambiance ambiance, TaskData taskData, KryoSerializer kryoSerializer, List<TaskSelector> selectors) {
    return prepareTaskRequest(ambiance, taskData, kryoSerializer, TaskCategory.DELEGATE_TASK_V2,
        Collections.emptyList(), true, null, selectors, Scope.PROJECT, EnvironmentType.ALL, false,
        Collections.emptyList(), false, null, new MaskingExpressionEvaluator());
  }

  public static TaskRequest prepareTaskRequestWithTaskSelector(Ambiance ambiance, TaskData taskData,
      KryoSerializer kryoSerializer, TaskCategory taskCategory, List<String> units, boolean withLogs, String taskName,
      List<TaskSelector> selectors) {
    return prepareTaskRequest(ambiance, taskData, kryoSerializer, taskCategory, units, withLogs, taskName, selectors,
        Scope.PROJECT, EnvironmentType.ALL, false, Collections.emptyList(), false, null);
  }

  public static TaskRequest prepareTaskRequest(Ambiance ambiance, TaskData taskData, KryoSerializer kryoSerializer,
      TaskCategory taskCategory, List<String> units, boolean withLogs, String taskName,
      boolean executeOnHarnessHostedDelegates, List<String> eligibleToExecuteDelegateIds, boolean emitEvent,
      String stageId) {
    return prepareTaskRequest(ambiance, taskData, kryoSerializer, taskCategory, units, withLogs, taskName, null,
        Scope.PROJECT, EnvironmentType.ALL, executeOnHarnessHostedDelegates, eligibleToExecuteDelegateIds, emitEvent,
        stageId);
  }

  public static TaskRequest prepareTaskRequest(Ambiance ambiance, TaskData taskData, KryoSerializer kryoSerializer,
      TaskCategory taskCategory, List<String> units, boolean withLogs, String taskName, List<TaskSelector> selectors,
      Scope taskScope, EnvironmentType environmentType, boolean executeOnHarnessHostedDelegates,
      List<String> eligibleToExecuteDelegateIds, boolean emitEvent, String stageId) {
    LinkedHashMap<String, String> logAbstractionMap =
        withLogs ? StepUtils.generateLogAbstractions(ambiance) : new LinkedHashMap<>();
    return prepareTaskRequest(ambiance, taskData, kryoSerializer, taskCategory,
        CollectionUtils.emptyIfNull(StepUtils.generateLogKeys(logAbstractionMap, units)), units, withLogs, taskName,
        selectors, taskScope, environmentType, executeOnHarnessHostedDelegates, eligibleToExecuteDelegateIds, emitEvent,
        stageId, null);
  }

  public static TaskRequest prepareTaskRequest(Ambiance ambiance, TaskData taskData, KryoSerializer kryoSerializer,
      TaskCategory taskCategory, List<String> units, boolean withLogs, String taskName, List<TaskSelector> selectors,
      Scope taskScope, EnvironmentType environmentType, boolean executeOnHarnessHostedDelegates,
      List<String> eligibleToExecuteDelegateIds, boolean emitEvent, String stageId,
      ExpressionEvaluator maskingEvaluator) {
    LinkedHashMap<String, String> logAbstractionMap =
        withLogs ? StepUtils.generateLogAbstractions(ambiance) : new LinkedHashMap<>();
    return prepareTaskRequest(ambiance, taskData, kryoSerializer, taskCategory,
        CollectionUtils.emptyIfNull(StepUtils.generateLogKeys(logAbstractionMap, units)), units, withLogs, taskName,
        selectors, taskScope, environmentType, executeOnHarnessHostedDelegates, eligibleToExecuteDelegateIds, emitEvent,
        stageId, maskingEvaluator);
  }

  public static TaskRequest prepareTaskRequest(Ambiance ambiance, TaskData taskData, KryoSerializer kryoSerializer,
      TaskCategory taskCategory, List<String> keys, List<String> units, boolean withLogs, String taskName,
      List<TaskSelector> selectors, Scope taskScope, EnvironmentType environmentType,
      boolean executeOnHarnessHostedDelegates, List<String> eligibleToExecuteDelegateIds, boolean emitEvent,
      String stageId, ExpressionEvaluator maskingEvaluator) {
    String accountId = Preconditions.checkNotNull(ambiance.getSetupAbstractionsMap().get("accountId"));
    TaskParameters taskParameters = (TaskParameters) taskData.getParameters()[0];
    List<ExecutionCapability> capabilities = new ArrayList<>();
    if (taskParameters instanceof ExecutionCapabilityDemander) {
      capabilities = ListUtils.emptyIfNull(
          ((ExecutionCapabilityDemander) taskParameters).fetchRequiredExecutionCapabilities(maskingEvaluator));
    }
    LinkedHashMap<String, String> logAbstractionMap =
        withLogs ? StepUtils.generateLogAbstractions(ambiance) : new LinkedHashMap<>();
    units = withLogs ? units : new ArrayList<>();
    logCommandUnits(units);

    TaskDetails.Builder taskDetailsBuilder =
        TaskDetails.newBuilder()
            .setExecutionTimeout(Duration.newBuilder().setSeconds(taskData.getTimeout() / 1000).build())
            .setExpressionFunctorToken(ambiance.getExpressionFunctorToken())
            .setMode(taskData.isAsync() ? TaskMode.ASYNC : TaskMode.SYNC)
            .setParked(taskData.isParked())
            .setType(TaskType.newBuilder().setType(taskData.getTaskType()).build());

    ObjectMapper objectMapper = new ObjectMapper();
    if (SerializationFormat.JSON.equals(taskData.getSerializationFormat())) {
      try {
        taskDetailsBuilder.setJsonParameters(ByteString.copyFrom(objectMapper.writeValueAsBytes(taskParameters)));
      } catch (JsonProcessingException e) {
        throw new InvalidRequestException("Could not serialize the task request", e);
      }
    } else {
      taskDetailsBuilder.setKryoParameters(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(taskParameters) == null
              ? new byte[] {}
              : kryoSerializer.asDeflatedBytes(taskParameters)));
    }

    Map<String, String> setupAbstractionsMap = StepUtils.buildAbstractions(ambiance, taskScope);
    if (environmentType != null && environmentType != EnvironmentType.ALL) {
      setupAbstractionsMap.put("envType", environmentType.name());
    }

    SubmitTaskRequest.Builder requestBuilder =
        SubmitTaskRequest.newBuilder()
            .setAccountId(AccountId.newBuilder().setId(accountId).build())
            .setDetails(taskDetailsBuilder.build())
            .setExecuteOnHarnessHostedDelegates(executeOnHarnessHostedDelegates)
            .setEmitEvent(emitEvent)
            .addAllSelectors(CollectionUtils.emptyIfNull(selectors))
            .setLogAbstractions(TaskLogAbstractions.newBuilder().putAllValues(logAbstractionMap).build())
            .setSetupAbstractions(TaskSetupAbstractions.newBuilder().putAllValues(setupAbstractionsMap).build())
            .setSelectionTrackingLogEnabled(true);

    if (Strings.isNotBlank(stageId)) {
      requestBuilder.setStageId(stageId);
    }

    if (isNotEmpty(capabilities)) {
      requestBuilder.addAllCapabilities(
          capabilities.stream()
              .map(capability
                  -> Capability.newBuilder()
                         .setKryoCapability(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(capability) == null
                                 ? new byte[] {}
                                 : kryoSerializer.asDeflatedBytes(capability)))
                         .build())
              .collect(toList()));
    }

    if (isNotEmpty(eligibleToExecuteDelegateIds)) {
      requestBuilder.addAllEligibleToExecuteDelegateIds(eligibleToExecuteDelegateIds.stream().collect(toList()));
    }

    DelegateTaskRequest delegateTaskRequest = DelegateTaskRequest.newBuilder()
                                                  .addAllUnits(CollectionUtils.emptyIfNull(units))
                                                  .addAllLogKeys(keys)
                                                  .setRequest(requestBuilder.build())
                                                  .setTaskName(taskName == null ? taskData.getTaskType() : taskName)
                                                  .build();

    return TaskRequest.newBuilder()
        .setUseReferenceFalseKryoSerializer(true)
        .setDelegateTaskRequest(delegateTaskRequest)
        .setTaskCategory(taskCategory)
        .build();
  }

  public static TaskRequest prepareTaskRequest(Ambiance ambiance, TaskDetails taskDetails, List<String> units,
      List<TaskSelector> selectors, String taskName, boolean withLogs) {
    SubmitTaskRequest submitTaskRequest =
        SubmitTaskRequest.newBuilder()
            .setAccountId(AccountId.newBuilder().setId(AmbianceUtils.getAccountId(ambiance)).build())
            .setDetails(taskDetails)
            .setSetupAbstractions(TaskSetupAbstractions.newBuilder()
                                      .putAllValues(StepUtils.buildAbstractions(ambiance, Scope.PROJECT))
                                      .build())
            .addAllSelectors(CollectionUtils.emptyIfNull(selectors))
            .setLogAbstractions(
                TaskLogAbstractions.newBuilder()
                    .putAllValues(withLogs ? StepUtils.generateLogAbstractions(ambiance) : Collections.emptyMap())
                    .build())
            .setSelectionTrackingLogEnabled(true)
            .build();
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.newBuilder()
            .setRequest(submitTaskRequest)
            .addAllUnits(withLogs ? CollectionUtils.emptyIfNull(units) : Collections.emptyList())
            .addAllLogKeys(withLogs ? CollectionUtils.emptyIfNull(StepUtils.generateLogKeys(ambiance, units))
                                    : Collections.emptyList())
            .setTaskName(taskName == null ? taskDetails.getType().getType() : taskName)
            .build();
    return TaskRequest.newBuilder()
        .setDelegateTaskRequest(delegateTaskRequest)
        .setUseReferenceFalseKryoSerializer(true)
        .setTaskCategory(TaskCategory.DELEGATE_TASK_V2)
        .build();
  }

  private static void logCommandUnits(List<String> units) {
    if (!isEmpty(units)) {
      String commandUnits =
          "Sending following command units to delegate task: [" + Joiner.on(", ").skipNulls().join(units) + "]";
      log.info(commandUnits);
    }
  }
}
