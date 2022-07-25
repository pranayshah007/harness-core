/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.EnvironmentType.ALL;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.ListUtils.trimStrings;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;

import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.ShellScriptProvisionOutputVariables;
import io.harness.beans.SweepingOutputInstance;
import io.harness.context.ContextElementType;
import io.harness.data.algorithm.HashGenerator;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.expression.ExpressionReflectionUtils;
import io.harness.ff.FeatureFlagService;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;

import software.wings.api.ScriptStateExecutionData;
import software.wings.api.ShellScriptProvisionerOutputElement;
import software.wings.api.shellscript.provision.ShellScriptProvisionExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.NameValuePair;
import software.wings.beans.TaskType;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandType;
import software.wings.beans.shellscript.provisioner.ShellScriptInfrastructureProvisioner;
import software.wings.beans.shellscript.provisioner.ShellScriptProvisionParameters;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateExecutionContext;
import software.wings.sm.StateType;
import software.wings.sm.states.ManagerExecutionLogCallback;
import software.wings.sm.states.mixin.SweepingOutputStateMixin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.annotations.Transient;

@FieldNameConstants(onlyExplicitlyIncluded = true, innerTypeName = "ShellScriptProvisionStateKeys")
@OwnedBy(CDP)
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class ShellScriptProvisionState extends State implements SweepingOutputStateMixin {
  private static final int TIMEOUT_IN_MINUTES = 20;
  private static final String COMMAND_UNIT = "Shell Script Provision";
  private static final String PROVISIONER_OUTPUT_PATH_KEY = "PROVISIONER_OUTPUT_PATH";
  @Inject private InfrastructureProvisionerService infrastructureProvisionerService;
  @Inject private ActivityService activityService;
  @Inject private DelegateService delegateService;
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject protected FeatureFlagService featureFlagService;
  @FieldNameConstants.Include @Getter @Setter private String provisionerId;
  @FieldNameConstants.Include @Getter @Setter private List<NameValuePair> variables;
  @Getter @Setter private String sweepingOutputName;
  @Getter @Setter private SweepingOutputInstance.Scope sweepingOutputScope;
  @Getter @Setter private List<String> delegateSelectors;

  @Transient @Inject KryoSerializer kryoSerializer;

  public ShellScriptProvisionState(String name) {
    super(name, StateType.SHELL_SCRIPT_PROVISION.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    String activityId = createActivity(context);
    ShellScriptInfrastructureProvisioner shellScriptProvisioner = resolveProvisionerId(context);

    ShellScriptProvisionParameters parameters =
        ShellScriptProvisionParameters.builder()
            .scriptBody(shellScriptProvisioner.getScriptBody())
            .textVariables(infrastructureProvisionerService.extractTextVariables(variables, context))
            .encryptedVariables(infrastructureProvisionerService.extractEncryptedTextVariables(
                variables, context.getAppId(), context.getWorkflowExecutionId()))
            .timeoutInMillis(TimeUnit.MINUTES.toMillis(TIMEOUT_IN_MINUTES))
            .accountId(context.getAccountId())
            .appId(context.getAppId())
            .activityId(activityId)
            .commandUnit(COMMAND_UNIT)
            .entityId(infrastructureProvisionerService.getEntityId(
                provisionerId, Objects.requireNonNull(((ExecutionContextImpl) context).getEnv()).getUuid()))
            .workflowExecutionId(context.getWorkflowExecutionId())
            .outputPathKey(PROVISIONER_OUTPUT_PATH_KEY)
            .delegateSelectors(getRenderedAndTrimmedSelectors(context))
            .build();

    int expressionFunctorToken = HashGenerator.generateIntegerHash();
    renderTaskParameters(context, parameters, expressionFunctorToken);

    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(context.getAccountId())
                                    .waitId(activityId)
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, context.getAppId())
                                    .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
                                    .description("Shell script provision task")
                                    .data(TaskData.builder()
                                              .async(true)
                                              .taskType(TaskType.SHELL_SCRIPT_PROVISION_TASK.toString())
                                              .parameters(new Object[] {parameters})
                                              .timeout(defaultIfNullTimeout(DEFAULT_ASYNC_CALL_TIMEOUT))
                                              .expressionFunctorToken(expressionFunctorToken)
                                              .build())
                                    .build();

    String delegateTaskId = delegateService.queueTask(delegateTask);
    appendDelegateTaskDetails(context, delegateTask);
    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Collections.singletonList(activityId))
        .delegateTaskId(delegateTaskId)
        .stateExecutionData(ScriptStateExecutionData.builder().activityId(activityId).build())
        .build();
  }

  private ShellScriptInfrastructureProvisioner resolveProvisionerId(ExecutionContext context) {
    String renderedProvisionerName = context.renderExpression(provisionerId);
    InfrastructureProvisioner provisioner =
        infrastructureProvisionerService.getByName(context.getAppId(), renderedProvisionerName);

    if (!StringUtils.equals(provisionerId, renderedProvisionerName) && Objects.isNull(provisioner)) {
      throw new InvalidArgumentsException(
          "Could not find a Shell Script Provisioner with resolved name: " + renderedProvisionerName);
    }

    if (Objects.nonNull(provisioner)) {
      if (provisioner instanceof ShellScriptInfrastructureProvisioner) {
        provisionerId = provisioner.getUuid();
        return (ShellScriptInfrastructureProvisioner) provisioner;
      } else {
        throw new InvalidArgumentsException(
            "Resolved Provisioner with name: " + renderedProvisionerName + ", is not of type Shell Script");
      }
    }

    return infrastructureProvisionerService.getShellScriptProvisioner(context.getAppId(), provisionerId);
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    resolveProvisionerId(context);
    Entry<String, ResponseData> responseEntry = response.entrySet().iterator().next();
    String activityId = responseEntry.getKey();
    ShellScriptProvisionExecutionData executionData = (ShellScriptProvisionExecutionData) responseEntry.getValue();
    executionData.setActivityId(activityId);

    ShellScriptProvisionerOutputElement outputInfoElement =
        context.getContextElement(ContextElementType.SHELL_SCRIPT_PROVISION);
    if (outputInfoElement == null) {
      outputInfoElement = ShellScriptProvisionerOutputElement.builder().build();
    }

    if (executionData.getExecutionStatus() == ExecutionStatus.SUCCESS) {
      String output = executionData.getOutput();
      Map<String, Object> outputMap = parseOutput(output);

      boolean isSavingOutputToSweepingOutput = featureFlagService.isEnabled(
          FeatureName.SAVE_SHELL_SCRIPT_PROVISION_OUTPUTS_TO_SWEEPING_OUTPUT, context.getAccountId());
      validateReserveNameForProvisionerOutput(isSavingOutputToSweepingOutput);

      handleSweepingOutput(sweepingOutputService, context, outputMap);

      if (isSavingOutputToSweepingOutput) {
        saveOutputs(context, outputMap);
      } else {
        outputInfoElement.addOutPuts(outputMap);
      }
      ManagerExecutionLogCallback managerExecutionCallback =
          infrastructureProvisionerService.getManagerExecutionCallback(context.getAppId(), activityId, COMMAND_UNIT);
      infrastructureProvisionerService.regenerateInfrastructureMappings(
          provisionerId, context, outputMap, Optional.of(managerExecutionCallback), Optional.empty());
    }

    activityService.updateStatus(activityId, context.getAppId(), executionData.getExecutionStatus());
    return ExecutionResponse.builder()
        .stateExecutionData(executionData)
        .contextElement(outputInfoElement)
        .notifyElement(outputInfoElement)
        .executionStatus(executionData.getExecutionStatus())
        .errorMessage(executionData.getErrorMsg())
        .build();
  }

  private void validateReserveNameForProvisionerOutput(boolean isSavingOutputToSweepingOutput) {
    if (isSavingOutputToSweepingOutput
        && ShellScriptProvisionOutputVariables.SWEEPING_OUTPUT_NAME.equals(getSweepingOutputName())) {
      throw new InvalidArgumentsException(
          format("Output variables can not be exported in context with reserved name: %s ",
              ShellScriptProvisionOutputVariables.SWEEPING_OUTPUT_NAME));
    }
  }

  private void saveOutputs(ExecutionContext context, Map<String, Object> outputMap) {
    ShellScriptProvisionerOutputElement outputInfoElement =
        context.getContextElement(ContextElementType.SHELL_SCRIPT_PROVISION);
    SweepingOutputInstance instance =
        sweepingOutputService.find(context.prepareSweepingOutputInquiryBuilder()
                                       .name(ShellScriptProvisionOutputVariables.SWEEPING_OUTPUT_NAME)
                                       .build());
    ShellScriptProvisionOutputVariables scriptProvisionOutputVariables = instance != null
        ? (ShellScriptProvisionOutputVariables) instance.getValue()
        : new ShellScriptProvisionOutputVariables();

    scriptProvisionOutputVariables.putAll(outputMap);
    if (outputInfoElement != null && outputInfoElement.getOutputVariables() != null) {
      // Ensure that we're not missing any variables during migration from context element to sweeping output
      // can be removed with the next releases
      scriptProvisionOutputVariables.putAll(outputInfoElement.getOutputVariables());
    }

    if (instance != null) {
      sweepingOutputService.deleteById(context.getAppId(), instance.getUuid());
    }

    sweepingOutputService.save(context.prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW)
                                   .name(ShellScriptProvisionOutputVariables.SWEEPING_OUTPUT_NAME)
                                   .value(scriptProvisionOutputVariables)
                                   .build());
  }

  @VisibleForTesting
  Map<String, Object> parseOutput(String output) {
    Map<String, Object> outputs = new LinkedHashMap<>();
    if (isBlank(output)) {
      return outputs;
    }

    TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {};
    try {
      return new ObjectMapper().readValue(output, typeRef);
    } catch (IOException e) {
      log.error("Output : " + output);
      throw new InvalidRequestException("Not a json type output", WingsException.USER);
    }
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // nothing to do
  }

  private String createActivity(ExecutionContext executionContext) {
    Application app = ((ExecutionContextImpl) executionContext).getApp();
    Environment env = ((ExecutionContextImpl) executionContext).getEnv();

    ActivityBuilder activityBuilder =
        Activity.builder()
            .applicationName(Objects.requireNonNull(app).getName())
            .commandName(getName())
            .type(Type.Command)
            .workflowType(executionContext.getWorkflowType())
            .workflowExecutionName(executionContext.getWorkflowExecutionName())
            .stateExecutionInstanceId(executionContext.getStateExecutionInstanceId())
            .stateExecutionInstanceName(executionContext.getStateExecutionInstanceName())
            .commandType(getStateType())
            .workflowExecutionId(executionContext.getWorkflowExecutionId())
            .workflowId(executionContext.getWorkflowId())
            .commandUnits(
                asList(Command.Builder.aCommand().withName(COMMAND_UNIT).withCommandType(CommandType.OTHER).build()))
            .status(ExecutionStatus.RUNNING);

    if (executionContext.getOrchestrationWorkflowType() != null
        && executionContext.getOrchestrationWorkflowType() == BUILD) {
      activityBuilder.environmentId(GLOBAL_ENV_ID).environmentName(GLOBAL_ENV_ID).environmentType(ALL);
    } else {
      activityBuilder.environmentId(env.getUuid())
          .environmentName(env.getName())
          .environmentType(env.getEnvironmentType());
    }

    Activity activity = activityBuilder.build();
    activity.setAppId(app.getUuid());
    return activityService.save(activity).getUuid();
  }

  @Override
  public Map<String, String> validateFields() {
    Map<String, String> results = new HashMap<>();
    if (isEmpty(provisionerId)) {
      results.put("Provisioner", "Provisioner must be provided.");
    }
    // if more fields need to validated, please make sure templatized fields are not broken.
    return results;
  }

  @Override
  public KryoSerializer getKryoSerializer() {
    return kryoSerializer;
  }

  private void renderTaskParameters(ExecutionContext context, TaskParameters parameters, int expressionFunctorToken) {
    ExpressionReflectionUtils.applyExpression(parameters,
        (secretMode, value)
            -> context.renderExpression(value,
                StateExecutionContext.builder()
                    .adoptDelegateDecryption(true)
                    .expressionFunctorToken(expressionFunctorToken)
                    .build()));
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }

  private List<String> getRenderedAndTrimmedSelectors(ExecutionContext context) {
    if (isEmpty(delegateSelectors)) {
      return emptyList();
    }
    return trimStrings(delegateSelectors.stream().map(context::renderExpression).collect(toList()));
  }
}
