/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.yaml.YAMLFieldNameConstants.DELEGATE_SELECTORS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STAGE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP_GROUP;

import static software.wings.beans.LogHelper.COMMAND_UNIT_PLACEHOLDER;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.common.NGTimeConversionHelper;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
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
import io.harness.delegate.task.SimpleHDelegateTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingHelper;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.DelegateTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.Task;
import io.harness.yaml.core.StepSpecType;

import software.wings.beans.SerializationFormat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import io.fabric8.utils.Strings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;

@OwnedBy(PIPELINE)
@Slf4j
public class StepUtils {
  private StepUtils() {}

  public static final String DEFAULT_STEP_TIMEOUT = "10m";

  public static Task prepareDelegateTaskInput(
      String accountId, TaskData taskData, Map<String, String> setupAbstractions) {
    return createHDelegateTask(accountId, taskData, setupAbstractions, new LinkedHashMap<>());
  }

  public static Task prepareDelegateTaskInput(String accountId, TaskData taskData,
      Map<String, String> setupAbstractions, LinkedHashMap<String, String> logAbstractions) {
    return createHDelegateTask(accountId, taskData, setupAbstractions, logAbstractions);
  }
  private static Task createHDelegateTask(String accountId, TaskData taskData, Map<String, String> setupAbstractions,
      LinkedHashMap<String, String> logAbstractions) {
    return SimpleHDelegateTask.builder()
        .accountId(accountId)
        .data(taskData)
        .setupAbstractions(setupAbstractions)
        .logStreamingAbstractions(logAbstractions)
        .build();
  }

  @Nonnull
  public static LinkedHashMap<String, String> generateLogAbstractions(Ambiance ambiance, String lastGroup) {
    LinkedHashMap<String, String> logAbstractions = new LinkedHashMap<>();
    logAbstractions.put("accountId", ambiance.getSetupAbstractionsMap().getOrDefault("accountId", ""));
    logAbstractions.put("orgId", ambiance.getSetupAbstractionsMap().getOrDefault("orgIdentifier", ""));
    logAbstractions.put("projectId", ambiance.getSetupAbstractionsMap().getOrDefault("projectIdentifier", ""));
    logAbstractions.put("pipelineId", ambiance.getMetadata().getPipelineIdentifier());
    logAbstractions.put("runSequence", String.valueOf(ambiance.getMetadata().getRunSequence()));
    for (int i = 0; i < ambiance.getLevelsList().size(); i++) {
      Level currentLevel = ambiance.getLevelsList().get(i);
      String retrySuffix = currentLevel.getRetryIndex() > 0 ? String.format("_%s", currentLevel.getRetryIndex()) : "";
      logAbstractions.put("level" + i, currentLevel.getIdentifier() + retrySuffix);
      if (lastGroup != null && lastGroup.equals(currentLevel.getGroup())) {
        break;
      }
    }
    return logAbstractions;
  }

  @Nonnull
  public static LinkedHashMap<String, String> generateLogAbstractions(Ambiance ambiance) {
    return generateLogAbstractions(ambiance, null);
  }

  public static TaskRequest prepareTaskRequest(Ambiance ambiance, TaskData taskData, KryoSerializer kryoSerializer,
      boolean executeOnHarnessHostedDelegates, List<String> eligibleToExecuteDelegateIds, boolean emitEvent,
      String stageId) {
    return prepareTaskRequest(ambiance, taskData, kryoSerializer, TaskCategory.DELEGATE_TASK_V2,
        Collections.emptyList(), true, null, executeOnHarnessHostedDelegates, eligibleToExecuteDelegateIds, emitEvent,
        stageId);
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
        withLogs ? generateLogAbstractions(ambiance) : new LinkedHashMap<>();
    return prepareTaskRequest(ambiance, taskData, kryoSerializer, taskCategory,
        CollectionUtils.emptyIfNull(generateLogKeys(logAbstractionMap, units)), units, withLogs, taskName, selectors,
        taskScope, environmentType, executeOnHarnessHostedDelegates, eligibleToExecuteDelegateIds, emitEvent, stageId,
        null);
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
        withLogs ? generateLogAbstractions(ambiance) : new LinkedHashMap<>();
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

    Map<String, String> setupAbstractionsMap = buildAbstractions(ambiance, taskScope);
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

    return TaskRequest.newBuilder().setDelegateTaskRequest(delegateTaskRequest).setTaskCategory(taskCategory).build();
  }

  private static void logCommandUnits(List<String> units) {
    if (!isEmpty(units)) {
      String commandUnits =
          "Sending following command units to delegate task: [" + Joiner.on(", ").skipNulls().join(units) + "]";
      log.info(commandUnits);
    }
  }

  public static TaskRequest prepareTaskRequest(
      Ambiance ambiance, TaskDetails taskDetails, List<String> units, List<TaskSelector> selectors, String taskName) {
    return prepareTaskRequest(ambiance, taskDetails, units, selectors, taskName, true);
  }

  public static TaskRequest prepareTaskRequest(Ambiance ambiance, TaskDetails taskDetails, List<String> units,
      List<TaskSelector> selectors, String taskName, boolean withLogs) {
    SubmitTaskRequest submitTaskRequest =
        SubmitTaskRequest.newBuilder()
            .setAccountId(AccountId.newBuilder().setId(AmbianceUtils.getAccountId(ambiance)).build())
            .setDetails(taskDetails)
            .setSetupAbstractions(
                TaskSetupAbstractions.newBuilder().putAllValues(buildAbstractions(ambiance, Scope.PROJECT)).build())
            .addAllSelectors(CollectionUtils.emptyIfNull(selectors))
            .setLogAbstractions(TaskLogAbstractions.newBuilder()
                                    .putAllValues(withLogs ? generateLogAbstractions(ambiance) : Collections.emptyMap())
                                    .build())
            .setSelectionTrackingLogEnabled(true)
            .build();
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.newBuilder()
            .setRequest(submitTaskRequest)
            .addAllUnits(withLogs ? CollectionUtils.emptyIfNull(units) : Collections.emptyList())
            .addAllLogKeys(
                withLogs ? CollectionUtils.emptyIfNull(generateLogKeys(ambiance, units)) : Collections.emptyList())
            .setTaskName(taskName == null ? taskDetails.getType().getType() : taskName)
            .build();
    return TaskRequest.newBuilder()
        .setDelegateTaskRequest(delegateTaskRequest)
        .setTaskCategory(TaskCategory.DELEGATE_TASK_V2)
        .build();
  }

  public static Map<String, String> buildAbstractions(Ambiance ambiance, Scope taskScope) {
    Map<String, String> setupMap = new HashMap<>();
    if (!isEmpty(ambiance.getSetupAbstractionsMap())) {
      setupMap.putAll(ambiance.getSetupAbstractionsMap());
    }
    setupMap.put("ng", "true");
    switch (taskScope) {
      case ORG:
        setupMap.put("owner", AmbianceUtils.getOrgIdentifier(ambiance));
        break;
      case PROJECT:
        setupMap.put(
            "owner", AmbianceUtils.getOrgIdentifier(ambiance) + "/" + AmbianceUtils.getProjectIdentifier(ambiance));
        break;
      case UNKNOWN:
      case ACCOUNT:
      default:
        // Doing Nothing here no owner key verify this behaviour
        break;
    }
    return setupMap;
  }

  public static List<String> generateLogKeys(Ambiance ambiance, List<String> units) {
    LinkedHashMap<String, String> logAbstractionMap = generateLogAbstractions(ambiance);
    return generateLogKeys(logAbstractionMap, units);
  }

  public static List<String> generateLogKeys(LinkedHashMap<String, String> logAbstractionMap, List<String> units) {
    if (isEmpty(logAbstractionMap)) {
      return Collections.emptyList();
    }
    String baseLogKey = LogStreamingHelper.generateLogBaseKey(logAbstractionMap);
    if (isEmpty(units)) {
      return Collections.singletonList(baseLogKey);
    }

    List<String> unitKeys = new ArrayList<>();
    for (String unit : units) {
      String logKey = baseLogKey + (isEmpty(baseLogKey) ? "" : String.format(COMMAND_UNIT_PLACEHOLDER, unit));
      unitKeys.add(logKey);
    }
    return unitKeys;
  }

  public static boolean isStepInRollbackSection(Ambiance ambiance) {
    List<Level> levelsList = ambiance.getLevelsList();
    if (isEmpty(levelsList)) {
      return false;
    }

    Optional<Level> optionalLevel =
        ambiance.getLevelsList()
            .stream()
            .filter(level -> level.getStepType().getType().equals("ROLLBACK_OPTIONAL_CHILD_CHAIN"))
            .findFirst();

    return optionalLevel.isPresent();
  }

  public static long getTimeoutMillis(ParameterField<String> timeout, String defaultTimeout) {
    String timeoutString;
    if (ParameterField.isNull(timeout) || EmptyPredicate.isEmpty(timeout.getValue())) {
      timeoutString = defaultTimeout;
    } else {
      timeoutString = timeout.getValue();
    }
    return NGTimeConversionHelper.convertTimeStringToMilliseconds(timeoutString);
  }

  public static List<TaskSelector> getTaskSelectors(ParameterField<List<String>> delegateSelectors) {
    return getDelegateSelectorList(delegateSelectors)
        .stream()
        .map(delegateSelector -> TaskSelector.newBuilder().setSelector(delegateSelector).build())
        .collect(toList());
  }

  public static List<String> getDelegateSelectorList(ParameterField<List<String>> delegateSelectors) {
    if (ParameterField.isNull(delegateSelectors) || delegateSelectors.getValue() == null) {
      return new ArrayList<>();
    }
    return delegateSelectors.getValue();
  }

  public static List<String> getDelegateSelectorListFromTaskSelectorYaml(
      ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    if (ParameterField.isNull(delegateSelectors) || delegateSelectors.getValue() == null) {
      return new ArrayList<>();
    }
    return delegateSelectors.getValue()
        .stream()
        .map(TaskSelectorYaml::getDelegateSelectors)
        .collect(Collectors.toList());
  }

  public static Status getStepStatus(CommandExecutionStatus commandExecutionStatus) {
    if (commandExecutionStatus == null) {
      return null;
    }
    switch (commandExecutionStatus) {
      case SUCCESS:
        return Status.SUCCEEDED;
      case QUEUED:
        return Status.QUEUED;
      case SKIPPED:
        return Status.SKIPPED;
      case FAILURE:
        return Status.FAILED;
      case RUNNING:
        return Status.RUNNING;
      default:
        throw new InvalidRequestException(
            "Unhandled type CommandExecutionStatus: " + commandExecutionStatus, WingsException.USER);
    }
  }

  public static ParameterField<List<TaskSelectorYaml>> delegateSelectorsFromFqn(PlanCreationContext ctx, String fqn)
      throws IOException {
    ParameterField<List<TaskSelectorYaml>> delegateSelectors = null;
    YamlNode node = YamlUtils.getGivenYamlNodeFromParentPath(ctx.getCurrentField().getNode(), fqn);
    if (node == null) {
      return delegateSelectors;
    }
    YamlField delegateSelectorStageField = node.getField(DELEGATE_SELECTORS);
    if (delegateSelectorStageField != null) {
      delegateSelectors = YamlUtils.read(delegateSelectorStageField.getNode().toString(),
          new TypeReference<ParameterField<List<TaskSelectorYaml>>>() {});
    }
    return delegateSelectors;
  }

  public static void appendDelegateSelectorsToSpecParameters(StepSpecType stepSpecType, PlanCreationContext ctx) {
    try {
      if (stepSpecType instanceof WithDelegateSelector) {
        WithDelegateSelector withDelegateSelector = (WithDelegateSelector) stepSpecType;
        // Delegate Selector Precedence: 1)Step -> 2)stepGroup -> 3)Stage ->  4)Pipeline

        ParameterField<List<TaskSelectorYaml>> delegateSelectors = withDelegateSelector.fetchDelegateSelectors();
        if (hasDelegateSelectors(delegateSelectors)) {
          setOriginAndDelegateSelectors(delegateSelectors, withDelegateSelector, STEP);
          return;
        }

        delegateSelectors = delegateSelectorsFromFqn(ctx, STEP_GROUP);
        if (hasDelegateSelectors(delegateSelectors)) {
          setOriginAndDelegateSelectors(delegateSelectors, withDelegateSelector, STEP_GROUP);
          return;
        }

        delegateSelectors = delegateSelectorsFromFqn(ctx, STAGE);
        if (hasDelegateSelectors(delegateSelectors)) {
          setOriginAndDelegateSelectors(delegateSelectors, withDelegateSelector, STAGE);
          return;
        }

        delegateSelectors = delegateSelectorsFromFqn(ctx, YAMLFieldNameConstants.PIPELINE);
        if (hasDelegateSelectors(delegateSelectors)) {
          setOriginAndDelegateSelectors(delegateSelectors, withDelegateSelector, YAMLFieldNameConstants.PIPELINE);
        }
      }
    } catch (Exception e) {
      log.error("Error while appending delegate selector to spec params ", e);
    }
  }

  private static void setOriginAndDelegateSelectors(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      WithDelegateSelector withDelegateSelector, String origin) {
    if (!delegateSelectors.isExpression()) {
      delegateSelectors.getValue().forEach(selector -> selector.setOrigin(origin));
    }
    withDelegateSelector.setDelegateSelectors(delegateSelectors);
  }

  private static boolean hasDelegateSelectors(ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    if (ParameterField.isNull(delegateSelectors)) {
      return false;
    }
    if (delegateSelectors.isExpression()) {
      return true;
    }
    if (isEmpty(delegateSelectors.getValue())) {
      return false;
    }
    List<TaskSelectorYaml> selectorYamls = delegateSelectors.getValue()
                                               .stream()
                                               .filter(Objects::nonNull)
                                               .filter(s -> isNotEmpty(s.getDelegateSelectors()))
                                               .collect(toList());
    if (selectorYamls.isEmpty()) {
      return false;
    }
    return true;
  }
}
