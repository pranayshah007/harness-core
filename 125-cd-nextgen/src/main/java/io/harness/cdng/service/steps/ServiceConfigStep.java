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
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.configfile.steps.ConfigFilesOutcome;
import io.harness.cdng.manifest.steps.ManifestsOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.CollectionUtils;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.steps.StepUtils;
import io.harness.tasks.ResponseData;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class ServiceConfigStep implements ChildExecutable<ServiceConfigStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.SERVICE_CONFIG.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private ServiceStepsHelper serviceStepsHelper;
  @Inject private OutcomeService outcomeService;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public Class<ServiceConfigStepParameters> getStepParametersClass() {
    return ServiceConfigStepParameters.class;
  }

  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, ServiceConfigStepParameters stepParameters, StepInputPackage inputPackage) {
    NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance, true);
    logCallback.saveExecutionLog("Starting service step...");
    return ChildExecutableResponse.newBuilder()
        .setChildNodeId(stepParameters.getChildNodeId())
        .addAllLogKeys(CollectionUtils.emptyIfNull(
            StepUtils.generateLogKeys(StepUtils.generateLogAbstractions(ambiance), Collections.emptyList())))
        .build();
  }

  @Override
  public StepResponse handleChildResponse(
      Ambiance ambiance, ServiceConfigStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    StepResponse stepResponse = StepUtils.createStepResponseFromChildResponse(responseDataMap);
    NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
    if (StatusUtils.brokeStatuses().contains(stepResponse.getStatus())) {
      logCallback.saveExecutionLog(LogHelper.color("Failed to complete service step", LogColor.Red), LogLevel.INFO,
          CommandExecutionStatus.FAILURE);
    } else {
      logCallback.saveExecutionLog("Completed service step", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    }
    return stepResponse.withStepOutcomes(Collections.singleton(
        StepResponse.StepOutcome.builder().name("output").outcome(createOutcome(ambiance)).build()));
  }

  private ServiceConfigStepOutcome createOutcome(Ambiance ambiance) {
    return ServiceConfigStepOutcome.builder()
        .serviceResult(getServiceOutcome(ambiance))
        .variablesResult(getVariablesSweepingOutput(ambiance))
        .artifactResults(getArtifactsOutcome(ambiance))
        .manifestResults(getManifestsOutcome(ambiance))
        .configFileResults(getConfigFilesOutcome(ambiance))
        .build();
  }

  private VariablesSweepingOutput getVariablesSweepingOutput(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(YAMLFieldNameConstants.SERVICE_VARIABLES));
    if (!optionalSweepingOutput.isFound()) {
      return null;
    }
    return (VariablesSweepingOutput) optionalSweepingOutput.getOutput();
  }

  private ServiceStepOutcome getServiceOutcome(Ambiance ambiance) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));
    if (!optionalOutcome.isFound()) {
      return null;
    }
    return (ServiceStepOutcome) optionalOutcome.getOutcome();
  }

  private ArtifactsOutcome getArtifactsOutcome(Ambiance ambiance) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    if (!optionalOutcome.isFound()) {
      return null;
    }
    return (ArtifactsOutcome) optionalOutcome.getOutcome();
  }

  private ManifestsOutcome getManifestsOutcome(Ambiance ambiance) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));
    if (!optionalOutcome.isFound()) {
      return null;
    }
    return (ManifestsOutcome) optionalOutcome.getOutcome();
  }

  private ConfigFilesOutcome getConfigFilesOutcome(Ambiance ambiance) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.CONFIG_FILES));
    if (!optionalOutcome.isFound()) {
      return null;
    }
    return (ConfigFilesOutcome) optionalOutcome.getOutcome();
  }
}
