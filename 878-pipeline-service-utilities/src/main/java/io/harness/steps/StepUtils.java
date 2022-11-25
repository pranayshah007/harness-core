package io.harness.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

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
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.DelegateTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;
import io.harness.tasks.Task;

import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.apache.commons.collections4.ListUtils;

@OwnedBy(PIPELINE)
public class StepUtils {
  private StepUtils() {}

  public static final String DEFAULT_STEP_TIMEOUT = "10m";

  public static StepResponse createStepResponseFromChildResponse(Map<String, ResponseData> responseDataMap) {
    StepResponseBuilder responseBuilder = StepResponse.builder().status(Status.SUCCEEDED);

    List<Status> childStatuses = new LinkedList<>();
    String nodeExecutionId = "";

    for (ResponseData responseData : responseDataMap.values()) {
      StepResponseNotifyData responseNotifyData = (StepResponseNotifyData) responseData;
      Status executionStatus = responseNotifyData.getStatus();
      childStatuses.add(executionStatus);
      nodeExecutionId = responseNotifyData.getNodeUuid();
      if (StatusUtils.brokeStatuses().contains(executionStatus)) {
        responseBuilder.failureInfo(responseNotifyData.getFailureInfo());
      }
    }
    responseBuilder.status(StatusUtils.calculateStatusForNode(childStatuses, nodeExecutionId));
    return responseBuilder.build();
  }

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

  public static TaskRequest prepareTaskRequest(Ambiance ambiance, TaskData taskData, KryoSerializer kryoSerializer) {
    return prepareTaskRequest(
        ambiance, taskData, kryoSerializer, TaskCategory.DELEGATE_TASK_V2, Collections.emptyList(), true, null);
  }

  public static TaskRequest prepareTaskRequestWithTaskSelector(
      Ambiance ambiance, TaskData taskData, KryoSerializer kryoSerializer, List<TaskSelector> selectors) {
    return prepareTaskRequest(ambiance, taskData, kryoSerializer, TaskCategory.DELEGATE_TASK_V2,
        Collections.emptyList(), true, null, selectors, Scope.PROJECT, EnvironmentType.ALL);
  }

  public static TaskRequest prepareTaskRequestWithTaskSelector(Ambiance ambiance, TaskData taskData,
      KryoSerializer kryoSerializer, String taskName, List<TaskSelector> selectors) {
    return prepareTaskRequest(ambiance, taskData, kryoSerializer, TaskCategory.DELEGATE_TASK_V2,
        Collections.emptyList(), true, taskName, selectors, Scope.PROJECT, EnvironmentType.ALL);
  }

  public static TaskRequest prepareTaskRequestWithTaskSelector(Ambiance ambiance, TaskData taskData,
      KryoSerializer kryoSerializer, List<String> units, String taskName, List<TaskSelector> selectors) {
    return prepareTaskRequest(ambiance, taskData, kryoSerializer, TaskCategory.DELEGATE_TASK_V2, units, true, taskName,
        selectors, Scope.PROJECT, EnvironmentType.ALL);
  }

  public static TaskRequest prepareCDTaskRequest(Ambiance ambiance, TaskData taskData, KryoSerializer kryoSerializer,
      List<String> units, String taskName, List<TaskSelector> selectors, EnvironmentType environmentType) {
    return prepareTaskRequest(ambiance, taskData, kryoSerializer, TaskCategory.DELEGATE_TASK_V2, units, true, taskName,
        selectors, Scope.PROJECT, environmentType);
  }

  public static TaskRequest prepareTaskRequestWithoutLogs(
      Ambiance ambiance, TaskData taskData, KryoSerializer kryoSerializer) {
    return prepareTaskRequest(
        ambiance, taskData, kryoSerializer, TaskCategory.DELEGATE_TASK_V2, Collections.emptyList(), false, null);
  }

  public static TaskRequest prepareTaskRequest(
      Ambiance ambiance, TaskData taskData, KryoSerializer kryoSerializer, List<String> units, String taskName) {
    return prepareTaskRequest(ambiance, taskData, kryoSerializer, TaskCategory.DELEGATE_TASK_V2, units, true, taskName);
  }

  public static TaskRequest prepareTaskRequestWithTaskSelector(Ambiance ambiance, TaskData taskData,
      KryoSerializer kryoSerializer, TaskCategory taskCategory, List<String> units, boolean withLogs, String taskName,
      List<TaskSelector> selectors) {
    return prepareTaskRequest(ambiance, taskData, kryoSerializer, taskCategory, units, withLogs, taskName, selectors,
        Scope.PROJECT, EnvironmentType.ALL);
  }

  public static TaskRequest prepareTaskRequest(Ambiance ambiance, TaskData taskData, KryoSerializer kryoSerializer,
      TaskCategory taskCategory, List<String> units, boolean withLogs, String taskName) {
    return prepareTaskRequest(ambiance, taskData, kryoSerializer, taskCategory, units, withLogs, taskName, null,
        Scope.PROJECT, EnvironmentType.ALL);
  }

  public static TaskRequest prepareTaskRequest(Ambiance ambiance, TaskData taskData, KryoSerializer kryoSerializer,
      TaskCategory taskCategory, List<String> units, boolean withLogs, String taskName, List<TaskSelector> selectors,
      Scope taskScope, EnvironmentType environmentType) {
    String accountId = Preconditions.checkNotNull(ambiance.getSetupAbstractionsMap().get("accountId"));
    TaskParameters taskParameters = (TaskParameters) taskData.getParameters()[0];
    List<ExecutionCapability> capabilities = new ArrayList<>();
    if (taskParameters instanceof ExecutionCapabilityDemander) {
      capabilities = ListUtils.emptyIfNull(
          ((ExecutionCapabilityDemander) taskParameters).fetchRequiredExecutionCapabilities(null));
    }
    LinkedHashMap<String, String> logAbstractionMap =
        withLogs ? generateLogAbstractions(ambiance) : new LinkedHashMap<>();
    units = withLogs ? units : new ArrayList<>();

    TaskDetails taskDetails =
        TaskDetails.newBuilder()
            .setKryoParameters(ByteString.copyFrom(kryoSerializer.asDeflatedBytes(taskParameters) == null
                    ? new byte[] {}
                    : kryoSerializer.asDeflatedBytes(taskParameters)))
            .setExecutionTimeout(Duration.newBuilder().setSeconds(taskData.getTimeout() / 1000).build())
            .setExpressionFunctorToken(ambiance.getExpressionFunctorToken())
            .setMode(taskData.isAsync() ? TaskMode.ASYNC : TaskMode.SYNC)
            .setParked(taskData.isParked())
            .setType(TaskType.newBuilder().setType(taskData.getTaskType()).build())
            .build();

    Map<String, String> setupAbstractionsMap = buildAbstractions(ambiance, taskScope);
    if (environmentType != null && environmentType != EnvironmentType.ALL) {
      setupAbstractionsMap.put("envType", environmentType.name());
    }

    SubmitTaskRequest.Builder requestBuilder =
        SubmitTaskRequest.newBuilder()
            .setAccountId(AccountId.newBuilder().setId(accountId).build())
            .setDetails(taskDetails)
            .addAllSelectors(CollectionUtils.emptyIfNull(selectors))
            .setLogAbstractions(TaskLogAbstractions.newBuilder().putAllValues(logAbstractionMap).build())
            .setSetupAbstractions(TaskSetupAbstractions.newBuilder().putAllValues(setupAbstractionsMap).build())
            .setSelectionTrackingLogEnabled(true);

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

    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.newBuilder()
            .addAllUnits(CollectionUtils.emptyIfNull(units))
            .addAllLogKeys(CollectionUtils.emptyIfNull(generateLogKeys(logAbstractionMap, units)))
            .setRequest(requestBuilder.build())
            .setTaskName(taskName == null ? taskData.getTaskType() : taskName)
            .build();

    return TaskRequest.newBuilder().setDelegateTaskRequest(delegateTaskRequest).setTaskCategory(taskCategory).build();
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
}
