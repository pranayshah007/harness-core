/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.data.structure.EmptyPredicate;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.ChildrenExecutable;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.StepUtils;
import io.harness.tasks.ResponseData;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ServiceSpecStep
    implements SyncExecutable<ServiceSpecStepParameters>, ChildrenExecutable<ServiceSpecStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.SERVICE_SPEC.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private ServiceStepsHelper serviceStepsHelper;

  @Override
  public Class<ServiceSpecStepParameters> getStepParametersClass() {
    return ServiceSpecStepParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, ServiceSpecStepParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
    saveVariablesSweepingOutput(ambiance, stepParameters, logCallback);
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  @Override
  public ChildrenExecutableResponse obtainChildren(
      Ambiance ambiance, ServiceSpecStepParameters stepParameters, StepInputPackage inputPackage) {
    NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
    saveVariablesSweepingOutput(ambiance, stepParameters, logCallback);
    return ChildrenExecutableResponse.newBuilder()
        .addAllChildren(stepParameters.getChildrenNodeIds()
                            .stream()
                            .map(id -> ChildrenExecutableResponse.Child.newBuilder().setChildNodeId(id).build())
                            .collect(Collectors.toList()))
        .build();
  }

  @Override
  public StepResponse handleChildrenResponse(
      Ambiance ambiance, ServiceSpecStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    StepResponse stepResponse = StepUtils.createStepResponseFromChildResponse(responseDataMap);
    if (StatusUtils.positiveStatuses().contains(stepResponse.getStatus())) {
      NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
      logCallback.saveExecutionLog("Processed artifacts and manifests");
    }
    return stepResponse;
  }

  private void saveVariablesSweepingOutput(
      Ambiance ambiance, ServiceSpecStepParameters stepParameters, NGLogCallback logCallback) {
    saveExecutionLog(logCallback, "Processing service variables...");
    VariablesSweepingOutput variablesSweepingOutput = getVariablesSweepingOutput(ambiance, stepParameters, logCallback);
    executionSweepingOutputResolver.consume(ambiance, YAMLFieldNameConstants.VARIABLES, variablesSweepingOutput, null);

    Object outputObj = variablesSweepingOutput.get("output");
    if (!(outputObj instanceof VariablesSweepingOutput)) {
      outputObj = new VariablesSweepingOutput();
    }
    executionSweepingOutputResolver.consume(ambiance, YAMLFieldNameConstants.SERVICE_VARIABLES,
        (VariablesSweepingOutput) outputObj, StepOutcomeGroup.STAGE.name());

    saveExecutionLog(logCallback, "Processed service variables");
  }

  private VariablesSweepingOutput getVariablesSweepingOutput(
      Ambiance ambiance, ServiceSpecStepParameters stepParameters, NGLogCallback logCallback) {
    Map<String, Object> variables = getFinalVariablesMap(ambiance, stepParameters, logCallback);
    VariablesSweepingOutput variablesOutcome = new VariablesSweepingOutput();
    variablesOutcome.putAll(variables);
    return variablesOutcome;
  }

  @VisibleForTesting
  Map<String, Object> getFinalVariablesMap(
      Ambiance ambiance, ServiceSpecStepParameters stepParameters, NGLogCallback logCallback) {
    ParameterField<List<NGVariable>> variableList = stepParameters.getOriginalVariables();
    Map<String, Object> variables = new HashMap<>();
    Map<String, Object> outputVariables = new VariablesSweepingOutput();
    if (!ParameterField.isNull(variableList) && EmptyPredicate.isNotEmpty(variableList.getValue())) {
      Map<String, Object> originalVariables = NGVariablesUtils.getMapOfVariables(variableList.getValue());
      variables.putAll(originalVariables);
      outputVariables.putAll(originalVariables);
    }
    outputVariables = addStageOverrides(ambiance, outputVariables, stepParameters, logCallback);
    variables.put("output", outputVariables);
    return variables;
  }

  private Map<String, Object> addStageOverrides(Ambiance ambiance, Map<String, Object> variables,
      ServiceSpecStepParameters stepParameters, NGLogCallback logCallback) {
    if (ParameterField.isNull(stepParameters.getStageOverrideVariables())
        || EmptyPredicate.isEmpty(stepParameters.getStageOverrideVariables().getValue())) {
      return variables;
    }

    saveExecutionLog(logCallback, "Applying service variable stage overrides");
    return NGVariablesUtils.applyVariableOverrides(
        variables, stepParameters.getStageOverrideVariables().getValue(), ambiance.getExpressionFunctorToken());
  }

  private void saveExecutionLog(NGLogCallback logCallback, String line) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(line);
    }
  }
}
